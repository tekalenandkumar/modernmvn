package com.modernmvn.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.modernmvn.backend.dto.SecurityAdvisory;
import com.modernmvn.backend.dto.SecurityAdvisory.Severity;
import com.modernmvn.backend.dto.VersionIntelligence.*;
import com.modernmvn.backend.dto.VulnerabilityReport;
import com.modernmvn.backend.entity.SecuritySummaryEntity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import java.util.concurrent.CompletableFuture;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Provides security vulnerability lookups via the OSV.dev API
 * and version stability scoring.
 *
 * OSV API docs: https://google.github.io/osv.dev/api/
 *
 * NOTE: getVulnerabilities() still calls OSV live (for backward compatibility
 * with SecurityController). The ArtifactIndexingService uses queryOsvPublic()
 * directly and persists results to the new schema.
 */
@Service
public class SecurityService {

    private static final Logger log = LoggerFactory.getLogger(SecurityService.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // OSV.dev is free, no API key required
    private static final String OSV_QUERY_URL = "https://api.osv.dev/v1/query";
    // NVD API — free, no key needed for basic use (rate-limited)
    private static final String NVD_CVE_URL = "https://services.nvd.nist.gov/rest/json/cves/2.0?cveId=";

    // Pre-release pattern (matches alpha, beta, RC, SNAPSHOT, etc.)
    private static final Pattern PRE_RELEASE_PATTERN = Pattern.compile(
            "(?i).*(alpha|beta|rc|cr|m\\d|snapshot|preview|dev|incubating|ea).*");

    // Age thresholds for stability scoring
    private static final long DAYS_RECENT = 90; // < 90 days = "recent"
    private static final long DAYS_OUTDATED = 1825; // > 5 years = "outdated"

    public SecurityService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    // Break circular dependency: ArtifactIndexingService → SecurityService →
    // ArtifactIndexingService
    private ArtifactIndexingService indexingService;

    @Autowired
    public void setIndexingService(@Lazy ArtifactIndexingService indexingService) {
        this.indexingService = indexingService;
    }

    // ──────────────────────── Vulnerability Report ────────────────────────

    /**
     * Query OSV.dev for known vulnerabilities of a specific GAV.
     */
    @Cacheable(value = "vulnerabilities", key = "#groupId + ':' + #artifactId + ':' + #version")
    public VulnerabilityReport getVulnerabilities(String groupId, String artifactId, String version) {
        try {
            List<SecurityAdvisory> advisories = queryOsvPublic(groupId, artifactId, version);

            // Enrich with NVD for real CVSS scores on CVE IDs
            advisories = enrichWithNvdCvss(advisories);

            int critical = 0, high = 0, medium = 0, low = 0;
            Severity highest = null;
            double maxCvss = -1;
            for (SecurityAdvisory adv : advisories) {
                if (adv.cvssScore() > maxCvss)
                    maxCvss = adv.cvssScore();
                switch (adv.severity()) {
                    case CRITICAL -> {
                        critical++;
                        highest = maxSeverity(highest, Severity.CRITICAL);
                    }
                    case HIGH -> {
                        high++;
                        highest = maxSeverity(highest, Severity.HIGH);
                    }
                    case MEDIUM -> {
                        medium++;
                        highest = maxSeverity(highest, Severity.MEDIUM);
                    }
                    case LOW -> {
                        low++;
                        highest = maxSeverity(highest, Severity.LOW);
                    }
                    default -> {
                        highest = maxSeverity(highest, Severity.UNKNOWN);
                    }
                }
            }

            VulnerabilityReport report = new VulnerabilityReport(
                    groupId, artifactId, version,
                    advisories.size(), critical, high, medium, low,
                    highest, advisories, VulnerabilityReport.DISCLAIMER);

            return report;

        } catch (Exception e) {
            return VulnerabilityReport.clean(groupId, artifactId, version);
        }
    }

    // ──────────────────────── Historical Trend ────────────────────────

    /** A single trend data point (serialised to JSON). */
    public record TrendPoint(
            String date,
            String version,
            int totalVulnerabilities,
            int criticalCount,
            int highCount,
            double maxCvssScore) {
    }

    // ──────────────────────── Version Intelligence ────────────────────────

    /**
     * Compute stability and safety assessment for specific versions.
     * Takes pre-fetched version data to avoid redundant Maven Central calls.
     */
    public VersionAssessment assessVersion(
            String groupId, String artifactId,
            String version, boolean isRelease, long timestamp) {
        // Try DB-backed data first (instant, no external calls)
        VulnerabilityReport report = getVulnerabilityReportFromDb(groupId, artifactId, version);

        // DB miss — trigger indexing, then re-read from DB
        if (report == null && indexingService != null) {
            try {
                indexingService.ensureIndexed(groupId, artifactId, version);
                report = getVulnerabilityReportFromDb(groupId, artifactId, version);
            } catch (Exception e) {
                // Indexing failed — fall through
            }
        }

        // No DB data available — return clean report (no live OSV calls in hot path)
        if (report == null) {
            report = VulnerabilityReport.clean(groupId, artifactId, version);
        }

        // Compute stability
        StabilityGrade stability = computeStabilityGrade(version, isRelease, timestamp);
        double stabilityScore = computeStabilityScore(version, isRelease, timestamp);

        // Compute combined safety indicator
        SafetyIndicator safety = computeSafetyIndicator(report, stability);
        String safetyLabel = labelForIndicator(safety, report.totalVulnerabilities());

        return new VersionAssessment(
                version, isRelease, timestamp,
                report.totalVulnerabilities(), report.highestSeverity(),
                stability, stabilityScore,
                safety, safetyLabel);
    }

    /**
     * Build a VulnerabilityReport from DB-precomputed SecuritySummaryEntity.
     * Returns null if not indexed yet.
     */
    private VulnerabilityReport getVulnerabilityReportFromDb(String groupId, String artifactId, String version) {
        if (indexingService == null)
            return null;
        try {
            Optional<SecuritySummaryEntity> opt = indexingService.getSecuritySummary(groupId, artifactId, version);
            if (opt.isEmpty())
                return null;

            SecuritySummaryEntity s = opt.get();
            Severity highest = null;
            if (s.getCriticalCount() > 0)
                highest = Severity.CRITICAL;
            else if (s.getHighCount() > 0)
                highest = Severity.HIGH;
            else if (s.getMediumCount() > 0)
                highest = Severity.MEDIUM;
            else if (s.getLowCount() > 0)
                highest = Severity.LOW;

            return new VulnerabilityReport(
                    groupId, artifactId, version,
                    s.getTotalVulns(), s.getCriticalCount(), s.getHighCount(),
                    s.getMediumCount(), s.getLowCount(),
                    highest, List.of(), // No individual advisories needed for assessment
                    VulnerabilityReport.DISCLAIMER);
        } catch (Exception e) {
            return null; // Fallback to live
        }
    }

    // ──────────────────────── Stability Scoring ────────────────────────

    /**
     * Compute a stability grade for a version.
     */
    public StabilityGrade computeStabilityGrade(String version, boolean isRelease, long timestamp) {
        if (PRE_RELEASE_PATTERN.matcher(version).matches()) {
            return StabilityGrade.PRE_RELEASE;
        }
        if (timestamp <= 0)
            return StabilityGrade.UNKNOWN;

        long daysSinceRelease = ChronoUnit.DAYS.between(
                Instant.ofEpochMilli(timestamp), Instant.now());

        if (daysSinceRelease < DAYS_RECENT)
            return StabilityGrade.RECENT;
        if (daysSinceRelease > DAYS_OUTDATED)
            return StabilityGrade.OUTDATED;
        return StabilityGrade.STABLE;
    }

    /**
     * Compute a 0–100 stability score.
     * Factors: age (maturity vs. staleness), release status, version naming.
     */
    public double computeStabilityScore(String version, boolean isRelease, long timestamp) {
        double score = 50.0; // baseline

        // Pre-release penalty
        if (PRE_RELEASE_PATTERN.matcher(version).matches()) {
            score -= 25.0;
        } else if (isRelease) {
            score += 10.0; // clean release bonus
        }

        // Age-based scoring
        if (timestamp > 0) {
            long days = ChronoUnit.DAYS.between(
                    Instant.ofEpochMilli(timestamp), Instant.now());

            if (days < 7) {
                score += 0; // brand new — neutral
            } else if (days < DAYS_RECENT) {
                score += 10; // recent: likely OK
            } else if (days < 365) {
                score += 20; // 3–12 months: sweet spot
            } else if (days < DAYS_OUTDATED) {
                score += 15; // 1–5 years: mature
            } else {
                score -= 15; // > 5 years: outdated
            }
        }

        // Clamp to [0, 100]
        return Math.max(0, Math.min(100, score));
    }

    // ──────────────────────── Combined Safety ────────────────────────

    private SafetyIndicator computeSafetyIndicator(VulnerabilityReport report, StabilityGrade stability) {
        // Security takes priority
        if (report.criticalCount() > 0 || report.highCount() > 0) {
            return SafetyIndicator.DANGER;
        }
        if (report.mediumCount() > 0) {
            return SafetyIndicator.WARNING;
        }
        if (report.lowCount() > 0) {
            return SafetyIndicator.CAUTION;
        }

        // Then stability
        if (stability == StabilityGrade.PRE_RELEASE) {
            return SafetyIndicator.CAUTION;
        }
        if (stability == StabilityGrade.OUTDATED) {
            return SafetyIndicator.WARNING;
        }

        return SafetyIndicator.SAFE;
    }

    private String labelForIndicator(SafetyIndicator indicator, int vulnCount) {
        return switch (indicator) {
            case SAFE -> "Safe to use";
            case CAUTION -> "Use with caution";
            case WARNING -> vulnCount > 0
                    ? vulnCount + " known " + (vulnCount == 1 ? "vulnerability" : "vulnerabilities")
                    : "Outdated — consider upgrading";
            case DANGER -> vulnCount + " security " + (vulnCount == 1 ? "issue" : "issues") + " found";
            case UNKNOWN -> "Analysis in progress";
        };
    }

    // ──────────────────────── OSV.dev API ────────────────────────

    /**
     * Query the OSV.dev API for vulnerabilities affecting a Maven package version.
     * Protected by Resilience4j. If it fails or times out, returns empty list.
     */
    @TimeLimiter(name = "osv", fallbackMethod = "osvFallbackFuture")
    public CompletableFuture<List<SecurityAdvisory>> queryOsvPublicFuture(String groupId, String artifactId,
            String version) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return queryOsvPublicInternal(groupId, artifactId, version);
            } catch (Exception e) {
                throw new RuntimeException("OSV API call failed", e);
            }
        });
    }

    /**
     * Internal synchronous call wrapped by the Future for TimeLimiter access.
     */
    @CircuitBreaker(name = "osv", fallbackMethod = "osvFallback")
    public List<SecurityAdvisory> queryOsvPublic(String groupId, String artifactId, String version) throws Exception {
        return queryOsvPublicFuture(groupId, artifactId, version).join();
    }

    private List<SecurityAdvisory> queryOsvPublicInternal(String groupId, String artifactId, String version)
            throws Exception {
        // Build request payload
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode pkg = payload.putObject("package");
        pkg.put("name", groupId + ":" + artifactId);
        pkg.put("ecosystem", "Maven");
        payload.put("version", version);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OSV_QUERY_URL))
                .header("Content-Type", "application/json")
                .header("User-Agent", "modernmvn/1.0")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return List.of(); // graceful degradation
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode vulns = root.path("vulns");
        if (vulns.isMissingNode() || !vulns.isArray()) {
            return List.of(); // no vulnerabilities found — clean
        }

        List<SecurityAdvisory> advisories = new ArrayList<>();
        for (JsonNode vuln : vulns) {
            advisories.add(parseOsvVulnerability(vuln, version));
        }

        advisories.sort(Comparator.comparingInt(a -> severityOrdinal(a.severity())));
        return advisories;
    }

    /**
     * Fallback method when OSV CircuitBreaker is open or TimeLimiter is exceeded.
     */
    public CompletableFuture<List<SecurityAdvisory>> osvFallbackFuture(String groupId, String artifactId,
            String version, Throwable t) {
        log.warn("OSV API Circuit Breaker / Timeout fallback engaged for {}:{}:{}. Reason: {}", groupId, artifactId,
                version, t.getMessage());
        return CompletableFuture.completedFuture(List.of()); // Return empty list gracefully
    }

    public List<SecurityAdvisory> osvFallback(String groupId, String artifactId, String version, Throwable t) {
        return osvFallbackFuture(groupId, artifactId, version, t).join();
    }

    /**
     * Parse a single OSV vulnerability JSON node into a SecurityAdvisory.
     */
    private SecurityAdvisory parseOsvVulnerability(JsonNode vuln, String queryVersion) {
        String id = vuln.path("id").asText("UNKNOWN");
        String summary = vuln.path("summary").asText(id);
        String details = vuln.has("details") ? vuln.path("details").asText() : null;

        // Published / modified dates
        String published = vuln.path("published").asText(null);
        String modified = vuln.path("modified").asText(null);

        // Aliases (CVE-xxxx, GHSA-xxxx, etc.)
        List<String> aliases = new ArrayList<>();
        aliases.add(id);
        JsonNode aliasNode = vuln.path("aliases");
        if (aliasNode.isArray()) {
            for (JsonNode a : aliasNode) {
                String alias = a.asText();
                if (!alias.equals(id))
                    aliases.add(alias);
            }
        }

        // CWE IDs from database_specific
        List<String> cweIds = new ArrayList<>();
        JsonNode cwes = vuln.path("database_specific").path("cwe_ids");
        if (cwes.isArray()) {
            for (JsonNode c : cwes)
                cweIds.add(c.asText());
        }

        // Severity from severity array or database_specific
        Severity severity = Severity.UNKNOWN;
        double cvssScore = -1;
        String cvssVector = null;

        JsonNode severityArray = vuln.path("severity");
        if (severityArray.isArray() && !severityArray.isEmpty()) {
            for (JsonNode s : severityArray) {
                String type = s.path("type").asText();
                String score = s.path("score").asText();
                if ("CVSS_V3".equals(type) || "CVSS_V4".equals(type)) {
                    cvssVector = score;
                    cvssScore = extractCvssScore(score);
                    severity = cvssToSeverity(cvssScore);
                    break;
                }
            }
        }

        // Fallback: try database_specific.severity
        if (severity == Severity.UNKNOWN) {
            String dbSeverity = vuln.path("database_specific").path("severity").asText("");
            if (!dbSeverity.isEmpty()) {
                severity = parseSeverityString(dbSeverity);
            }
        }

        // Find fixed version from affected ranges
        String fixedVersion = findFixedVersion(vuln, queryVersion);

        // Reference URL
        String referenceUrl = null;
        JsonNode refs = vuln.path("references");
        if (refs.isArray()) {
            for (JsonNode ref : refs) {
                String refType = ref.path("type").asText();
                if ("ADVISORY".equals(refType) || "WEB".equals(refType)) {
                    referenceUrl = ref.path("url").asText(null);
                    break;
                }
            }
        }
        // Fallback: construct OSV URL
        if (referenceUrl == null) {
            referenceUrl = "https://osv.dev/vulnerability/" + id;
        }

        return new SecurityAdvisory(
                id, summary, details,
                severity, cvssScore, cvssVector,
                cweIds, aliases,
                published, modified,
                fixedVersion, referenceUrl);
    }

    // ──────────────────────── Helpers ────────────────────────

    /**
     * Find the first "fixed" version from affected ranges.
     */
    private String findFixedVersion(JsonNode vuln, String queryVersion) {
        JsonNode affected = vuln.path("affected");
        if (!affected.isArray())
            return null;

        for (JsonNode aff : affected) {
            // Check if this affected entry matches our package
            String ecosystem = aff.path("package").path("ecosystem").asText("");
            if (!"Maven".equals(ecosystem))
                continue;

            JsonNode ranges = aff.path("ranges");
            if (!ranges.isArray())
                continue;

            for (JsonNode range : ranges) {
                JsonNode events = range.path("events");
                if (!events.isArray())
                    continue;

                for (JsonNode event : events) {
                    if (event.has("fixed")) {
                        return event.get("fixed").asText();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Extract CVSS base score from a CVSS vector string.
     * E.g., "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H" → computed from known
     * databases.
     * Since OSV stores the vector not the numeric score, we use the
     * database_specific score if available.
     */
    private double extractCvssScore(String cvssVector) {
        if (cvssVector == null || !cvssVector.startsWith("CVSS:"))
            return -1;
        // Approximate from vector — use severity bands
        // For a proper implementation you'd use a CVSS calculator library
        // For now, we'll rely on the severity string from database_specific
        return -1;
    }

    private Severity cvssToSeverity(double score) {
        if (score < 0)
            return Severity.UNKNOWN;
        if (score >= 9.0)
            return Severity.CRITICAL;
        if (score >= 7.0)
            return Severity.HIGH;
        if (score >= 4.0)
            return Severity.MEDIUM;
        return Severity.LOW;
    }

    private Severity parseSeverityString(String s) {
        return switch (s.toUpperCase()) {
            case "CRITICAL" -> Severity.CRITICAL;
            case "HIGH" -> Severity.HIGH;
            case "MODERATE", "MEDIUM" -> Severity.MEDIUM;
            case "LOW" -> Severity.LOW;
            default -> Severity.UNKNOWN;
        };
    }

    private Severity maxSeverity(Severity a, Severity b) {
        if (a == null)
            return b;
        if (b == null)
            return a;
        return severityOrdinal(a) <= severityOrdinal(b) ? a : b;
    }

    /** Lower ordinal = more severe. */
    private int severityOrdinal(Severity s) {
        return switch (s) {
            case CRITICAL -> 0;
            case HIGH -> 1;
            case MEDIUM -> 2;
            case LOW -> 3;
            case UNKNOWN -> 4;
        };
    }

    // ──────────────────────── NVD CVSS Enrichment ─────────────────────────

    /**
     * For any advisory that has a CVE ID alias but no numeric CVSS score,
     * call the NVD API to get the real numeric score. This supplements OSV data
     * which only provides CVSS vectors, not numeric scores.
     * Rate-limited: NVD allows ~5 req/s without an API key.
     */
    private List<SecurityAdvisory> enrichWithNvdCvss(List<SecurityAdvisory> advisories) {
        List<SecurityAdvisory> enriched = new ArrayList<>();
        for (SecurityAdvisory adv : advisories) {
            if (adv.cvssScore() > 0) {
                enriched.add(adv); // already has a score
                continue;
            }
            // Find any CVE alias
            String cveId = adv.aliases().stream()
                    .filter(a -> a.startsWith("CVE-"))
                    .findFirst()
                    .orElse(null);

            if (cveId == null) {
                // No CVE — try to parse from CVSS vector string
                double parsedScore = parseCvssVector(adv.cvssVector());
                if (parsedScore > 0) {
                    Severity newSev = cvssToSeverity(parsedScore);
                    enriched.add(new SecurityAdvisory(
                            adv.id(), adv.summary(), adv.details(),
                            newSev, parsedScore, adv.cvssVector(),
                            adv.cweIds(), adv.aliases(),
                            adv.published(), adv.modified(),
                            adv.fixedVersion(), adv.referenceUrl()));
                } else {
                    enriched.add(adv);
                }
                continue;
            }

            // Query NVD for the CVE
            try {
                double nvdScore = fetchNvdCvssScore(cveId);
                if (nvdScore > 0) {
                    Severity newSev = cvssToSeverity(nvdScore);
                    enriched.add(new SecurityAdvisory(
                            adv.id(), adv.summary(), adv.details(),
                            newSev, nvdScore, adv.cvssVector(),
                            adv.cweIds(), adv.aliases(),
                            adv.published(), adv.modified(),
                            adv.fixedVersion(), adv.referenceUrl()));
                } else {
                    enriched.add(adv);
                }
            } catch (Exception e) {
                enriched.add(adv); // NVD lookup failed — keep original
            }
        }
        return enriched;
    }

    /**
     * Query NVD 2.0 API for a CVE's base CVSS score.
     * Returns -1 if unavailable.
     */
    private double fetchNvdCvssScore(String cveId) throws Exception {
        String url = NVD_CVE_URL + cveId;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "modernmvn/1.0")
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            return -1;

        JsonNode root = objectMapper.readTree(resp.body());
        JsonNode vulns = root.path("vulnerabilities");
        if (!vulns.isArray() || vulns.isEmpty())
            return -1;

        JsonNode cve = vulns.get(0).path("cve");

        // Try CVSS v3.1 first, then v3.0, then v2.0
        for (String metricKey : List.of("cvssMetricV31", "cvssMetricV30", "cvssMetricV2")) {
            JsonNode metrics = cve.path("metrics").path(metricKey);
            if (metrics.isArray() && !metrics.isEmpty()) {
                JsonNode cvssData = metrics.get(0).path("cvssData");
                if (cvssData.has("baseScore")) {
                    return cvssData.get("baseScore").asDouble(-1);
                }
            }
        }
        return -1;
    }

    /**
     * Approximate CVSS score from a CVSS vector string.
     * Uses the AV:N/AC:L/PR:N/UI:N impact metrics as heuristics.
     * For a full implementation integrate the CVSS calculator library.
     */
    private double parseCvssVector(String vector) {
        if (vector == null || !vector.startsWith("CVSS:"))
            return -1;

        // Extract component parts and derive a rough score
        // High impact: AV:N (network) + AC:L (low) + no auth + C:H/I:H/A:H
        boolean networkReachable = vector.contains("AV:N");
        boolean lowComplexity = vector.contains("AC:L");
        boolean noPriv = vector.contains("PR:N");
        boolean highConf = vector.contains("C:H");
        boolean highInteg = vector.contains("I:H");
        boolean highAvail = vector.contains("A:H");

        double score = 5.0;
        if (networkReachable)
            score += 1.0;
        if (lowComplexity)
            score += 0.5;
        if (noPriv)
            score += 0.5;
        if (highConf)
            score += 1.0;
        if (highInteg)
            score += 1.0;
        if (highAvail)
            score += 0.5;

        return Math.min(10.0, score);
    }

}
