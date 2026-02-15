package com.modernmvn.backend.dto;

import java.util.List;

/**
 * Request payload for POM file upload with optional configuration.
 */
public record PomUploadRequest(
        String pomContent,
        List<String> customRepositories, // Optional list of additional repo URLs
        boolean detectMultiModule // Whether to detect <modules> block
) {
    public PomUploadRequest {
        if (customRepositories == null) {
            customRepositories = List.of();
        }
    }
}
