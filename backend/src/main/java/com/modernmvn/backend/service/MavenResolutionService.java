package com.modernmvn.backend.service;

import com.modernmvn.backend.dto.DependencyNode;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class MavenResolutionService {

    private final RepositorySystem repositorySystem;
    private final RepositorySystemSession repositorySystemSession;

    public MavenResolutionService(RepositorySystem repositorySystem, RepositorySystemSession repositorySystemSession) {
        this.repositorySystem = repositorySystem;
        this.repositorySystemSession = repositorySystemSession;
    }

    @org.springframework.cache.annotation.Cacheable(value = "mavenDependencies", key = "#groupId + ':' + #artifactId + ':' + #version")
    public DependencyNode resolveDependency(String groupId, String artifactId, String version) {
        try {
            Artifact artifact = new DefaultArtifact(groupId, artifactId, "jar", version);
            Dependency dependency = new Dependency(artifact, "compile");

            RemoteRepository central = new RemoteRepository.Builder("central", "default",
                    "https://repo.maven.apache.org/maven2/").build();

            CollectRequest collectRequest = new CollectRequest();
            collectRequest.setRoot(dependency);
            collectRequest.setRepositories(Collections.singletonList(central));

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
        try {
            org.apache.maven.model.io.xpp3.MavenXpp3Reader reader = new org.apache.maven.model.io.xpp3.MavenXpp3Reader();
            org.apache.maven.model.Model model = reader.read(new java.io.StringReader(pomContent));

            String groupId = model.getGroupId() != null ? model.getGroupId()
                    : (model.getParent() != null ? model.getParent().getGroupId() : "unknown");
            String artifactId = model.getArtifactId();
            String version = model.getVersion() != null ? model.getVersion()
                    : (model.getParent() != null ? model.getParent().getVersion() : "0.0.1-SNAPSHOT");

            // Basic Property Interpolation
            java.util.Map<String, String> properties = new java.util.HashMap<>();
            if (model.getProperties() != null) {
                for (String key : model.getProperties().stringPropertyNames()) {
                    properties.put(key, model.getProperties().getProperty(key));
                }
            }
            // Add implicit properties
            properties.put("project.groupId", groupId);
            properties.put("project.artifactId", artifactId);
            properties.put("project.version", version);
            // Add java.version if missing (common in Spring Boot)
            if (!properties.containsKey("java.version")) {
                properties.put("java.version", "17");
            }

            List<Dependency> dependencies = new ArrayList<>();
            for (org.apache.maven.model.Dependency d : model.getDependencies()) {
                String dGroupId = interpolate(d.getGroupId(), properties);
                String dArtifactId = interpolate(d.getArtifactId(), properties);
                String dVersion = interpolate(d.getVersion(), properties);
                String dScope = d.getScope() != null ? d.getScope() : "compile";

                if (dVersion == null || dVersion.isEmpty()) {
                    // Heuristic for Spring Boot managed dependencies
                    if (model.getParent() != null && dGroupId.startsWith("org.springframework.boot")) {
                        dVersion = model.getParent().getVersion();
                    } else {
                        // Fallback to LATEST for other managed dependencies
                        dVersion = "LATEST";
                    }
                }

                Artifact artifact = new DefaultArtifact(dGroupId, dArtifactId, "jar", dVersion);
                dependencies.add(new Dependency(artifact, dScope));
            }

            RemoteRepository central = new RemoteRepository.Builder("central", "default",
                    "https://repo.maven.apache.org/maven2/").build();

            CollectRequest collectRequest = new CollectRequest();
            collectRequest.setDependencies(dependencies);
            collectRequest.setRepositories(Collections.singletonList(central));

            CollectResult collectResult = repositorySystem.collectDependencies(repositorySystemSession, collectRequest);

            // The root of CollectResult when using setDependencies is a synthetic root with
            // null artifact.
            // We iterate over its children to convert them.
            List<DependencyNode> children = new ArrayList<>();
            for (org.eclipse.aether.graph.DependencyNode child : collectResult.getRoot().getChildren()) {
                children.add(convertToDto(child));
            }

            return new DependencyNode(
                    groupId,
                    artifactId,
                    version,
                    "compile",
                    "pom",
                    children,
                    "RESOLVED",
                    null);
        } catch (Exception e) {
            e.printStackTrace();
            return new DependencyNode("unknown", "unknown", "0.0.0", "compile", "pom", Collections.emptyList(), "ERROR",
                    e.getMessage());
        }
    }

    private String interpolate(String value, java.util.Map<String, String> properties) {
        if (value == null)
            return null;
        if (!value.contains("${"))
            return value;

        for (java.util.Map.Entry<String, String> entry : properties.entrySet()) {
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

        // Check for resolution errors (Aether stores exceptions in the data map)
        // For simplicity in this demo, we'll check a known potential key or rely on the
        // caller exception.

        org.eclipse.aether.graph.DependencyNode winner = (org.eclipse.aether.graph.DependencyNode) aetherNode.getData()
                .get(org.eclipse.aether.util.graph.transformer.ConflictResolver.NODE_DATA_WINNER);

        if (winner != null) {
            resolutionStatus = "CONFLICT";
            conflictMessage = "Conflict with version " + winner.getArtifact().getVersion();
        } else {
            // Basic scope-based status
            String scope = aetherNode.getDependency() != null ? aetherNode.getDependency().getScope() : "compile";
            if ("test".equals(scope) || "provided".equals(scope)) {
                // Technically resolved, but often "omitted" from runtime classpath
                // For now, let's keep them as RESOLVED but maybe add a note?
                // Let's mark optional dependencies
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
