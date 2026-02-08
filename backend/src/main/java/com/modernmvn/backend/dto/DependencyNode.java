package com.modernmvn.backend.dto;

import java.util.List;

public record DependencyNode(
        String groupId,
        String artifactId,
        String version,
        String scope,
        String type,
        List<DependencyNode> children,
        String resolutionStatus, // "RESOLVED", "CONFLICT", "MISSING"
        String conflictMessage) {
}
