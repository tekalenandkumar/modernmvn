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

            return convertToDto(collectResult.getRoot());
        } catch (Exception e) {
            e.printStackTrace();
            return new DependencyNode(groupId, artifactId, version, "compile", "jar", Collections.emptyList(), "ERROR",
                    e.getMessage());
        }
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
