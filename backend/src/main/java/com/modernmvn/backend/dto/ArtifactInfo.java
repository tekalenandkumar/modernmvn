package com.modernmvn.backend.dto;

import java.util.List;

/**
 * Summary info for an artifact (groupId:artifactId), including all known
 * versions
 * and recommended version.
 */
public record ArtifactInfo(
        String groupId,
        String artifactId,
        String latestVersion,
        String latestReleaseVersion,
        String packaging,
        long versionCount,
        List<ArtifactVersion> versions,
        String description,
        String url,
        List<LicenseInfo> licenses,
        long lastUpdated) {

    public record LicenseInfo(String name, String url) {
    }
}
