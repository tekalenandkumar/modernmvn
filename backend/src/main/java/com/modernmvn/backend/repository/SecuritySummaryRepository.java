package com.modernmvn.backend.repository;

import com.modernmvn.backend.entity.SecuritySummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SecuritySummaryRepository extends JpaRepository<SecuritySummaryEntity, Long> {

    Optional<SecuritySummaryEntity> findByArtifactVersionId(Long artifactVersionId);
}
