package com.modernmvn.backend.worker;

import com.modernmvn.backend.entity.IndexingJobEntity;
import com.modernmvn.backend.repository.IndexingJobRepository;
import com.modernmvn.backend.service.ArtifactIndexingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IndexingJobWorker {

    private static final Logger log = LoggerFactory.getLogger(IndexingJobWorker.class);

    private final IndexingJobRepository jobRepository;
    private final ArtifactIndexingService indexingService;

    public IndexingJobWorker(IndexingJobRepository jobRepository, ArtifactIndexingService indexingService) {
        this.jobRepository = jobRepository;
        this.indexingService = indexingService;
    }

    /**
     * Polls the `indexing_jobs` table every 2 seconds for PENDING jobs.
     * Uses a fixed delay so that the next execution starts only
     * after the previous one completes.
     */
    @Scheduled(fixedDelayString = "${modernmvn.worker.indexing.delay:2000}")
    public void processIndexingJobs() {
        List<IndexingJobEntity> pendingJobs = jobRepository.findTop10ByStatusOrderByCreatedAtAsc("PENDING");

        if (pendingJobs.isEmpty()) {
            return;
        }

        log.debug("Found {} pending indexing jobs", pendingJobs.size());

        for (IndexingJobEntity job : pendingJobs) {
            // Mark as PROCESSING immediately to prevent other instances from picking it up
            job.setStatus("PROCESSING");
            jobRepository.save(job);

            try {
                // This call is transactional internally and handles the Distributed Lock
                indexingService.processJob(job);
            } catch (Exception e) {
                log.error("Unhandled exception processing indexing job ID {}: {}", job.getId(), e.getMessage());
                job.setStatus("FAILED");
                jobRepository.save(job);
            }

            // Wait briefly to avoid hammering external APIs if picking up many jobs
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
