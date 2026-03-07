package com.modernmvn.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Logs key configuration and environment info at startup to help diagnose
 * Railway.com deployment failures.
 */
@Component
public class StartupDiagnostics {

    private static final Logger log = LoggerFactory.getLogger(StartupDiagnostics.class);

    private final Environment env;

    public StartupDiagnostics(Environment env) {
        this.env = env;
    }

    @PostConstruct
    public void logConfig() {
        log.info("=== ModernMVN Startup Diagnostics ===");
        log.info("[ENV] PORT           = {}", System.getenv("PORT"));
        log.info("[ENV] PGHOST         = {}", maskHost(System.getenv("PGHOST")));
        log.info("[ENV] PGPORT         = {}", System.getenv("PGPORT"));
        log.info("[ENV] PGDATABASE     = {}", System.getenv("PGDATABASE"));
        log.info("[ENV] PGUSER         = {}", System.getenv("PGUSER"));
        log.info("[ENV] PGPASSWORD     = {}", mask(System.getenv("PGPASSWORD")));
        log.info("[ENV] DATABASE_URL   = {}", maskUrl(System.getenv("DATABASE_URL")));
        log.info("[ENV] REDIS_URL      = {}", maskUrl(System.getenv("REDIS_URL")));
        log.info("[ENV] SPRING_PROFILES_ACTIVE = {}", System.getenv("SPRING_PROFILES_ACTIVE"));

        log.info("[CFG] server.port         = {}", env.getProperty("server.port"));
        log.info("[CFG] datasource.url      = {}", maskUrl(env.getProperty("spring.datasource.url")));
        log.info("[CFG] datasource.username = {}", env.getProperty("spring.datasource.username"));
        log.info("[CFG] redis.url           = {}", maskUrl(env.getProperty("spring.data.redis.url")));
        log.info("[CFG] active profiles     = {}", String.join(", ", env.getActiveProfiles()));
        log.info("=== End Startup Diagnostics ===");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("✅ Application is READY and accepting traffic on port {}", env.getProperty("server.port", "8080"));
    }

    // ─── Masking helpers ─────────────────────────────────────────────

    private String mask(String value) {
        if (value == null)
            return "<NOT SET>";
        if (value.isEmpty())
            return "<EMPTY>";
        return "****" + (value.length() > 4 ? value.substring(value.length() - 2) : "");
    }

    private String maskHost(String value) {
        if (value == null)
            return "<NOT SET>";
        return value; // host names are not sensitive
    }

    private String maskUrl(String value) {
        if (value == null)
            return "<NOT SET>";
        // Mask password portion: protocol://user:PASSWORD@host/db →
        // protocol://user:****@host/db
        return value.replaceAll("(://[^:]+:)[^@]+(@)", "$1****$2");
    }
}
