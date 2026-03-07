package com.modernmvn.backend.repository;

import com.modernmvn.backend.entity.DependencyEdgeEntity;
import com.modernmvn.backend.entity.DependencyEdgeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DependencyEdgeRepository extends JpaRepository<DependencyEdgeEntity, DependencyEdgeId> {

    List<DependencyEdgeEntity> findByRootVersionId(Long rootVersionId);

    long countByRootVersionId(Long rootVersionId);

    void deleteByRootVersionId(Long rootVersionId);

    /**
     * Counts how many distinct root artifacts declare a dependency on the given
     * version.
     * This is the DB-backed "Used By" count — replaces the deprecated Maven Central
     * Solr d: field.
     */
    @Query("SELECT COUNT(DISTINCT e.rootVersionId) FROM DependencyEdgeEntity e WHERE e.dependencyVersionId = :versionId")
    long countReverseDependencies(@Param("versionId") Long versionId);

    /**
     * Returns the root artifact version IDs that depend on a given version (for
     * listing).
     */
    @Query(value = """
            SELECT DISTINCT e.root_version_id
            FROM dependency_edges e
            WHERE e.dependency_version_id = :versionId
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<Long> findRootVersionIdsByDependencyVersionId(
            @Param("versionId") Long versionId,
            @Param("limit") int limit,
            @Param("offset") int offset);
}
