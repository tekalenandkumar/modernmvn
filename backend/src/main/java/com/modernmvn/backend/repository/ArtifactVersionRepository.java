package com.modernmvn.backend.repository;

import com.modernmvn.backend.entity.ArtifactEntity;
import com.modernmvn.backend.entity.ArtifactVersionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ArtifactVersionRepository extends JpaRepository<ArtifactVersionEntity, Long> {

        Optional<ArtifactVersionEntity> findByArtifactAndVersion(ArtifactEntity artifact, String version);

        /** Pessimistic lock for concurrency-safe indexing. */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT v FROM ArtifactVersionEntity v WHERE v.artifact.groupId = :g AND v.artifact.artifactId = :a AND v.version = :ver")
        Optional<ArtifactVersionEntity> findForUpdate(
                        @Param("g") String groupId,
                        @Param("a") String artifactId,
                        @Param("ver") String version);

        /** Find stale completed versions for background refresh. */
        @Query("SELECT v FROM ArtifactVersionEntity v WHERE v.indexingStatus = 'COMPLETE' AND v.lastIndexedAt < :before")
        List<ArtifactVersionEntity> findStaleVersions(@Param("before") Instant before);

        /** Find versions by indexing status (for worker polling). */
        List<ArtifactVersionEntity> findByIndexingStatus(String status);

        @Query("SELECT v FROM ArtifactVersionEntity v WHERE v.artifact.groupId = :g AND v.artifact.artifactId = :a AND v.version = :ver")
        Optional<ArtifactVersionEntity> findByGav(
                        @Param("g") String groupId,
                        @Param("a") String artifactId,
                        @Param("ver") String version);

        /**
         * Finds versions that are in PROCESSING or PENDING status but do not have a
         * corresponding
         * active job in the indexing_jobs table. These are "orphaned" states.
         * We only pick versions older than the threshold to avoid race conditions with
         * job creation.
         */
        @Query("SELECT v FROM ArtifactVersionEntity v WHERE v.indexingStatus IN (com.modernmvn.backend.entity.IndexingJobStatus.PROCESSING, com.modernmvn.backend.entity.IndexingJobStatus.PENDING) "
                        +
                        "AND v.createdAt < :threshold " +
                        "AND NOT EXISTS (SELECT 1 FROM IndexingJobEntity j WHERE j.groupId = v.artifact.groupId AND j.artifactId = v.artifact.artifactId AND j.version = v.version)")
        List<ArtifactVersionEntity> findOrphanedVersions(@Param("threshold") Instant threshold);
}
