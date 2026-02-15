package com.modernmvn.backend.dto;

import java.util.List;

/**
 * Result of analyzing a multi-module POM project.
 * Contains the parent module info and resolved dependency trees for each child
 * module.
 */
public record MultiModuleResult(
        String parentGroupId,
        String parentArtifactId,
        String parentVersion,
        boolean isMultiModule,
        List<ModuleInfo> modules,
        DependencyNode mergedTree) {

    /**
     * Information about a single module in a multi-module project.
     */
    public record ModuleInfo(
            String moduleName,
            String groupId,
            String artifactId,
            String version,
            String packaging,
            DependencyNode dependencyTree) {
    }
}
