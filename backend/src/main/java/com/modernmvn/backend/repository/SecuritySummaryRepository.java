package com.modernmvn.backend.repository;

import com.modernmvn.backend.entity.SecuritySummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SecuritySummaryRepository extends JpaRepository<SecuritySummaryEntity, Long> {

    Optional<SecuritySummaryEntity> findByArtifactVersionId(Long artifactVersionId);

    @Query("SELECT s FROM SecuritySummaryEntity s " +
            "JOIN ArtifactVersionEntity v ON s.artifactVersionId = v.id " +
            "WHERE v.artifact.groupId = :g AND v.artifact.artifactId = :a " +
            "ORDER BY v.createdAt DESC")
    List<SecuritySummaryEntity> findHistory(@Param("g") String groupId, @Param("a") String artifactId);
}
