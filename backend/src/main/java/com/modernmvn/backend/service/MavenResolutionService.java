package com.modernmvn.backend.service;

import com.modernmvn.backend.dto.DependencyNodeDto;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MavenResolutionService {

    private final RepositorySystem repositorySystem;
    private final String localRepoPath;
    private final String remoteRepoUrl;

    public MavenResolutionService(
            RepositorySystem repositorySystem,
            @Value("${maven.local-repo}") String localRepoPath,
            @Value("${maven.remote-repo}") String remoteRepoUrl) {
        this.repositorySystem = repositorySystem;
        this.localRepoPath = localRepoPath;
        this.remoteRepoUrl = remoteRepoUrl;
    }

    public DependencyNodeDto resolve(String coordinate) {
        RepositorySystemSession session = newSession(repositorySystem);

        Artifact artifact = new DefaultArtifact(coordinate);
        Dependency dependency = new Dependency(artifact, "compile");

        RemoteRepository central = new RemoteRepository.Builder("central", "default", remoteRepoUrl).build();

        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot(dependency);
        collectRequest.setRepositories(Collections.singletonList(central));

        try {
            DependencyRequest dependencyRequest = new DependencyRequest();
            dependencyRequest.setCollectRequest(collectRequest);

            // Resolve dependencies
            DependencyNode root = repositorySystem.resolveDependencies(session, dependencyRequest).getRoot();

            return convert(root);
        } catch (DependencyResolutionException e) {
            throw new RuntimeException("Failed to resolve dependencies for " + coordinate, e);
        }
    }

    private RepositorySystemSession newSession(RepositorySystem system) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        LocalRepository localRepo = new LocalRepository(localRepoPath);
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

        // Add basic error handlers if needed (default behavior)
        return session;
    }

    private DependencyNodeDto convert(DependencyNode node) {
        Artifact a = node.getArtifact();
        List<DependencyNodeDto> children = node.getChildren().stream()
                .map(this::convert)
                .collect(Collectors.toList());

        return new DependencyNodeDto(
                a.getGroupId(),
                a.getArtifactId(),
                a.getVersion(),
                node.getDependency() != null ? node.getDependency().getScope() : "compile",
                children);
    }
}
