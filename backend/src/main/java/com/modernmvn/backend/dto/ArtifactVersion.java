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
        boolean isRecommended) {
}
