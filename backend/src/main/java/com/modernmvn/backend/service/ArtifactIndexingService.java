package com.modernmvn.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modernmvn.backend.dto.DependencyNode;
import com.modernmvn.backend.dto.SecurityAdvisory;
import com.modernmvn.backend.entity.*;
import com.modernmvn.backend.repository.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Central orchestrator for artifact indexing.
 * Resolves dependencies via Aether, queries OSV for vulnerabilities,
 * and persists everything to Postgres.
 */
@Service
public class ArtifactIndexingService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactIndexingService.class);

    private static final long STALENESS_HOURS = 24;
    private static final String SEARCH_API = "https://search.maven.org/solrsearch/select";
    private static final int MAX_VERSIONS_TO_INDEX = 500;

    private final MavenResolutionService resolutionService;
    private final SecurityService securityService;
    private final ArtifactRepository artifactRepository;
    private final ArtifactVersionRepository versionRepository;
    private final DependencyEdgeRepository edgeRepository;
    private final VulnerabilityRepository vulnerabilityRepository;
    private final ArtifactVulnerabilityRepository artifactVulnRepository;
    private final SecuritySummaryRepository summaryRepository;
    private final IndexingJobRepository jobRepository;
    private final DistributedLockRepository distributedLockRepository;
    private final ArtifactIndexingService self;
    private final MeterRegistry meterRegistry;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ArtifactIndexingService(
            MavenResolutionService resolutionService,
            SecurityService securityService,
            ArtifactRepository artifactRepository,
            ArtifactVersionRepository versionRepository,
            DependencyEdgeRepository edgeRepository,
            VulnerabilityRepository vulnerabilityRepository,
            ArtifactVulnerabilityRepository artifactVulnRepository,
            SecuritySummaryRepository summaryRepository,
            IndexingJobRepository jobRepository,
            DistributedLockRepository distributedLockRepository,
            @Lazy ArtifactIndexingService self,
            MeterRegistry meterRegistry) {
        this.resolutionService = resolutionService;
        this.securityService = securityService;
        this.artifactRepository = artifactRepository;
        this.versionRepository = versionRepository;
        this.edgeRepository = edgeRepository;
        this.vulnerabilityRepository = vulnerabilityRepository;
        this.artifactVulnRepository = artifactVulnRepository;
        this.summaryRepository = summaryRepository;
        this.jobRepository = jobRepository;
        this.distributedLockRepository = distributedLockRepository;
        this.self = self;
        this.meterRegistry = meterRegistry;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    // ──────────────────────── Main Entry Point ────────────────────────

    @Transactional
    public ArtifactVersionEntity ensureIndexed(String groupId, String artifactId, String version) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            // 1. Check if already completely indexed
            Optional<ArtifactVersionEntity> current = versionRepository.findByGav(groupId, artifactId, version);

            if (current.isPresent()) {
                ArtifactVersionEntity av = current.get();
                if ("COMPLETE".equals(av.getIndexingStatus())) {
                    Instant staleLimit = Instant.now().minus(STALENESS_HOURS, ChronoUnit.HOURS);
                    if (av.getLastIndexedAt() != null && av.getLastIndexedAt().isAfter(staleLimit)) {
                        return av;
                    }
                }
            }

            // 2. Resolve/Insert root version shell
            ArtifactVersionEntity av = getOrCreateVersionShell(groupId, artifactId, version);

            // 3. Mark as INDEXING
            av.setIndexingStatus("INDEXING");
            versionRepository.saveAndFlush(av);

            log.info("Starting indexing for {}:{}:{}", groupId, artifactId, version);

            // 4. Build Dependency Graph (Aether)
            DependencyNode root = resolutionService.resolveDependency(groupId, artifactId, version);
            av.setDependencyCount(countTotalDependencies(root));

            // 5. Clear old edges and re-populate
            edgeRepository.deleteByRootVersionId(av.getId());
            saveDependencyEdges(av, root);

            // 6. Extract flat list of GAVs for security scanning
            Set<GAV> uniqueGavs = new HashSet<>();
            extractGavs(root, uniqueGavs);

            // 7. OSV Lookup and Vulnerability persistence
            for (GAV gav : uniqueGavs) {
                try {
                    List<SecurityAdvisory> advisories = securityService.queryOsvPublic(gav.groupId(), gav.artifactId(),
                            gav.version());
                    // Find or create version for the dependency so we can link it
                    ArtifactVersionEntity depAv = getOrCreateVersionShell(gav.groupId(), gav.artifactId(),
                            gav.version());
                    saveAdvisories(depAv, advisories);
                } catch (Exception e) {
                    meterRegistry.counter("osv_call.failure").increment();
                    log.warn("Failed to fetch vulnerabilities for {}: {}", gav, e.getMessage());
                }
            }

            // 8. Finalize
            av.setIndexingStatus("COMPLETE");
            av.setLastIndexedAt(Instant.now());
            av.setErrorMessage(null);

            // 9. Update precomputed Security Summary
            Timer.Sample summarySample = Timer.start(meterRegistry);
            buildAndSaveSecuritySummary(av);
            summarySample.stop(Timer.builder("security_summary_build_time")
                    .description("Time taken to build security summary")
                    .register(meterRegistry));

            return versionRepository.save(av);
        } catch (Exception e) {
            log.error("Indexing failed for {}:{}:{}: {}", groupId, artifactId, version, e.getMessage());
            meterRegistry.counter("indexing.failure").increment();
            throw new RuntimeException("Indexing failed", e);
        } finally {
            sample.stop(Timer.builder("indexing_duration")
                    .description("Time taken to index an artifact version")
                    .register(meterRegistry));
        }
    }

    private ArtifactVersionEntity getOrCreateVersionShell(String groupId, String artifactId, String version) {
        ArtifactEntity artifact = artifactRepository.findByGroupIdAndArtifactId(groupId, artifactId)
                .orElseGet(() -> artifactRepository.save(new ArtifactEntity(groupId, artifactId)));

        return versionRepository.findByArtifactAndVersion(artifact, version)
                .orElseGet(() -> versionRepository.save(new ArtifactVersionEntity(artifact, version)));
    }

    // ──────────────────────── Async Scaling ───────────────────────────

    @Async
    public void indexAllVersionsAsync(String groupId, String artifactId) {
        try {
            List<String> versions = fetchVersionsFromCentral(groupId, artifactId);
            int count = 0;
            for (String v : versions) {
                if (++count > MAX_VERSIONS_TO_INDEX)
                    break;

                // Use a background job row to coordinate across cluster
                if (jobRepository.findByGroupIdAndArtifactIdAndVersion(groupId, artifactId, v).isEmpty()) {
                    jobRepository.save(new IndexingJobEntity(groupId, artifactId, v));
                }
            }
        } catch (Exception e) {
            log.error("Failed to trigger background indexing for {}:{}: {}", groupId, artifactId, e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processJob(IndexingJobEntity job) {
        try {
            ensureIndexed(job.getGroupId(), job.getArtifactId(), job.getVersion());
            job.setStatus(IndexingJobStatus.COMPLETE);
        } catch (Exception e) {
            log.error("Job failed for {}:{}:{}: {}", job.getGroupId(), job.getArtifactId(), job.getVersion(),
                    e.getMessage());
            job.setStatus(IndexingJobStatus.FAILED);
        }
        jobRepository.save(job);
    }

    @Transactional
    public void refreshStaleVersions() {
        Instant staleLimit = Instant.now().minus(STALENESS_HOURS, ChronoUnit.HOURS);
        List<ArtifactVersionEntity> stale = versionRepository.findStaleVersions(staleLimit);

        log.info("Found {} stale versions to re-index", stale.size());
        for (ArtifactVersionEntity av : stale) {
            try {
                self.ensureIndexed(av.getArtifact().getGroupId(), av.getArtifact().getArtifactId(), av.getVersion());
            } catch (Exception e) {
                log.error("Failed to refresh stale version {}: {}", av.getId(), e.getMessage());
            }
        }
    }

    // ──────────────────────── Precomputation ──────────────────────────

    public void buildAndSaveSecuritySummary(ArtifactVersionEntity av) {
        long directCount = artifactVulnRepository.countDirectVulnerabilities(av.getId());
        long transitiveCount = artifactVulnRepository.countTransitiveVulnerabilities(av.getId());

        SecuritySummaryRepository.SeverityCounts counts = summaryRepository.getSeverityCounts(av.getId());

        SecuritySummaryEntity summary = summaryRepository.findById(av.getId())
                .orElse(new SecuritySummaryEntity(av.getId()));

        summary.setDirectVulns((int) directCount);
        summary.setTransitiveVulns((int) transitiveCount);
        summary.setTotalVulns((int) (directCount + transitiveCount));

        summary.setCriticalCount(counts.getCritical());
        summary.setHighCount(counts.getHigh());
        summary.setMediumCount(counts.getMedium());
        summary.setLowCount(counts.getLow());

        Double maxCvss = summaryRepository.getMaxCvss(av.getId());
        summary.setMaxCvss(maxCvss != null ? maxCvss : -1.0);

        summary.computeRiskScore();
        summary.setLastCalculatedAt(Instant.now());

        summaryRepository.save(summary);
    }

    public Optional<SecuritySummaryEntity> getSecuritySummary(String groupId, String artifactId, String version) {
        return summaryRepository.findByGav(groupId, artifactId, version);
    }

    public List<SecuritySummaryEntity> getSecurityHistory(String groupId, String artifactId) {
        return summaryRepository.findHistory(groupId, artifactId);
    }

    @Transactional(readOnly = true)
    public int getDependencyCountFromDb(String groupId, String artifactId, String version) {
        return versionRepository.findByGav(groupId, artifactId, version)
                .map(ArtifactVersionEntity::getDependencyCount)
                .orElse(0);
    }

    @Transactional(readOnly = true)
    public boolean isVersionSafeFromDb(String groupId, String artifactId, String version) {
        return summaryRepository.findByGav(groupId, artifactId, version)
                .map(s -> s.getCriticalCount() == 0 && s.getHighCount() == 0)
                .orElse(true); // default to safe if unknown
    }

    // ──────────────────────── Helpers ───────────────────────────────

    private void saveDependencyEdges(ArtifactVersionEntity av, DependencyNode root) {
        List<DependencyEdgeEntity> edges = new ArrayList<>();
        traverseAndCollectEdges(av, root, 0, edges, new HashSet<>());
        edgeRepository.saveAll(edges);
    }

    private void traverseAndCollectEdges(ArtifactVersionEntity av, DependencyNode node, int depth,
            List<DependencyEdgeEntity> edges, Set<String> seen) {
        String key = node.groupId() + ":" + node.artifactId() + ":" + node.version();
        if (seen.contains(key))
            return;
        seen.add(key);

        for (DependencyNode child : node.children()) {
            ArtifactVersionEntity depAv = getOrCreateVersionShell(child.groupId(), child.artifactId(), child.version());

            edges.add(new DependencyEdgeEntity(av.getId(), depAv.getId(),
                    depth + 1, depth == 0, child.scope()));

            traverseAndCollectEdges(av, child, depth + 1, edges, seen);
        }
    }

    private void saveAdvisories(ArtifactVersionEntity av, List<SecurityAdvisory> advisories) {
        for (SecurityAdvisory adv : advisories) {
            VulnerabilityEntity v = vulnerabilityRepository.findByCveId(adv.id())
                    .orElseGet(() -> vulnerabilityRepository.save(VulnerabilityEntity.fromDto(adv)));

            if (artifactVulnRepository.findByArtifactVersionId(av.getId()).stream()
                    .noneMatch(existing -> existing.getVulnerabilityId().equals(v.getId()))) {
                artifactVulnRepository.save(new ArtifactVulnerabilityEntity(av.getId(), v.getId()));
            }
        }
    }

    private List<String> fetchVersionsFromCentral(String groupId, String artifactId) throws Exception {
        String query = String.format("g:\"%s\" AND a:\"%s\"", groupId, artifactId);
        String url = SEARCH_API + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&core=gav&rows=100&wt=json";

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200)
            return List.of();

        JsonNode root = objectMapper.readTree(response.body());
        List<String> versions = new ArrayList<>();
        for (JsonNode doc : root.path("response").path("docs")) {
            versions.add(doc.path("v").asText());
        }
        return versions;
    }

    private void extractGavs(DependencyNode node, Set<GAV> unique) {
        unique.add(new GAV(node.groupId(), node.artifactId(), node.version()));
        for (DependencyNode child : node.children()) {
            extractGavs(child, unique);
        }
    }

    private int countTotalDependencies(DependencyNode root) {
        Set<String> seen = new HashSet<>();
        collectUniqueDeps(root, seen);
        return Math.max(0, seen.size() - 1); // exclude root
    }

    private void collectUniqueDeps(DependencyNode node, Set<String> seen) {
        seen.add(node.groupId() + ":" + node.artifactId() + ":" + node.version());
        for (DependencyNode child : node.children()) {
            collectUniqueDeps(child, seen);
        }
    }

    private record GAV(String groupId, String artifactId, String version) {
    }
}
