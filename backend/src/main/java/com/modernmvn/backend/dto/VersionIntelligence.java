package com.modernmvn.backend.dto;

import java.util.List;

/**
 * Combined security + stability intelligence for an artifact.
 * Provides an at-a-glance safety assessment across all versions.
 */
public record VersionIntelligence(
        String groupId,
        String artifactId,
        VersionAssessment recommendedVersion,
        List<VersionAssessment> versions) {

    /**
     * Assessment for a single version covering security and stability.
     */
    public record VersionAssessment(
            String version,
            boolean isRelease,
            long timestamp,
            // Security
            int vulnerabilityCount,
            SecurityAdvisory.Severity highestSeverity,
            // Stability
            StabilityGrade stabilityGrade,
            double stabilityScore, // 0.0 – 100.0
            // Combined
            SafetyIndicator safetyIndicator,
            String safetyLabel // human-readable like "Safe to use", "Use with caution"
    ) {
    }

    /**
     * Version stability grade based on age, release status, and popularity.
     */
    public enum StabilityGrade {
        STABLE, // mature release, no pre-release qualifiers
        RECENT, // recent release, likely stable but unproven
        PRE_RELEASE, // alpha/beta/RC/SNAPSHOT
        OUTDATED, // very old, may have better alternatives
        UNKNOWN
    }

    /**
     * Traffic-light style safety indicator combining security + stability.
     */
    public enum SafetyIndicator {
        SAFE, // no known vulnerabilities, stable release
        CAUTION, // minor issues or pre-release
        WARNING, // known vulnerabilities (medium/low) or very outdated
        DANGER // critical or high-severity vulnerabilities
    }
}
