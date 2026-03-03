package com.modernmvn.backend.repository;

import com.modernmvn.backend.entity.IndexingJobEntity;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IndexingJobRepository extends JpaRepository<IndexingJobEntity, Long> {

    Optional<IndexingJobEntity> findByGroupIdAndArtifactIdAndVersion(String groupId, String artifactId, String version);

    List<IndexingJobEntity> findTop10ByStatusOrderByCreatedAtAsc(String status);

    /**
     * Fetch pending jobs with pessimistic write lock and SKIP LOCKED.
     * This ensures that multiple cluster nodes don't pick up the same job.
     * Note: "jakarta.persistence.lock.timeout" = -2 is the way to specify SKIP
     * LOCKED in Hibernate.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({ @QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2") })
    @Query("SELECT j FROM IndexingJobEntity j WHERE j.status = :status ORDER BY j.createdAt ASC")
    List<IndexingJobEntity> findPendingJobsWithLock(@Param("status") String status, Pageable pageable);
}
