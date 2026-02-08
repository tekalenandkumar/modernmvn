package com.modernmvn.backend.config;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MavenConfig {

    @Bean
    public RepositorySystem repositorySystem() {
        return new RepositorySystemSupplier().get();
    }
}
