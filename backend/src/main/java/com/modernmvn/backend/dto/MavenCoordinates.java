package com.modernmvn.backend.dto;

public record MavenCoordinates(
        String groupId,
        String artifactId,
        String version) {
}
