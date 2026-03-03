package com.modernmvn.backend.config;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class MavenConfig {

    @Value("${maven.local-repo:/var/modernmvn/local-repo}")
    private String localRepoPath;

    @Bean
    public RepositorySystem repositorySystem() {
        return new org.eclipse.aether.supplier.RepositorySystemSupplier().get();
    }

    /**
     * Helper to create a fresh, configured session.
     * Shared sessions are not thread-safe and can leak memory.
     */
    public RepositorySystemSession createSession(RepositorySystem repositorySystem) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        LocalRepository localRepo = new LocalRepository(localRepoPath);
        session.setLocalRepositoryManager(repositorySystem.newLocalRepositoryManager(session, localRepo));

        // Disable verbose mode to save memory/cpu unless needed for debugging
        session.setConfigProperty(org.eclipse.aether.util.graph.manager.DependencyManagerUtils.CONFIG_PROP_VERBOSE,
                false);
        session.setConfigProperty(org.eclipse.aether.util.graph.transformer.ConflictResolver.CONFIG_PROP_VERBOSE,
                false);

        // Security: Set timeouts for Aether transport
        session.setConfigProperty("aether.connector.connectTimeout", 5000); // 5s
        session.setConfigProperty("aether.connector.requestTimeout", 15000); // 15s

        // Performance: Max threads for concurrent downloads
        session.setConfigProperty("aether.priority.cachedir", "/tmp/aether-cache");

        return session;
    }

    public String getLocalRepoPath() {
        return localRepoPath;
    }
}
