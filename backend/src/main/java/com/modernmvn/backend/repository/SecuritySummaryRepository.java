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
                        "AND s.lastCalculatedAt >= :cutoff " +
                        "ORDER BY s.lastCalculatedAt DESC")
        List<SecuritySummaryEntity> findHistory(@Param("g") String groupId, @Param("a") String artifactId,
                        @Param("cutoff") java.time.Instant cutoff);

        @Query("SELECT s FROM SecuritySummaryEntity s " +
                        "JOIN ArtifactVersionEntity v ON s.artifactVersionId = v.id " +
                        "WHERE v.artifact.groupId = :g AND v.artifact.artifactId = :a AND v.version = :v")
        Optional<SecuritySummaryEntity> findByGav(@Param("g") String groupId, @Param("a") String artifactId,
                        @Param("v") String version);

        @Query("SELECT " +
                        "COALESCE(SUM(CASE WHEN v.severity = 'CRITICAL' THEN 1 ELSE 0 END), 0) as critical, " +
                        "COALESCE(SUM(CASE WHEN v.severity = 'HIGH' THEN 1 ELSE 0 END), 0) as high, " +
                        "COALESCE(SUM(CASE WHEN v.severity = 'MEDIUM' THEN 1 ELSE 0 END), 0) as medium, " +
                        "COALESCE(SUM(CASE WHEN v.severity = 'LOW' THEN 1 ELSE 0 END), 0) as low " +
                        "FROM ArtifactVulnerabilityEntity av " +
                        "JOIN VulnerabilityEntity v ON av.vulnerabilityId = v.id " +
                        "WHERE av.artifactVersionId = :id")
        SeverityCounts getSeverityCounts(@Param("id") Long artifactVersionId);

        @Query("SELECT COALESCE(MAX(v.cvssScore), -1.0) FROM ArtifactVulnerabilityEntity av " +
                        "JOIN VulnerabilityEntity v ON av.vulnerabilityId = v.id " +
                        "WHERE av.artifactVersionId = :id")
        Double getMaxCvss(@Param("id") Long artifactVersionId);

        interface SeverityCounts {
                int getCritical();

                int getHigh();

                int getMedium();

                int getLow();
        }
}
