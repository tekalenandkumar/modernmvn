package com.modernmvn.backend.dto;

import java.util.List;
import java.util.Map;

/**
 * Detailed info for a specific artifact version, including dependency snippets
 * and transitive dependency metadata.
 */
public record ArtifactDetail(
        String groupId,
        String artifactId,
        String version,
        String packaging,
        String description,
        String url,
        String name,
        List<ArtifactInfo.LicenseInfo> licenses,
        Map<String, String> dependencySnippets,
        int dependencyCount,
        long timestamp) {
}
