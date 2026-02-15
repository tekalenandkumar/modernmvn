package com.modernmvn.backend.service;

import com.modernmvn.backend.dto.DependencyNode;
import com.modernmvn.backend.dto.MultiModuleResult;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.util.*;

@Service
public class MavenResolutionService {

    private final RepositorySystem repositorySystem;
    private final RepositorySystemSession repositorySystemSession;

    // Limits for security
    private static final int MAX_POM_SIZE_BYTES = 512 * 1024; // 512 KB
    private static final int MAX_CUSTOM_REPOS = 5;
    private static final Set<String> ALLOWED_REPO_SCHEMES = Set.of("https");

    public MavenResolutionService(RepositorySystem repositorySystem, RepositorySystemSession repositorySystemSession) {
        this.repositorySystem = repositorySystem;
        this.repositorySystemSession = repositorySystemSession;
    }

    @org.springframework.cache.annotation.Cacheable(value = "mavenDependencies", key = "#groupId + ':' + #artifactId + ':' + #version")
    public DependencyNode resolveDependency(String groupId, String artifactId, String version) {
        return resolveDependencyWithRepos(groupId, artifactId, version, List.of());
    }

    public DependencyNode resolveDependencyWithRepos(String groupId, String artifactId, String version,
            List<String> customRepoUrls) {
        try {
            Artifact artifact = new DefaultArtifact(groupId, artifactId, "jar", version);
            Dependency dependency = new Dependency(artifact, "compile");

            List<RemoteRepository> repos = buildRepositoryList(customRepoUrls);

            CollectRequest collectRequest = new CollectRequest();
            collectRequest.setRoot(dependency);
            collectRequest.setRepositories(repos);

            CollectResult collectResult = repositorySystem.collectDependencies(repositorySystemSession, collectRequest);

            if (!collectResult.getExceptions().isEmpty()) {
                System.out.println("Resolution exceptions for " + groupId + ":" + artifactId + ":" + version);
                for (Exception e : collectResult.getExceptions()) {
                    e.printStackTrace();
                }
            }

            return convertToDto(collectResult.getRoot());
        } catch (Exception e) {
            e.printStackTrace();
            return new DependencyNode(groupId, artifactId, version, "compile", "jar", Collections.emptyList(), "ERROR",
                    e.getMessage());
        }
    }

    public DependencyNode resolveFromPom(String pomContent) {
        return resolveFromPomWithRepos(pomContent, List.of());
    }

    public DependencyNode resolveFromPomWithRepos(String pomContent, List<String> customRepoUrls) {
        validatePomSize(pomContent);
        try {
            MavenXpp3Reader reader = new MavenXpp3Reader();
            Model model = reader.read(new StringReader(pomContent));
            return resolveModelAsTree(model, customRepoUrls);
        } catch (IllegalArgumentException e) {
            throw e; // Re-throw validation errors
        } catch (Exception e) {
            e.printStackTrace();
            return new DependencyNode("unknown", "unknown", "0.0.0", "compile", "pom", Collections.emptyList(),
                    "ERROR", e.getMessage());
        }
    }

    /**
     * Analyze a POM for multi-module structure and resolve each module's
     * dependencies.
     * Modules listed in the parent POM's <modules> block are "detected" but since
     * we only receive
     * the parent POM text, child module POMs aren't available. We mark them as
     * LOCAL artifacts.
     */
    public MultiModuleResult resolveMultiModule(String pomContent, List<String> customRepoUrls) {
        validatePomSize(pomContent);
        try {
            MavenXpp3Reader reader = new MavenXpp3Reader();
            Model model = reader.read(new StringReader(pomContent));

            String parentGroupId = extractGroupId(model);
            String parentArtifactId = model.getArtifactId();
            String parentVersion = extractVersion(model);

            List<String> moduleNames = model.getModules();
            boolean isMultiModule = moduleNames != null && !moduleNames.isEmpty();

            if (!isMultiModule) {
                // Single module — resolve normally
                DependencyNode tree = resolveModelAsTree(model, customRepoUrls);
                MultiModuleResult.ModuleInfo singleModule = new MultiModuleResult.ModuleInfo(
                        parentArtifactId, parentGroupId, parentArtifactId, parentVersion,
                        model.getPackaging() != null ? model.getPackaging() : "jar", tree);
                return new MultiModuleResult(
                        parentGroupId, parentArtifactId, parentVersion,
                        false, List.of(singleModule), tree);
            }

            // Multi-module: create ModuleInfo for each declared module
            List<MultiModuleResult.ModuleInfo> modules = new ArrayList<>();
            List<DependencyNode> allModuleChildren = new ArrayList<>();

            // Also resolve the parent's own dependencies (if any)
            DependencyNode parentTree = resolveModelAsTree(model, customRepoUrls);

            for (String moduleName : moduleNames) {
                // We don't have the child POM content, so we create a LOCAL placeholder
                DependencyNode localModuleNode = new DependencyNode(
                        parentGroupId, moduleName, parentVersion,
                        "compile", "jar", Collections.emptyList(),
                        "LOCAL", "Module detected from parent POM. Upload individual module POM for full analysis.");

                MultiModuleResult.ModuleInfo moduleInfo = new MultiModuleResult.ModuleInfo(
                        moduleName, parentGroupId, moduleName, parentVersion, "jar", localModuleNode);
                modules.add(moduleInfo);
                allModuleChildren.add(localModuleNode);
            }

            // Merge: parent tree children + local module nodes
            List<DependencyNode> mergedChildren = new ArrayList<>(parentTree.children());
            mergedChildren.addAll(allModuleChildren);

            DependencyNode mergedTree = new DependencyNode(
                    parentGroupId, parentArtifactId, parentVersion,
                    "compile", "pom", mergedChildren, "RESOLVED", null);

            return new MultiModuleResult(
                    parentGroupId, parentArtifactId, parentVersion,
                    true, modules, mergedTree);

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            DependencyNode errorNode = new DependencyNode("unknown", "unknown", "0.0.0", "compile", "pom",
                    Collections.emptyList(), "ERROR", e.getMessage());
            return new MultiModuleResult("unknown", "unknown", "0.0.0", false, List.of(), errorNode);
        }
    }

    // ─── Internal helpers ────────────────────────────────────────────

    private DependencyNode resolveModelAsTree(Model model, List<String> customRepoUrls) {
        String groupId = extractGroupId(model);
        String artifactId = model.getArtifactId();
        String version = extractVersion(model);

        Map<String, String> properties = buildProperties(model, groupId, artifactId, version);

        List<Dependency> dependencies = new ArrayList<>();
        Set<String> localModuleArtifacts = new HashSet<>();
        if (model.getModules() != null) {
            for (String mod : model.getModules()) {
                localModuleArtifacts.add(groupId + ":" + mod);
            }
        }

        for (org.apache.maven.model.Dependency d : model.getDependencies()) {
            String dGroupId = interpolate(d.getGroupId(), properties);
            String dArtifactId = interpolate(d.getArtifactId(), properties);
            String dVersion = interpolate(d.getVersion(), properties);
            String dScope = d.getScope() != null ? d.getScope() : "compile";

            // Skip local module references — they won't exist in remote repos
            if (localModuleArtifacts.contains(dGroupId + ":" + dArtifactId)) {
                continue;
            }

            if (dVersion == null || dVersion.isEmpty()) {
                if (model.getParent() != null && dGroupId.startsWith("org.springframework.boot")) {
                    dVersion = model.getParent().getVersion();
                } else {
                    dVersion = "LATEST";
                }
            }

            Artifact artifact = new DefaultArtifact(dGroupId, dArtifactId, "jar", dVersion);
            dependencies.add(new Dependency(artifact, dScope));
        }

        List<RemoteRepository> repos = buildRepositoryList(customRepoUrls);

        try {
            CollectRequest collectRequest = new CollectRequest();
            collectRequest.setDependencies(dependencies);
            collectRequest.setRepositories(repos);

            CollectResult collectResult = repositorySystem.collectDependencies(repositorySystemSession, collectRequest);

            List<DependencyNode> children = new ArrayList<>();
            for (org.eclipse.aether.graph.DependencyNode child : collectResult.getRoot().getChildren()) {
                children.add(convertToDto(child));
            }

            return new DependencyNode(groupId, artifactId, version, "compile", "pom", children, "RESOLVED", null);
        } catch (Exception e) {
            e.printStackTrace();
            return new DependencyNode(groupId, artifactId, version, "compile", "pom", Collections.emptyList(), "ERROR",
                    e.getMessage());
        }
    }

    private void validatePomSize(String pomContent) {
        if (pomContent == null || pomContent.isBlank()) {
            throw new IllegalArgumentException("POM content cannot be empty.");
        }
        if (pomContent.getBytes().length > MAX_POM_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "POM content exceeds maximum allowed size of " + (MAX_POM_SIZE_BYTES / 1024) + " KB.");
        }
    }

    List<RemoteRepository> buildRepositoryList(List<String> customRepoUrls) {
        List<RemoteRepository> repos = new ArrayList<>();
        repos.add(new RemoteRepository.Builder("central", "default",
                "https://repo.maven.apache.org/maven2/").build());

        if (customRepoUrls != null) {
            if (customRepoUrls.size() > MAX_CUSTOM_REPOS) {
                throw new IllegalArgumentException("Maximum " + MAX_CUSTOM_REPOS + " custom repositories allowed.");
            }
            int idx = 0;
            for (String url : customRepoUrls) {
                if (url != null && !url.isBlank()) {
                    validateRepoUrl(url);
                    repos.add(new RemoteRepository.Builder("custom-" + idx++, "default", url.trim()).build());
                }
            }
        }
        return repos;
    }

    private void validateRepoUrl(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || !ALLOWED_REPO_SCHEMES.contains(scheme.toLowerCase())) {
                throw new IllegalArgumentException(
                        "Repository URL must use HTTPS. Got: " + url);
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("Invalid repository URL: " + url);
            }
        } catch (IllegalArgumentException e) {
            if (e.getMessage().startsWith("Repository URL") || e.getMessage().startsWith("Invalid repository")) {
                throw e;
            }
            throw new IllegalArgumentException("Invalid repository URL format: " + url);
        }
    }

    private String extractGroupId(Model model) {
        return model.getGroupId() != null ? model.getGroupId()
                : (model.getParent() != null ? model.getParent().getGroupId() : "unknown");
    }

    private String extractVersion(Model model) {
        return model.getVersion() != null ? model.getVersion()
                : (model.getParent() != null ? model.getParent().getVersion() : "0.0.1-SNAPSHOT");
    }

    private Map<String, String> buildProperties(Model model, String groupId, String artifactId, String version) {
        Map<String, String> properties = new HashMap<>();
        if (model.getProperties() != null) {
            for (String key : model.getProperties().stringPropertyNames()) {
                properties.put(key, model.getProperties().getProperty(key));
            }
        }
        properties.put("project.groupId", groupId);
        properties.put("project.artifactId", artifactId);
        properties.put("project.version", version);
        if (!properties.containsKey("java.version")) {
            properties.put("java.version", "17");
        }
        return properties;
    }

    private String interpolate(String value, Map<String, String> properties) {
        if (value == null)
            return null;
        if (!value.contains("${"))
            return value;

        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            if (value.contains(placeholder)) {
                value = value.replace(placeholder, entry.getValue());
            }
        }
        return value;
    }

    private DependencyNode convertToDto(org.eclipse.aether.graph.DependencyNode aetherNode) {
        List<DependencyNode> children = new ArrayList<>();
        for (org.eclipse.aether.graph.DependencyNode child : aetherNode.getChildren()) {
            children.add(convertToDto(child));
        }

        Artifact artifact = aetherNode.getArtifact();

        String resolutionStatus = "RESOLVED";
        String conflictMessage = null;

        org.eclipse.aether.graph.DependencyNode winner = (org.eclipse.aether.graph.DependencyNode) aetherNode.getData()
                .get(org.eclipse.aether.util.graph.transformer.ConflictResolver.NODE_DATA_WINNER);

        if (winner != null) {
            resolutionStatus = "CONFLICT";
            if (!winner.getArtifact().getVersion().equals(artifact.getVersion())) {
                conflictMessage = "Conflict: " + artifact.getVersion() + " omitted for "
                        + winner.getArtifact().getVersion();
            } else {
                resolutionStatus = "RESOLVED";
            }
        } else {
            String scope = aetherNode.getDependency() != null ? aetherNode.getDependency().getScope() : "compile";
            if ("test".equals(scope) || "provided".equals(scope)) {
                if (aetherNode.getDependency() != null && aetherNode.getDependency().isOptional()) {
                    resolutionStatus = "OPTIONAL";
                }
            }
        }

        return new DependencyNode(
                artifact.getGroupId(),
                artifact.getArtifactId(),
                artifact.getVersion(),
                aetherNode.getDependency() != null ? aetherNode.getDependency().getScope() : "compile",
                artifact.getExtension(),
                children,
                resolutionStatus,
                conflictMessage);
    }
}
