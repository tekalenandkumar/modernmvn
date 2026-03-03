package com.modernmvn.backend.repository;

import com.modernmvn.backend.entity.ArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ArtifactRepository extends JpaRepository<ArtifactEntity, Long> {

    Optional<ArtifactEntity> findByGroupIdAndArtifactId(String groupId, String artifactId);
}
