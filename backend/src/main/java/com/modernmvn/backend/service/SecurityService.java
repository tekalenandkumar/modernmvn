package com.modernmvn.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.modernmvn.backend.dto.SecurityAdvisory;
import com.modernmvn.backend.dto.SecurityAdvisory.Severity;
import com.modernmvn.backend.dto.VersionIntelligence;
import com.modernmvn.backend.dto.VersionIntelligence.*;
import com.modernmvn.backend.dto.VulnerabilityReport;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

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
 */
@Service
public class SecurityService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // OSV.dev is free, no API key required
    private static final String OSV_QUERY_URL = "https://api.osv.dev/v1/query";
    private static final String OSV_VULN_URL = "https://api.osv.dev/v1/vulns/";

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

    // ──────────────────────── Vulnerability Report ────────────────────────

    /**
     * Query OSV.dev for known vulnerabilities of a specific GAV.
     */
    @Cacheable(value = "vulnerabilities", key = "#groupId + ':' + #artifactId + ':' + #version")
    public VulnerabilityReport getVulnerabilities(String groupId, String artifactId, String version) {
        try {
            List<SecurityAdvisory> advisories = queryOsv(groupId, artifactId, version);

            int critical = 0, high = 0, medium = 0, low = 0;
            Severity highest = null;
            for (SecurityAdvisory adv : advisories) {
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

            return new VulnerabilityReport(
                    groupId, artifactId, version,
                    advisories.size(),
                    critical, high, medium, low,
                    highest, advisories,
                    VulnerabilityReport.DISCLAIMER);

        } catch (Exception e) {
            // If OSV is down, return clean with a note rather than failing
            return VulnerabilityReport.clean(groupId, artifactId, version);
        }
    }

    // ──────────────────────── Version Intelligence ────────────────────────

    /**
     * Compute stability and safety assessment for specific versions.
     * Takes pre-fetched version data to avoid redundant Maven Central calls.
     */
    public VersionAssessment assessVersion(
            String groupId, String artifactId,
            String version, boolean isRelease, long timestamp) {
        // Get vulnerability count
        VulnerabilityReport report = getVulnerabilities(groupId, artifactId, version);

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
        };
    }

    // ──────────────────────── OSV.dev API ────────────────────────

    /**
     * Query the OSV.dev API for vulnerabilities affecting a Maven package version.
     */
    private List<SecurityAdvisory> queryOsv(String groupId, String artifactId, String version) throws Exception {
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

        // Sort by severity (critical first)
        advisories.sort(Comparator.comparingInt(a -> severityOrdinal(a.severity())));
        return advisories;
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
}
