package com.modernmvn.backend.controller;

import com.modernmvn.backend.dto.VersionIntelligence;
import com.modernmvn.backend.dto.VersionIntelligence.*;
import com.modernmvn.backend.dto.VulnerabilityReport;
import com.modernmvn.backend.dto.ArtifactVersion;
import com.modernmvn.backend.service.MavenCentralService;
import com.modernmvn.backend.service.SecurityService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints for security vulnerability lookups and version intelligence.
 */
@RestController
@RequestMapping("/api/security")
public class SecurityController {

    private final SecurityService securityService;
    private final MavenCentralService mavenCentralService;

    public SecurityController(SecurityService securityService, MavenCentralService mavenCentralService) {
        this.securityService = securityService;
        this.mavenCentralService = mavenCentralService;
    }

    /**
     * Get vulnerability report for a specific artifact version.
     * GET /api/security/{groupId}/{artifactId}/{version}
     */
    @GetMapping("/{groupId}/{artifactId}/{version}")
    public ResponseEntity<?> getVulnerabilities(
            @PathVariable String groupId,
            @PathVariable String artifactId,
            @PathVariable String version) {
        try {
            VulnerabilityReport report = securityService.getVulnerabilities(groupId, artifactId, version);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to fetch vulnerabilities: " + e.getMessage()));
        }
    }

    /**
     * Get version intelligence (stability + security) for an artifact.
     * Assesses the latest N versions and returns a recommended version.
     * GET /api/security/{groupId}/{artifactId}/intelligence?versions=10
     */
    @GetMapping("/{groupId}/{artifactId}/intelligence")
    public ResponseEntity<?> getVersionIntelligence(
            @PathVariable String groupId,
            @PathVariable String artifactId,
            @RequestParam(defaultValue = "10") int versions) {
        try {
            // Get version list from Maven Central
            var artifactInfo = mavenCentralService.getArtifactInfo(groupId, artifactId);
            List<ArtifactVersion> allVersions = artifactInfo.versions();

            // Limit to requested number
            int limit = Math.min(versions, allVersions.size());
            List<ArtifactVersion> toAssess = allVersions.subList(0, limit);

            // Assess each version
            List<VersionAssessment> assessments = new ArrayList<>();
            VersionAssessment bestAssessment = null;

            for (ArtifactVersion v : toAssess) {
                VersionAssessment assessment = securityService.assessVersion(
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

            VersionIntelligence intelligence = new VersionIntelligence(
                    groupId, artifactId, bestAssessment, assessments);

            return ResponseEntity.ok(intelligence);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to compute intelligence: " + e.getMessage()));
        }
    }

    /**
     * Quick safety check for a specific GAV — returns just the safety indicator.
     * Useful for badges and inline status checks.
     * GET /api/security/{groupId}/{artifactId}/{version}/badge
     */
    @GetMapping("/{groupId}/{artifactId}/{version}/badge")
    public ResponseEntity<?> getSafetyBadge(
            @PathVariable String groupId,
            @PathVariable String artifactId,
            @PathVariable String version) {
        try {
            VulnerabilityReport report = securityService.getVulnerabilities(groupId, artifactId, version);

            SafetyIndicator indicator;
            if (report.criticalCount() > 0 || report.highCount() > 0) {
                indicator = SafetyIndicator.DANGER;
            } else if (report.mediumCount() > 0) {
                indicator = SafetyIndicator.WARNING;
            } else if (report.lowCount() > 0) {
                indicator = SafetyIndicator.CAUTION;
            } else {
                indicator = SafetyIndicator.SAFE;
            }

            String label = switch (indicator) {
                case SAFE -> "No known vulnerabilities";
                case CAUTION -> report.totalVulnerabilities() + " low-severity issue(s)";
                case WARNING -> report.totalVulnerabilities() + " vulnerability(ies) found";
                case DANGER -> report.totalVulnerabilities() + " security issue(s) — action recommended";
            };

            return ResponseEntity.ok(Map.of(
                    "groupId", groupId,
                    "artifactId", artifactId,
                    "version", version,
                    "indicator", indicator,
                    "label", label,
                    "vulnerabilityCount", report.totalVulnerabilities(),
                    "highestSeverity", report.highestSeverity() != null ? report.highestSeverity() : "NONE"));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Badge lookup failed: " + e.getMessage()));
        }
    }
}
