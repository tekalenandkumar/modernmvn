package com.modernmvn.backend.worker;

import com.modernmvn.backend.entity.IndexingJobEntity;
import com.modernmvn.backend.entity.IndexingJobStatus;
import com.modernmvn.backend.repository.IndexingJobRepository;
import com.modernmvn.backend.service.ArtifactIndexingService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.util.List;

@Component
public class IndexingJobWorker {

    private static final Logger log = LoggerFactory.getLogger(IndexingJobWorker.class);

    private final IndexingJobRepository jobRepository;
    private final ArtifactIndexingService indexingService;
    private final MeterRegistry meterRegistry;

    @Value("${modernmvn.worker.stuck-timeout-minutes:30}")
    private int stuckTimeoutMinutes;

    @Value("${modernmvn.worker.max-retries:3}")
    private int maxRetries;

    @Value("${modernmvn.worker.sync-orphans.threshold-minutes:30}")
    private int syncOrphansThresholdMinutes;

    public IndexingJobWorker(IndexingJobRepository jobRepository,
            ArtifactIndexingService indexingService,
            MeterRegistry meterRegistry) {
        this.jobRepository = jobRepository;
        this.indexingService = indexingService;
        this.meterRegistry = meterRegistry;

        // Register gauges for queue depth visibility
        meterRegistry.gauge("indexing_jobs.pending", jobRepository,
                repo -> repo.countByStatus(IndexingJobStatus.PENDING));
        meterRegistry.gauge("indexing_jobs.processing", jobRepository,
                repo -> repo.countByStatus(IndexingJobStatus.PROCESSING));
    }

    /**
     * Polls the `indexing_jobs` table every 2 seconds for PENDING jobs.
     * Uses FOR UPDATE SKIP LOCKED via the repository to ensure cluster-safe
     * processing.
     */
    @Scheduled(fixedDelayString = "${modernmvn.worker.indexing.delay:2000}")
    public void processIndexingJobs() {
        // Fetch and claim up to 5 jobs atomically in a discrete transaction
        List<IndexingJobEntity> jobs = indexingService.fetchAndClaimJobs(5);

        if (jobs.isEmpty()) {
            return;
        }

        for (IndexingJobEntity job : jobs) {
            try {
                // Already claimed in fetchAndClaimJobs
                indexingService.processClaimedJob(job);
            } catch (Exception e) {
                log.error("Job processor failed for {}: {}", job.getId(), e.getMessage());
            }

            // Small delay between jobs to stay within API rate limits
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Periodically checks for jobs stuck in PROCESSING state for too long.
     * Resets them to PENDING or marks as FAILED if max retries exceeded.
     */
    @Scheduled(fixedDelayString = "${modernmvn.worker.cleanup.delay:300000}") // Default 5 mins
    public void cleanupStuckJobs() {
        log.info("Running stuck job cleanup (timeout: {}m)", stuckTimeoutMinutes);
        List<IndexingJobEntity> stuckJobs = indexingService.findStuckJobs(stuckTimeoutMinutes);

        if (stuckJobs.isEmpty()) {
            return;
        }

        log.info("Found {} stuck jobs to recover", stuckJobs.size());
        for (IndexingJobEntity job : stuckJobs) {
            try {
                indexingService.resetStuckJob(job.getId(), maxRetries);
            } catch (Exception e) {
                log.error("Failed to recover stuck job {}: {}", job.getId(), e.getMessage());
            }
        }
    }

    /**
     * Periodically syncs orphaned artifact_versions states that lack an active
     * entry in the indexing_jobs table.
     */
    @Scheduled(fixedDelayString = "${modernmvn.worker.sync-orphans.delay:3600000}") // Default 1 hour
    public void syncOrphans() {
        log.info("Running orphaned version sync (threshold: {}m)", syncOrphansThresholdMinutes);
        try {
            indexingService.syncOrphanedVersions(syncOrphansThresholdMinutes);
        } catch (Exception e) {
            log.error("Failed to sync orphaned versions: {}", e.getMessage());
        }
    }
}
