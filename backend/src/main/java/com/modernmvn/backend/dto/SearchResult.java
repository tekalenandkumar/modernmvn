package com.modernmvn.backend.dto;

import java.util.List;

/**
 * Represents the result of an artifact search query.
 */
public record SearchResult(
        String query,
        int totalResults,
        int page,
        int pageSize,
        List<SearchResultItem> items) {

    /**
     * A single search result item — a lightweight summary of an artifact.
     */
    public record SearchResultItem(
            String groupId,
            String artifactId,
            String latestVersion,
            String packaging,
            String description,
            long timestamp,
            long versionCount) {
    }
}
