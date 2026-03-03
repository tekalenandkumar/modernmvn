package com.modernmvn.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modernmvn.backend.dto.DependencyNode;
import com.modernmvn.backend.dto.SecurityAdvisory;
import com.modernmvn.backend.entity.*;
import com.modernmvn.backend.repository.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
 * and persists everything to Postgres. This is the ONLY place where
 * Aether and OSV are called (besides the user-triggered /api/maven/resolve).
 */
@Service
public class ArtifactIndexingService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactIndexingService.class);

    private static final long STALENESS_HOURS = 24;
    private static final String SEARCH_API = "https://search.maven.org/solrsearch/select";
    private static final int MAX_VERSIONS_TO_INDEX = 500; // Limit to avoid overwhelming external APIs

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
    private final ArtifactIndexingService self; // Proxy self-reference for @Transactional in @Async

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
            @Lazy ArtifactIndexingService self) {
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

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    // ──────────────────────── Main Entry Point ────────────────────────

    @Transactional
    public ArtifactVersionEntity ensureIndexed(String groupId, String artifactId, String version) {
        // 1. Check if already completely indexed
        Optional<ArtifactVersionEntity> current = versionRepository.findByGav(groupId, artifactId, version);

        if (current.isPresent()) {
            ArtifactVersionEntity av = current.get();
            // Already COMPLETE and not stale → return immediately
            if ("COMPLETE".equals(av.getIndexingStatus()) && !isStale(av)) {
                return av;
            }
        }

        // 2. Not fully indexed. Attempt to acquire Distributed Lock.
        String lockKey = groupId + ":" + artifactId + ":" + version;

        // If we can't get the lock, another node is indexing it.
        // We fallback to enqueueing a job (so we don't block this thread)
        if (!distributedLockRepository.tryLock(lockKey)) {
            log.info("Another instance is currently indexing {}:{}:{}, queuing job instead.", groupId, artifactId,
                    version);
            enqueueJobIfNotExists(groupId, artifactId, version);

            // Return an incomplete entity indicating it's still processing
            ArtifactVersionEntity pendingAv = current.orElseGet(() -> {
                ArtifactEntity artifact = artifactRepository.findByGroupIdAndArtifactId(groupId, artifactId)
                        .orElseGet(() -> artifactRepository.save(new ArtifactEntity(groupId, artifactId)));
                ArtifactVersionEntity newAv = new ArtifactVersionEntity(artifact, version);
                newAv.setIndexingStatus("INDEXING");
                return versionRepository.save(newAv);
            });
            return pendingAv;
        }

        try {
            // Lock acquired. Proceed with indexing.
            ArtifactEntity artifact = artifactRepository.findByGroupIdAndArtifactId(groupId, artifactId)
                    .orElseGet(() -> artifactRepository.save(new ArtifactEntity(groupId, artifactId)));

            ArtifactVersionEntity av = current.orElseGet(() -> new ArtifactVersionEntity(artifact, version));

            // 3. Mark INDEXING
            av.setIndexingStatus("INDEXING");
            av.setErrorMessage(null);
            av = versionRepository.save(av);

            // 4. Resolve dependencies via Aether
            DependencyNode tree = resolutionService.resolveDependency(groupId, artifactId, version);
            int depCount = countDependencies(tree);
            av.setDependencyCount(depCount);

            // 5. Flatten tree into dependency_edges
            edgeRepository.deleteByRootVersionId(av.getId());
            flattenTree(av.getId(), tree, 0, new HashSet<>());

            // 6. Scan vulnerabilities for root artifact
            scanAndPersistVulnerabilities(av.getId(), groupId, artifactId, version);

            // 7. Scan vulnerabilities for each dependency
            List<DependencyEdgeEntity> edges = edgeRepository.findByRootVersionId(av.getId());
            for (DependencyEdgeEntity edge : edges) {
                Optional<ArtifactVersionEntity> depVersion = versionRepository.findById(edge.getDependencyVersionId());
                depVersion.ifPresent(dv -> {
                    try {
                        scanAndPersistVulnerabilities(
                                dv.getId(),
                                dv.getArtifact().getGroupId(),
                                dv.getArtifact().getArtifactId(),
                                dv.getVersion());
                    } catch (Exception e) {
                        log.warn("Failed to scan vulnerabilities for dependency {}:{}:{}: {}",
                                dv.getArtifact().getGroupId(), dv.getArtifact().getArtifactId(),
                                dv.getVersion(), e.getMessage());
                    }
                });
            }

            // 8. Compute and store security summary
            computeAndStoreSummary(av.getId());

            // 9. Mark COMPLETE
            av.setIndexingStatus("COMPLETE");
            av.setLastIndexedAt(Instant.now());
            versionRepository.save(av);

            log.info("Successfully indexed {}:{}:{} — {} dependencies",
                    groupId, artifactId, version, depCount);

            return av;
        } catch (Exception e) {
            log.error("Failed to index {}:{}:{}: {}", groupId, artifactId, version, e.getMessage());

            // Re-fetch av inside catch block if needed, but since av scope changed, we find
            // it again or return current
            ArtifactVersionEntity failedAv = versionRepository.findByGav(groupId, artifactId, version).orElse(null);
            if (failedAv != null) {
                failedAv.setIndexingStatus("FAILED");
                failedAv.setErrorMessage(
                        e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 1000))
                                : "Unknown error");
                versionRepository.save(failedAv);
                return failedAv;
            }
            throw new RuntimeException("Indexing failed", e);
        } finally {
            distributedLockRepository.unlock(lockKey);
        }
    }

    /**
     * Check if a version is safe (no CRITICAL/HIGH vulnerabilities) by reading from
     * DB.
     * This is the DB-only replacement for SecurityService.getVulnerabilities() in
     * the hot path.
     *
     * @return true if safe or if no data exists yet (graceful degradation)
     */
    public boolean isVersionSafeFromDb(String groupId, String artifactId, String version) {
        try {
            Optional<ArtifactVersionEntity> av = versionRepository.findByGav(groupId, artifactId, version);
            if (av.isEmpty()) {
                return true; // No data yet — treat as safe (graceful degradation)
            }
            Optional<SecuritySummaryEntity> summary = summaryRepository.findByArtifactVersionId(av.get().getId());
            if (summary.isEmpty()) {
                return true; // Not indexed yet
            }
            return summary.get().getCriticalCount() == 0 && summary.get().getHighCount() == 0;
        } catch (Exception e) {
            return true; // Never block due to DB errors
        }
    }

    /**
     * Get dependency count from DB. Falls back to 0 if not indexed.
     */
    public int getDependencyCountFromDb(String groupId, String artifactId, String version) {
        try {
            Optional<ArtifactVersionEntity> av = versionRepository.findByGav(groupId, artifactId, version);
            if (av.isPresent() && "COMPLETE".equals(av.get().getIndexingStatus())) {
                return av.get().getDependencyCount();
            }
        } catch (Exception e) {
            log.warn("Failed to get dependency count from DB: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * Get security summary from DB for a specific version.
     */
    public Optional<SecuritySummaryEntity> getSecuritySummary(String groupId, String artifactId, String version) {
        try {
            Optional<ArtifactVersionEntity> av = versionRepository.findByGav(groupId, artifactId, version);
            if (av.isPresent()) {
                return summaryRepository.findByArtifactVersionId(av.get().getId());
            }
        } catch (Exception e) {
            log.warn("Failed to get security summary from DB: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Get all security summaries for an artifact, providing historical trend data.
     */
    public List<SecuritySummaryEntity> getSecurityHistory(String groupId, String artifactId) {
        return summaryRepository.findHistory(groupId, artifactId);
    }

    // ──────────────────────── Stale Refresh ────────────────────────

    /**
     * Re-index stale versions (older than STALENESS_HOURS).
     * Called by IndexingScheduler.
     */
    @Transactional
    public void refreshStaleVersions() {
        Instant staleThreshold = Instant.now().minus(STALENESS_HOURS, ChronoUnit.HOURS);
        List<ArtifactVersionEntity> stale = versionRepository.findStaleVersions(staleThreshold);

        log.info("Found {} stale versions to refresh", stale.size());
        for (ArtifactVersionEntity av : stale) {
            try {
                av.setIndexingStatus("PENDING"); // Reset so ensureIndexed re-processes
                versionRepository.save(av);
                ensureIndexed(
                        av.getArtifact().getGroupId(),
                        av.getArtifact().getArtifactId(),
                        av.getVersion());
            } catch (Exception e) {
                log.error("Failed to refresh stale version {}:{}:{}: {}",
                        av.getArtifact().getGroupId(), av.getArtifact().getArtifactId(),
                        av.getVersion(), e.getMessage());
            }
        }
    }

    // ──────────────────────── Queue Helpers ────────────────────────

    public void enqueueJobIfNotExists(String groupId, String artifactId, String version) {
        if (jobRepository.findByGroupIdAndArtifactIdAndVersion(groupId, artifactId, version).isEmpty()) {
            jobRepository.save(new IndexingJobEntity(groupId, artifactId, version));
            log.info("Queued background indexing job for {}:{}:{}", groupId, artifactId, version);
        }
    }

    /**
     * Called by the background setting worker to actually index.
     * Starts a new transaction to ensure locks work properly.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processJob(IndexingJobEntity job) {
        try {
            ensureIndexed(job.getGroupId(), job.getArtifactId(), job.getVersion());
            job.setStatus("COMPLETE");
        } catch (Exception e) {
            log.error("Job failed for {}:{}:{}: {}", job.getGroupId(), job.getArtifactId(), job.getVersion(),
                    e.getMessage());
            job.setStatus("FAILED");
        }
        jobRepository.save(job);
    }
    // ──────────────────────── Internal Helpers ────────────────────────

    private boolean isStale(ArtifactVersionEntity av) {
        if (av.getLastIndexedAt() == null)
            return true;
        return av.getLastIndexedAt().isBefore(Instant.now().minus(STALENESS_HOURS, ChronoUnit.HOURS));
    }

    /**
     * Flatten a DependencyNode tree into dependency_edge rows.
     * Each node gets its own ArtifactEntity + ArtifactVersionEntity (upserted).
     */
    private void flattenTree(Long rootVersionId, DependencyNode node, int depth, Set<String> visited) {
        if (node == null || node.children() == null)
            return;

        for (DependencyNode child : node.children()) {
            String key = child.groupId() + ":" + child.artifactId() + ":" + child.version();
            if (visited.contains(key))
                continue;
            visited.add(key);

            // Upsert the dependency's artifact + version
            ArtifactEntity depArtifact = artifactRepository.findByGroupIdAndArtifactId(
                    child.groupId(), child.artifactId())
                    .orElseGet(() -> artifactRepository.save(
                            new ArtifactEntity(child.groupId(), child.artifactId())));

            ArtifactVersionEntity depVersion = versionRepository.findByArtifactAndVersion(
                    depArtifact, child.version())
                    .orElseGet(() -> {
                        ArtifactVersionEntity newVer = new ArtifactVersionEntity(depArtifact, child.version());
                        newVer.setIndexingStatus("COMPLETE"); // Dependencies are indexed via parent
                        newVer.setLastIndexedAt(Instant.now());
                        return versionRepository.save(newVer);
                    });

            // Insert edge
            DependencyEdgeEntity edge = new DependencyEdgeEntity(
                    rootVersionId, depVersion.getId(),
                    depth + 1, depth == 0,
                    child.scope());
            try {
                edgeRepository.save(edge);
            } catch (Exception e) {
                // Duplicate edge — skip (idempotent)
            }

            // Recurse into children
            flattenTree(rootVersionId, child, depth + 1, visited);
        }
    }

    /**
     * Query OSV for a specific GAV and persist any found vulnerabilities.
     */
    private void scanAndPersistVulnerabilities(Long artifactVersionId,
            String groupId, String artifactId, String version) {
        try {
            // Use SecurityService's existing OSV query logic
            List<SecurityAdvisory> advisories = securityService.queryOsvPublic(groupId, artifactId, version);

            for (SecurityAdvisory advisory : advisories) {
                // Find the best CVE-like ID
                String cveId = advisory.id();

                // Upsert vulnerability
                VulnerabilityEntity vuln = vulnerabilityRepository.findByCveId(cveId)
                        .orElseGet(() -> vulnerabilityRepository.save(new VulnerabilityEntity(
                                cveId,
                                advisory.severity() != null ? advisory.severity().name() : "UNKNOWN",
                                advisory.cvssScore(),
                                advisory.summary(),
                                null)));

                // Update severity/CVSS if we now have better data
                if (advisory.cvssScore() > vuln.getCvssScore()) {
                    vuln.setCvssScore(advisory.cvssScore());
                    vuln.setSeverity(advisory.severity() != null ? advisory.severity().name() : vuln.getSeverity());
                    vulnerabilityRepository.save(vuln);
                }

                // Link vulnerability to artifact version
                try {
                    artifactVulnRepository.save(
                            new ArtifactVulnerabilityEntity(artifactVersionId, vuln.getId()));
                } catch (Exception e) {
                    // Duplicate — skip (idempotent)
                }
            }
        } catch (Exception e) {
            log.warn("OSV scan failed for {}:{}:{}: {}", groupId, artifactId, version, e.getMessage());
        }
    }

    /**
     * Compute security summary by aggregating vulnerability data from edges +
     * vulnerabilities.
     */
    private void computeAndStoreSummary(Long rootVersionId) {
        // Get all edges for this root
        List<DependencyEdgeEntity> edges = edgeRepository.findByRootVersionId(rootVersionId);

        // Collect all version IDs (root + dependencies)
        Set<Long> allVersionIds = new HashSet<>();
        allVersionIds.add(rootVersionId);
        for (DependencyEdgeEntity edge : edges) {
            allVersionIds.add(edge.getDependencyVersionId());
        }

        // Aggregate vulnerabilities
        int totalVulns = 0, directVulns = 0, transitiveVulns = 0;
        int critical = 0, high = 0, medium = 0, low = 0;
        double maxCvss = -1.0;

        // Root artifact's own vulnerabilities
        List<ArtifactVulnerabilityEntity> rootVulns = artifactVulnRepository.findByArtifactVersionId(rootVersionId);
        for (ArtifactVulnerabilityEntity av : rootVulns) {
            Optional<VulnerabilityEntity> vuln = vulnerabilityRepository.findById(av.getVulnerabilityId());
            if (vuln.isPresent()) {
                totalVulns++;
                directVulns++;
                maxCvss = Math.max(maxCvss, vuln.get().getCvssScore());
                switch (vuln.get().getSeverity() != null ? vuln.get().getSeverity().toUpperCase() : "") {
                    case "CRITICAL" -> critical++;
                    case "HIGH" -> high++;
                    case "MEDIUM" -> medium++;
                    case "LOW" -> low++;
                }
            }
        }

        // Dependency vulnerabilities
        for (DependencyEdgeEntity edge : edges) {
            List<ArtifactVulnerabilityEntity> depVulns = artifactVulnRepository
                    .findByArtifactVersionId(edge.getDependencyVersionId());
            for (ArtifactVulnerabilityEntity av : depVulns) {
                Optional<VulnerabilityEntity> vuln = vulnerabilityRepository.findById(av.getVulnerabilityId());
                if (vuln.isPresent()) {
                    totalVulns++;
                    if (edge.isDirect()) {
                        directVulns++;
                    } else {
                        transitiveVulns++;
                    }
                    maxCvss = Math.max(maxCvss, vuln.get().getCvssScore());
                    switch (vuln.get().getSeverity() != null ? vuln.get().getSeverity().toUpperCase() : "") {
                        case "CRITICAL" -> critical++;
                        case "HIGH" -> high++;
                        case "MEDIUM" -> medium++;
                        case "LOW" -> low++;
                    }
                }
            }
        }

        // Store summary
        SecuritySummaryEntity summary = summaryRepository.findByArtifactVersionId(rootVersionId)
                .orElse(new SecuritySummaryEntity(rootVersionId));
        summary.setTotalVulns(totalVulns);
        summary.setDirectVulns(directVulns);
        summary.setTransitiveVulns(transitiveVulns);
        summary.setCriticalCount(critical);
        summary.setHighCount(high);
        summary.setMediumCount(medium);
        summary.setLowCount(low);
        summary.setMaxCvss(maxCvss);
        summary.computeRiskScore();
        summary.setLastCalculatedAt(Instant.now());
        summaryRepository.save(summary);
    }

    private int countDependencies(DependencyNode node) {
        if (node == null || node.children() == null)
            return 0;
        int count = node.children().size();
        for (DependencyNode child : node.children()) {
            count += countDependencies(child);
        }
        return count;
    }

    // ──────────────────────── Bulk Version Indexing ────────────────────────

    /**
     * Asynchronously index ALL versions of an artifact.
     * Triggered when any single version is visited via the UI.
     * <p>
     * This builds up the full vulnerability history and ensures all versions
     * are ready for instant reads. Only indexes the top N stable releases
     * to avoid overwhelming external APIs.
     */
    @Async("asyncExecutor")
    public void indexAllVersionsAsync(String groupId, String artifactId) {
        log.info("Starting bulk version indexing for {}:{}", groupId, artifactId);
        try {
            List<String> versions = fetchAllVersionStrings(groupId, artifactId);
            int indexed = 0;

            for (String version : versions) {
                if (indexed >= MAX_VERSIONS_TO_INDEX) {
                    log.info("Reached max version limit ({}) for {}:{}",
                            MAX_VERSIONS_TO_INDEX, groupId, artifactId);
                    break;
                }

                // Skip if already indexed
                Optional<ArtifactVersionEntity> existing = versionRepository.findByGav(groupId, artifactId, version);
                if (existing.isPresent() && "COMPLETE".equals(existing.get().getIndexingStatus())
                        && !isStale(existing.get())) {
                    continue; // Already done
                }

                // Ensure it gets queued
                enqueueJobIfNotExists(groupId, artifactId, version);
                indexed++;

            }

            log.info("Bulk indexing complete for {}:{} — {} versions indexed",
                    groupId, artifactId, indexed);
        } catch (Exception e) {
            log.error("Bulk indexing failed for {}:{}: {}", groupId, artifactId, e.getMessage());
        }
    }

    /**
     * Fetch all version strings for a GAV from Maven Central Solr.
     * Returns stable releases first (sorted by timestamp descending).
     */
    private List<String> fetchAllVersionStrings(String groupId, String artifactId) throws Exception {
        String encodedG = URLEncoder.encode(groupId, StandardCharsets.UTF_8);
        String encodedA = URLEncoder.encode(artifactId, StandardCharsets.UTF_8);
        String url = SEARCH_API
                + "?q=g:%22" + encodedG + "%22+AND+a:%22" + encodedA + "%22"
                + "&core=gav"
                + "&rows=200"
                + "&wt=json";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "modernmvn/1.0")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.warn("Solr returned {} when fetching versions for {}:{}",
                    response.statusCode(), groupId, artifactId);
            return List.of();
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode docs = root.path("response").path("docs");

        List<String> versions = new ArrayList<>();
        for (JsonNode doc : docs) {
            String version = doc.path("v").asText("");
            if (!version.isEmpty()) {
                versions.add(version);
            }
        }
        return versions; // Solr returns sorted by timestamp desc already
    }
}
