package com.modernmvn.backend.dto;

/**
 * Represents a single version of a Maven artifact with metadata.
 */
public record ArtifactVersion(
                String version,
                String packaging,
                long timestamp,
                String repository,
                boolean isRelease,
                boolean isRecommended,
                Integer vulnerabilityCount) {

        /** Convenience constructor for callers that don't have vuln data yet. */
        public ArtifactVersion(String version, String packaging, long timestamp,
                        String repository, boolean isRelease, boolean isRecommended) {
                this(version, packaging, timestamp, repository, isRelease, isRecommended, null);
        }
}
