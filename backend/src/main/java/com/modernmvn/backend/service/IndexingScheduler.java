package com.modernmvn.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background scheduler that refreshes stale indexed artifacts.
 * Runs every 6 hours to re-scan vulnerabilities and update summaries.
 */
@Component
@EnableScheduling
public class IndexingScheduler {

    private static final Logger log = LoggerFactory.getLogger(IndexingScheduler.class);

    private final ArtifactIndexingService indexingService;

    public IndexingScheduler(ArtifactIndexingService indexingService) {
        this.indexingService = indexingService;
    }

    /**
     * Refresh artifacts whose last_indexed_at is older than 24 hours.
     * Runs every 6 hours.
     */
    @Scheduled(cron = "0 0 */6 * * *")
    public void refreshStaleSnapshots() {
        log.info("Starting scheduled stale artifact refresh...");
        try {
            indexingService.refreshStaleVersions();
            log.info("Scheduled stale artifact refresh complete.");
        } catch (Exception e) {
            log.error("Scheduled refresh failed: {}", e.getMessage());
        }
    }
}
