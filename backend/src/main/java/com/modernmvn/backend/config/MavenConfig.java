package com.modernmvn.backend.config;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MavenConfig {

    @Bean
    public RepositorySystem repositorySystem() {
        return new RepositorySystemSupplier().get();
    }

    @Bean
    public RepositorySystemSession repositorySystemSession(RepositorySystem repositorySystem) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        LocalRepository localRepo = new LocalRepository("target/local-repo");
        session.setLocalRepositoryManager(repositorySystem.newLocalRepositoryManager(session, localRepo));

        // Enable verbose mode for conflict resolution debugging
        session.setConfigProperty(org.eclipse.aether.util.graph.manager.DependencyManagerUtils.CONFIG_PROP_VERBOSE,
                true);
        session.setConfigProperty(org.eclipse.aether.util.graph.transformer.ConflictResolver.CONFIG_PROP_VERBOSE, true);

        return session;
    }
}
