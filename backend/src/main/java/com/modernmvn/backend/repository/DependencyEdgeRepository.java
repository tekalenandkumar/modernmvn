package com.modernmvn.backend.repository;

import com.modernmvn.backend.entity.DependencyEdgeEntity;
import com.modernmvn.backend.entity.DependencyEdgeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DependencyEdgeRepository extends JpaRepository<DependencyEdgeEntity, DependencyEdgeId> {

    List<DependencyEdgeEntity> findByRootVersionId(Long rootVersionId);

    long countByRootVersionId(Long rootVersionId);

    void deleteByRootVersionId(Long rootVersionId);
}
