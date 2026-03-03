package com.modernmvn.backend.controller;

import com.modernmvn.backend.dto.VersionIntelligence;
import com.modernmvn.backend.dto.VersionIntelligence.*;
import com.modernmvn.backend.dto.VulnerabilityReport;
import com.modernmvn.backend.dto.ArtifactVersion;
import com.modernmvn.backend.entity.SecuritySummaryEntity;
import com.modernmvn.backend.service.ArtifactIndexingService;
import com.modernmvn.backend.service.MavenCentralService;
import com.modernmvn.backend.service.SecurityService;
import com.modernmvn.backend.dto.SecurityAdvisory.Severity;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/security")
@Validated
public class SecurityController {

    private final MavenCentralService mavenCentralService;
    private final ArtifactIndexingService indexingService;
    private final SecurityService securityService; // only for stability scoring (pure computation)

    public SecurityController(MavenCentralService mavenCentralService,
            ArtifactIndexingService indexingService,
            SecurityService securityService) {
        this.mavenCentralService = mavenCentralService;
        this.indexingService = indexingService;
        this.securityService = securityService;
    }

    /**
     * Get vulnerability report for a specific artifact version.
     * GET /api/security/{groupId}/{artifactId}/{version}
     */
    @GetMapping("/{groupId}/{artifactId}/{version}")
    public ResponseEntity<?> getVulnerabilities(
            @PathVariable @Pattern(regexp = "[a-zA-Z0-9._-]+") String groupId,
            @PathVariable @Pattern(regexp = "[a-zA-Z0-9._-]+") String artifactId,
            @PathVariable @Pattern(regexp = "[a-zA-Z0-9._-]+") String version) {
        try {
            // Read from DB (instant)
            VulnerabilityReport report = getReportFromDbOrLive(groupId, artifactId, version);

            if (report == null) {
                // Graceful degeneracy: return a status indicating indexing is in progress
                return ResponseEntity.ok(Map.of(
                        "groupId", groupId,
                        "artifactId", artifactId,
                        "version", version,
                        "status", "INDEXING",
                        "indicator", SafetyIndicator.UNKNOWN,
                        "message", "Security analysis in progress — check back soon"));
            }

            return ResponseEntity.ok(report);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to fetch vulnerabilities: " + cause.getMessage()));
        }
    }

    /**
     * Get version intelligence (stability + security) for an artifact.
     * Assesses the latest N versions and returns a recommended version.
     * GET /api/security/{groupId}/{artifactId}/intelligence?versions=10
     */
    @GetMapping("/{groupId}/{artifactId}/intelligence")
    public ResponseEntity<?> getVersionIntelligence(
            @PathVariable @Pattern(regexp = "[a-zA-Z0-9._-]+") String groupId,
            @PathVariable @Pattern(regexp = "[a-zA-Z0-9._-]+") String artifactId,
            @RequestParam(defaultValue = "10") int versions) {
        try {
            // Get version list from Maven Central
            var artifactInfo = mavenCentralService.getArtifactInfo(groupId, artifactId);
            List<ArtifactVersion> allVersions = artifactInfo.versions();

            // Limit to requested number
            int limit = Math.min(versions, allVersions.size());
            List<ArtifactVersion> toAssess = allVersions.subList(0, limit);

            // Trigger async background indexing so eventual requests have complete data
            indexingService.indexAllVersionsAsync(groupId, artifactId);

            // Assess each version
            List<VersionAssessment> assessments = new ArrayList<>();
            VersionAssessment bestAssessment = null;

            for (ArtifactVersion v : toAssess) {
                VersionAssessment assessment = assessVersionFromDb(
                        groupId, artifactId,
                        v.version(), v.isRelease(), v.timestamp());
                assessments.add(assessment);

                // Track recommended: prefer SAFE + STABLE releases
                if (v.isRelease() && assessment.safetyIndicator() == SafetyIndicator.SAFE) {
                    if (bestAssessment == null ||
                            assessment.stabilityScore() > bestAssessment.stabilityScore()) {
                        bestAssessment = assessment;
                    }
                }
            }

            // Fallback: pick first release if no SAFE version found
            if (bestAssessment == null && !assessments.isEmpty()) {
                bestAssessment = assessments.stream()
                        .filter(VersionAssessment::isRelease)
                        .findFirst()
                        .orElse(assessments.get(0));
            }

            VersionIntelligence intelligence = new VersionIntelligence(groupId, artifactId, bestAssessment,
                    assessments);
            return ResponseEntity.ok(intelligence);

        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(404).body(Map.of("error", cause.getMessage()));
            }
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to compute intelligence: " + cause.getMessage()));
        }
    }

    /**
     * Quick safety check for a specific GAV — returns just the safety indicator.
     * Useful for badges and inline status checks.
     * GET /api/security/{groupId}/{artifactId}/{version}/badge
     */
    @GetMapping("/{groupId}/{artifactId}/{version}/badge")
    public ResponseEntity<?> getSafetyBadge(
            @PathVariable @Pattern(regexp = "[a-zA-Z0-9._-]+") String groupId,
            @PathVariable @Pattern(regexp = "[a-zA-Z0-9._-]+") String artifactId,
            @PathVariable @Pattern(regexp = "[a-zA-Z0-9._-]+") String version) {
        try {
            VulnerabilityReport report = getReportFromDbOrLive(groupId, artifactId, version);

            SafetyIndicator indicator;
            String label;
            int vulnCount;
            String severity;

            if (report == null) {
                indicator = SafetyIndicator.UNKNOWN;
                label = "Security analysis in progress — check back soon";
                vulnCount = 0;
                severity = "PENDING";
            } else {
                if (report.criticalCount() > 0 || report.highCount() > 0) {
                    indicator = SafetyIndicator.DANGER;
                } else if (report.mediumCount() > 0) {
                    indicator = SafetyIndicator.WARNING;
                } else if (report.lowCount() > 0) {
                    indicator = SafetyIndicator.CAUTION;
                } else {
                    indicator = SafetyIndicator.SAFE;
                }

                label = switch (indicator) {
                    case SAFE -> "No known vulnerabilities";
                    case CAUTION -> report.totalVulnerabilities() + " low-severity issue(s)";
                    case WARNING -> report.totalVulnerabilities() + " vulnerability(ies) found";
                    case DANGER -> report.totalVulnerabilities() + " security issue(s) — action recommended";
                    default -> "No known vulnerabilities";
                };
                vulnCount = report.totalVulnerabilities();
                severity = report.highestSeverity() != null ? report.highestSeverity().toString() : "NONE";
            }

            Map<String, Object> responseBlock = Map.<String, Object>of(
                    "groupId", groupId,
                    "artifactId", artifactId,
                    "version", version,
                    "indicator", indicator,
                    "label", label,
                    "vulnerabilityCount", vulnCount,
                    "highestSeverity", severity);

            return ResponseEntity.ok(responseBlock);

        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Badge lookup failed: " + cause.getMessage()));
        }
    }

    /**
     * Historical vulnerability trend for an artifact.
     * Returns all scan data points within the given time window, sorted by date.
     * Powered by PostgreSQL persistence layer.
     * GET /api/security/{groupId}/{artifactId}/trend?days=90
     */
    @GetMapping("/{groupId}/{artifactId}/trend")
    public ResponseEntity<?> getVulnerabilityTrend(
            @PathVariable @Pattern(regexp = "[a-zA-Z0-9._-]+") String groupId,
            @PathVariable @Pattern(regexp = "[a-zA-Z0-9._-]+") String artifactId,
            @RequestParam(defaultValue = "90") int days) {
        try {
            days = Math.min(days, 365);
            Instant cutoff = Instant.now().minus(days, java.time.temporal.ChronoUnit.DAYS);
            // Read from the precomputed security summaries (historical trend)
            List<SecuritySummaryEntity> history = indexingService.getSecurityHistory(groupId, artifactId);

            // Convert to trend data points
            List<Map<String, Object>> dataPoints = new ArrayList<>();
            for (SecuritySummaryEntity s : history) {
                if (s.getLastCalculatedAt() != null && s.getLastCalculatedAt().isBefore(cutoff)) {
                    continue;
                }
                dataPoints.add(Map.of(
                        "date", s.getLastCalculatedAt() != null ? s.getLastCalculatedAt().toString() : "",
                        "totalVulnerabilities", s.getTotalVulns(),
                        "criticalCount", s.getCriticalCount(),
                        "highCount", s.getHighCount(),
                        "maxCvssScore", Math.max(0.0, s.getMaxCvss())));
            }

            return ResponseEntity.ok(Map.of(
                    "groupId", groupId,
                    "artifactId", artifactId,
                    "days", days,
                    "dataPoints", dataPoints));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to fetch trend data: " + e.getMessage()));
        }
    }

    // ──────────────────────── DB-first helper ────────────────────────

    /**
     * Try to build a VulnerabilityReport from the precomputed DB summary.
     * On a DB miss, triggers ensureIndexed() to persist data, then reads from DB.
     * Falls back to live OSV only as a last resort.
     */
    private VulnerabilityReport getReportFromDbOrLive(String groupId, String artifactId, String version) {
        // 1. Try DB first (instant)
        Optional<SecuritySummaryEntity> opt = indexingService.getSecuritySummary(groupId, artifactId, version);
        if (opt.isPresent()) {
            return buildReportFromSummary(opt.get(), groupId, artifactId, version);
        }

        // 2. DB miss — trigger indexing (builds DB data once for all endpoints)
        try {
            indexingService.ensureIndexed(groupId, artifactId, version);
            opt = indexingService.getSecuritySummary(groupId, artifactId, version);
            if (opt.isPresent()) {
                return buildReportFromSummary(opt.get(), groupId, artifactId, version);
            }
        } catch (Exception e) {
            // Indexing failed — fall through
        }

        // 3. Return null instead of throwing 500 — controller will handle graceful
        // degeneracy
        return null;
    }

    private VulnerabilityReport buildReportFromSummary(
            SecuritySummaryEntity s, String groupId, String artifactId, String version) {
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
                highest, List.of(),
                VulnerabilityReport.DISCLAIMER);
    }

    // ──────────────────────── DB-only Version Assessment ────────────────────────

    /**
     * Assess a version using only DB-precomputed data. No live OSV calls.
     * Ensures the version is indexed first, then reads from the security summary.
     */
    private VersionAssessment assessVersionFromDb(
            String groupId, String artifactId,
            String version, boolean isRelease, long timestamp) {
        // Only read from DB. No blocking ensureIndexed() here.
        // We rely on getVersionIntelligence() triggering async background indexing.

        // 2. Read from DB
        VulnerabilityReport report;
        Optional<SecuritySummaryEntity> opt = indexingService.getSecuritySummary(groupId, artifactId, version);
        boolean isIndexed = opt.isPresent();

        if (isIndexed) {
            report = buildReportFromSummary(opt.get(), groupId, artifactId, version);
        } else {
            report = VulnerabilityReport.clean(groupId, artifactId, version);
        }

        // 3. Compute stability (pure computation — no external calls)
        StabilityGrade stability = securityService.computeStabilityGrade(version, isRelease, timestamp);
        double stabilityScore = securityService.computeStabilityScore(version, isRelease, timestamp);

        // 4. Compute combined safety indicator
        SafetyIndicator safety;
        if (!isIndexed) {
            safety = SafetyIndicator.UNKNOWN;
        } else if (report.criticalCount() > 0 || report.highCount() > 0) {
            safety = SafetyIndicator.DANGER;
        } else if (report.mediumCount() > 0) {
            safety = SafetyIndicator.WARNING;
        } else if (report.lowCount() > 0) {
            safety = SafetyIndicator.CAUTION;
        } else if (stability == StabilityGrade.PRE_RELEASE) {
            safety = SafetyIndicator.CAUTION;
        } else if (stability == StabilityGrade.OUTDATED) {
            safety = SafetyIndicator.WARNING;
        } else {
            safety = SafetyIndicator.SAFE;
        }

        String safetyLabel = switch (safety) {
            case SAFE -> "Safe to use";
            case CAUTION -> "Use with caution";
            case WARNING -> report.totalVulnerabilities() > 0
                    ? report.totalVulnerabilities() + " known vulnerabilities"
                    : "Outdated — consider upgrading";
            case DANGER -> report.totalVulnerabilities() + " security issues found";
            case UNKNOWN -> "Security analysis in progress";
        };

        return new VersionAssessment(
                version, isRelease, timestamp,
                report.totalVulnerabilities(), report.highestSeverity(),
                stability, stabilityScore,
                safety, safetyLabel);
    }
}
