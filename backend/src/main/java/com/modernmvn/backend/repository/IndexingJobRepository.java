package com.modernmvn.backend.repository;

import com.modernmvn.backend.entity.IndexingJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IndexingJobRepository extends JpaRepository<IndexingJobEntity, Long> {

    Optional<IndexingJobEntity> findByGroupIdAndArtifactIdAndVersion(String groupId, String artifactId, String version);

    List<IndexingJobEntity> findTop10ByStatusOrderByCreatedAtAsc(String status);
}
