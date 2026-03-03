package com.modernmvn.backend.worker;

import com.modernmvn.backend.entity.IndexingJobEntity;
import com.modernmvn.backend.repository.IndexingJobRepository;
import com.modernmvn.backend.service.ArtifactIndexingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
     * Uses FOR UPDATE SKIP LOCKED via the repository to ensure cluster-safe
     * processing.
     */
    @Scheduled(fixedDelayString = "${modernmvn.worker.indexing.delay:2000}")
    @Transactional
    public void processIndexingJobs() {
        // Fetch up to 5 jobs using skip-locked for cluster safety
        List<IndexingJobEntity> jobs = jobRepository.findPendingJobsWithLock("PENDING", PageRequest.of(0, 5));

        if (jobs.isEmpty()) {
            return;
        }

        for (IndexingJobEntity job : jobs) {
            log.info("Processing job {}:{}:{} (ID: {})", job.getGroupId(), job.getArtifactId(), job.getVersion(),
                    job.getId());
            try {
                // Mark as PROCESSING
                job.setStatus("PROCESSING");
                jobRepository.saveAndFlush(job);

                // This handles the actual work
                indexingService.processJob(job);
            } catch (Exception e) {
                log.error("Failed to process job {}: {}", job.getId(), e.getMessage());
                job.setStatus("FAILED");
                jobRepository.save(job);
            }

            // Small delay between jobs to stay within API rate limits of OSV/Maven
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
