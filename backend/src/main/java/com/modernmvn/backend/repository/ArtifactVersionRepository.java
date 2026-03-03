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

    /** Lookup by GAV without locking. */
    @Query("SELECT v FROM ArtifactVersionEntity v WHERE v.artifact.groupId = :g AND v.artifact.artifactId = :a AND v.version = :ver")
    Optional<ArtifactVersionEntity> findByGav(
            @Param("g") String groupId,
            @Param("a") String artifactId,
            @Param("ver") String version);
}
