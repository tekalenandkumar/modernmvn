package com.modernmvn.backend.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modernmvn.backend.entity.CrawlerStateEntity;
import com.modernmvn.backend.entity.IndexingJobEntity;
import com.modernmvn.backend.repository.CrawlerStateRepository;
import com.modernmvn.backend.repository.IndexingJobRepository;
import com.modernmvn.backend.service.ArtifactIndexingService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Proactively crawls Maven Central search API and seeds the indexing_jobs
 * queue so the database is populated independent of user traffic.
 *
 * Pagination strategy: group-prefix sharding.
 *
 * The Maven Central public Solr API (search.maven.org/solrsearch/select)
 * does NOT support cursorMark — it is stripped by the CloudFront/nginx proxy
 * and the sort order is overridden by the server. Deep offset paging
 * (start > 100K) causes HTTP 403 Forbidden responses.
 *
 * Solution: shard by group prefix (com, org, io, net, de, …).
 * Each prefix has a small total result count (< 100K) so we can safely
 * use offset-based pagination within each shard without triggering 403s.
 *
 * The cursorMark column is repurposed to persist
 * "prefixIndex:offsetWithinPrefix"
 * so the crawler can resume exactly across restarts.
 */
@Component
public class MavenCrawlerWorker {

    private static final Logger log = LoggerFactory.getLogger(MavenCrawlerWorker.class);

    private static final String SOLR_API = "https://search.maven.org/solrsearch/select";
    private static final int PAGE_SIZE = 200;
    private static final String USER_AGENT = "ModernMvnCrawler/1.0 (https://modernmvn.com; contact@modernmvn.com)";

    /**
     * Alphabetical group-ID prefixes to shard the crawl over.
     * These cover > 99% of Maven Central artifacts without large offsets.
     */
    private static final List<String> GROUP_PREFIXES = List.of(
            "com", "org", "io", "net", "de", "uk", "fr", "ru",
            "ch", "be", "pl", "cz", "nl", "dk", "se", "fi",
            "au", "nz", "br", "ar", "mx", "co", "in", "cn",
            "jp", "kr", "tw", "sg", "za", "hk", "il",
            "app", "dev", "tech", "cloud", "ai", "ml",
            "jakarta", "javax", "java", "android", "kotlin");

    private final CrawlerStateRepository stateRepository;
    private final IndexingJobRepository jobRepository;
    private final ArtifactIndexingService indexingService;
    private final MeterRegistry meterRegistry;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Set to true to re-enable the crawler. Defaults to false (disabled) to avoid
     * 403s.
     */
    @Value("${modernmvn.crawler.enabled:false}")
    private boolean crawlerEnabled;

    public MavenCrawlerWorker(
            CrawlerStateRepository stateRepository,
            IndexingJobRepository jobRepository,
            ArtifactIndexingService indexingService,
            MeterRegistry meterRegistry) {
        this.stateRepository = stateRepository;
        this.jobRepository = jobRepository;
        this.indexingService = indexingService;
        this.meterRegistry = meterRegistry;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    // ──────────────────────── Scheduled Tick ─────────────────────────

    /**
     * Main crawl tick. Runs every 30 seconds by default (configurable).
     * Continuously crawls Maven Central by rotating through group prefixes.
     * <p>
     * Disabled by default. Set modernmvn.crawler.enabled=true to re-enable.
     */
    @Scheduled(fixedDelayString = "${modernmvn.crawler.delay-ms:30000}")
    public void crawlNextBatch() {
        if (!crawlerEnabled) {
            log.debug("[Crawler] Crawler is disabled (modernmvn.crawler.enabled=false). Skipping tick.");
            return;
        }
        if (!tryClaimTick()) {
            log.debug("[Crawler] Another instance is running the crawler — skipping this tick.");
            return;
        }

        CrawlerStateEntity state = stateRepository.findById(1L)
                .orElseGet(() -> stateRepository.save(new CrawlerStateEntity()));

        // Parse current shard position from cursorMark field (repurposed as
        // "prefixIdx:offset")
        ShardPosition pos = parsePosition(state.getCursorMark());

        if (pos.prefixIdx >= GROUP_PREFIXES.size()) {
            // Completed all prefixes — start a fresh sweep
            log.info("[Crawler] Full sweep complete! Total discovered: {}. Restarting from first prefix.",
                    state.getTotalDiscovered());
            pos = new ShardPosition(0, 0);
            state.setCursorMark(encodePosition(pos));
            state.setCursorOffset(0L);
            stateRepository.save(state);
            meterRegistry.counter("crawler.sweep.complete").increment();
        }

        String prefix = GROUP_PREFIXES.get(pos.prefixIdx);
        log.info("[Crawler] Fetching shard {}/{} prefix='{}' offset={}",
                pos.prefixIdx + 1, GROUP_PREFIXES.size(), prefix, pos.offset);

        try {
            SolrResult solrResult = fetchArtifactBatch(prefix, pos.offset);

            // Update live total from Solr on every batch
            if (solrResult.numFound() > 0) {
                state.setTotalArtifacts(solrResult.numFound());
            }

            if (solrResult.artifacts().isEmpty() || pos.offset + PAGE_SIZE >= solrResult.numFound()) {
                // This prefix is fully crawled — advance to next
                log.info("[Crawler] Prefix '{}' done (numFound={}). Moving to next prefix.",
                        prefix, solrResult.numFound());
                pos = new ShardPosition(pos.prefixIdx + 1, 0);
            } else {
                // Advance offset within this prefix
                pos = new ShardPosition(pos.prefixIdx, pos.offset + PAGE_SIZE);
            }

            // Enqueue jobs for this batch
            int jobsQueued = 0;
            for (String[] gav : solrResult.artifacts()) {
                String groupId = gav[0];
                String artifactId = gav[1];
                String latestVersion = gav[2];
                jobsQueued += enqueueIfAbsent(groupId, artifactId, latestVersion);
                indexingService.indexAllVersionsAsync(groupId, artifactId);
            }

            long discovered = (state.getTotalDiscovered() != null) ? state.getTotalDiscovered() : 0L;
            state.setTotalDiscovered(discovered + solrResult.artifacts().size());
            state.setCursorMark(encodePosition(pos));
            state.setCursorOffset(state.getTotalDiscovered());
            state.setLastRun(Instant.now());
            stateRepository.save(state);

            meterRegistry.counter("crawler.artifacts.discovered").increment(solrResult.artifacts().size());
            meterRegistry.counter("crawler.jobs.queued").increment(jobsQueued);

            log.info("[Crawler] Batch done: {} artifacts, {} new jobs queued. Total discovered: {}",
                    solrResult.artifacts().size(), jobsQueued, state.getTotalDiscovered());

        } catch (Exception e) {
            log.error("[Crawler] Batch failed: {}", e.getMessage(), e);
            meterRegistry.counter("crawler.batch.failure").increment();
        } finally {
            releaseTickLock();
            applyCrawlDelay();
        }
    }

    private void releaseTickLock() {
        try {
            stateRepository.findById(1L).ifPresent(state -> {
                state.setRunning(false);
                stateRepository.save(state);
            });
        } catch (Exception e) {
            log.error("[Crawler] Failed to release tick lock: {}", e.getMessage());
        }
    }

    // ──────────────────────── Distributed Lock ───────────────────────

    @Transactional
    public boolean tryClaimTick() {
        try {
            CrawlerStateEntity state = stateRepository.findById(1L).orElse(null);
            if (state == null)
                return true;

            if (state.isRunning() && state.getLastRun() != null
                    && state.getLastRun().isAfter(Instant.now().minusSeconds(120))) {
                return false;
            }

            state.setRunning(true);
            stateRepository.save(state);
            return true;
        } catch (OptimisticLockingFailureException e) {
            return false;
        }
    }

    // ──────────────────────── Solr API ───────────────────────────────

    /**
     * Fetches one page from Maven Central Solr, scoped to a group prefix.
     * Keeping each prefix's offset small (< 100K) avoids the 403 Forbidden
     * errors that occur with deep pagination (start > 500K on q=*:*).
     */
    private SolrResult fetchArtifactBatch(String groupPrefix, long offset) throws Exception {
        String query = "g:" + groupPrefix + ".*";
        String url = SOLR_API
                + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&rows=" + PAGE_SIZE
                + "&start=" + offset
                + "&wt=json";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Solr returned HTTP " + response.statusCode()
                    + ": " + response.body().substring(0, Math.min(200, response.body().length())));
        }

        JsonNode root = objectMapper.readTree(response.body());
        long numFound = root.path("response").path("numFound").asLong(0);
        JsonNode docs = root.path("response").path("docs");

        List<String[]> result = new ArrayList<>();
        for (JsonNode doc : docs) {
            String id = doc.path("id").asText("");
            String latestVersion = doc.path("latestVersion").asText("");

            if (id.isBlank() || latestVersion.isBlank())
                continue;

            int colon = id.indexOf(':');
            if (colon < 0)
                continue;

            String groupId = sanitize(id.substring(0, colon));
            String artifactId = sanitize(id.substring(colon + 1));

            if (!groupId.isBlank() && !artifactId.isBlank()) {
                result.add(new String[] { groupId, artifactId, latestVersion });
            }
        }
        return new SolrResult(numFound, result);
    }

    /** Holds Solr response: live total and this page's artifacts. */
    private record SolrResult(long numFound, List<String[]> artifacts) {
    }

    // ──────────────────────── Shard Position ─────────────────────────

    /**
     * Persistent shard position stored in the cursorMark column. Format:
     * "prefixIdx:offset"
     */
    private record ShardPosition(int prefixIdx, long offset) {
    }

    private static ShardPosition parsePosition(String encoded) {
        if (encoded == null || encoded.isBlank() || "*".equals(encoded) || "DONE".equals(encoded)) {
            return new ShardPosition(0, 0);
        }
        try {
            String[] parts = encoded.split(":");
            return new ShardPosition(Integer.parseInt(parts[0]), Long.parseLong(parts[1]));
        } catch (Exception e) {
            return new ShardPosition(0, 0);
        }
    }

    private static String encodePosition(ShardPosition pos) {
        return pos.prefixIdx + ":" + pos.offset;
    }

    // ──────────────────────── Helpers ────────────────────────────────

    private void applyCrawlDelay() {
        try {
            long delayMs = Long.parseLong(
                    System.getProperty("modernmvn.crawler.rate-limit-ms", "1000"));
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9.\\-_]", "").trim();
    }

    private int enqueueIfAbsent(String groupId, String artifactId, String version) {
        try {
            if (jobRepository.findByGroupIdAndArtifactIdAndVersion(groupId, artifactId, version).isEmpty()) {
                jobRepository.save(new IndexingJobEntity(groupId, artifactId, version));
                return 1;
            }
        } catch (Exception e) {
            log.debug("[Crawler] Job already exists: {}:{}:{}", groupId, artifactId, version);
        }
        return 0;
    }

    /**
     * Returns current crawler status.
     */
    public CrawlerStateEntity getStatus() {
        return stateRepository.findById(1L).orElseGet(CrawlerStateEntity::new);
    }
}
