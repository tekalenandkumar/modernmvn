package com.modernmvn.backend.dto;

import java.util.List;

public record DependencyNodeDto(
        String groupId,
        String artifactId,
        String version,
        String scope,
        List<DependencyNodeDto> children) {
}
