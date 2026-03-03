package com.modernmvn.backend.service;

import com.modernmvn.backend.config.MavenConfig;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.util.*;

@Service
public class MavenResolutionService {

    private static final Logger log = LoggerFactory.getLogger(MavenResolutionService.class);

    private final RepositorySystem repositorySystem;
    private final MavenConfig mavenConfig;

    // Limits for security
    private static final int MAX_POM_SIZE_BYTES = 512 * 1024; // 512 KB
    private static final int MAX_CUSTOM_REPOS = 5;
    private static final int MAX_RESOLUTION_DEPTH = 10;
    private static final Set<String> ALLOWED_REPO_SCHEMES = Set.of("https");

    public MavenResolutionService(RepositorySystem repositorySystem, MavenConfig mavenConfig) {
        this.repositorySystem = repositorySystem;
        this.mavenConfig = mavenConfig;
    }

    @Cacheable(value = "mavenDependencies_v2", key = "#groupId + ':' + #artifactId + ':' + #version")
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

            // Create a fresh session for this resolution to ensure thread safety
            RepositorySystemSession session = mavenConfig.createSession(repositorySystem);
            CollectResult collectResult = repositorySystem.collectDependencies(session, collectRequest);

            if (!collectResult.getExceptions().isEmpty()) {
                log.warn("Resolution exceptions for {}:{}:{}", groupId, artifactId, version);
                for (Exception e : collectResult.getExceptions()) {
                    log.debug("Resolution detail: ", e);
                }
            }

            return convertToDto(collectResult.getRoot(), 0);
        } catch (Exception e) {
            log.error("Failed to resolve {}:{}:{}: {}", groupId, artifactId, version, e.getMessage(), e);
            String errorMsg = e.getClass().getSimpleName();
            if (e.getMessage() != null)
                errorMsg += ": " + e.getMessage();
            if (e.getCause() != null)
                errorMsg += " (Cause: " + e.getCause().getClass().getSimpleName() + ")";

            return new DependencyNode(groupId, artifactId, version, "compile", "jar", Collections.emptyList(), "ERROR",
                    errorMsg);
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
            throw e;
        } catch (Exception e) {
            log.error("Failed to resolve from POM: {}", e.getMessage());
            return new DependencyNode("unknown", "unknown", "0.0.0", "compile", "pom", Collections.emptyList(),
                    "ERROR", e.getMessage());
        }
    }

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
                DependencyNode tree = resolveModelAsTree(model, customRepoUrls);
                MultiModuleResult.ModuleInfo singleModule = new MultiModuleResult.ModuleInfo(
                        parentArtifactId, parentGroupId, parentArtifactId, parentVersion,
                        model.getPackaging() != null ? model.getPackaging() : "jar", tree);
                return new MultiModuleResult(
                        parentGroupId, parentArtifactId, parentVersion,
                        false, List.of(singleModule), tree);
            }

            List<MultiModuleResult.ModuleInfo> modules = new ArrayList<>();
            List<DependencyNode> allModuleChildren = new ArrayList<>();

            DependencyNode parentTree = resolveModelAsTree(model, customRepoUrls);

            if (moduleNames == null)
                return null; // Safe guard for linter
            for (String moduleName : moduleNames) {
                DependencyNode localModuleNode = new DependencyNode(
                        parentGroupId, moduleName, parentVersion,
                        "compile", "jar", Collections.emptyList(),
                        "LOCAL", "Module detected from parent POM.");

                MultiModuleResult.ModuleInfo moduleInfo = new MultiModuleResult.ModuleInfo(
                        moduleName, parentGroupId, moduleName, parentVersion, "jar", localModuleNode);
                modules.add(moduleInfo);
                allModuleChildren.add(localModuleNode);
            }

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
            log.error("Failed to resolve multi-module POM: {}", e.getMessage());
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

            RepositorySystemSession session = mavenConfig.createSession(repositorySystem);
            CollectResult collectResult = repositorySystem.collectDependencies(session, collectRequest);

            List<DependencyNode> children = new ArrayList<>();
            for (org.eclipse.aether.graph.DependencyNode child : collectResult.getRoot().getChildren()) {
                children.add(convertToDto(child, 1));
            }

            return new DependencyNode(groupId, artifactId, version, "compile", "pom", children, "RESOLVED", null);
        } catch (Exception e) {
            log.error("Internal model resolution failed for {}:{}:{}: {}", groupId, artifactId, version,
                    e.getMessage());
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

    private List<RemoteRepository> buildRepositoryList(List<String> customRepoUrls) {
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
                throw new IllegalArgumentException("Repository URL must use HTTPS. Got: " + url);
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("Invalid repository URL: " + url);
            }
        } catch (IllegalArgumentException e) {
            throw e;
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

    private DependencyNode convertToDto(org.eclipse.aether.graph.DependencyNode aetherNode, int depth) {
        List<DependencyNode> children = new ArrayList<>();

        if (depth < MAX_RESOLUTION_DEPTH) {
            for (org.eclipse.aether.graph.DependencyNode child : aetherNode.getChildren()) {
                children.add(convertToDto(child, depth + 1));
            }
        }

        org.eclipse.aether.artifact.Artifact artifact = aetherNode.getArtifact();
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
            if (("test".equals(scope) || "provided".equals(scope)) && aetherNode.getDependency().isOptional()) {
                resolutionStatus = "OPTIONAL";
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
