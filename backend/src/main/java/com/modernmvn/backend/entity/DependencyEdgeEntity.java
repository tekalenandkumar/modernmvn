package com.modernmvn.backend.entity;

import jakarta.persistence.*;

/**
 * Flattened dependency edge: one row per (root → transitive dependency) pair.
 * Depth 1 = direct dependency, depth > 1 = transitive.
 */
@Entity
@Table(name = "dependency_edges", indexes = {
        @Index(name = "idx_de_root", columnList = "root_version_id"),
        @Index(name = "idx_de_root_depth", columnList = "root_version_id, depth")
})
@IdClass(DependencyEdgeId.class)
public class DependencyEdgeEntity {

    @Id
    @Column(name = "root_version_id")
    private Long rootVersionId;

    @Id
    @Column(name = "dependency_version_id")
    private Long dependencyVersionId;

    @Column(nullable = false)
    private int depth;

    @Column(name = "is_direct", nullable = false)
    private boolean isDirect;

    @Column(length = 20)
    private String scope;

    // ─── Schema Foreign Keys (for ON DELETE CASCADE DDL only) ────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "root_version_id", insertable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_edge_root", foreignKeyDefinition = "FOREIGN KEY (root_version_id) REFERENCES artifact_versions(id) ON DELETE CASCADE"))
    private ArtifactVersionEntity rootVersionRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dependency_version_id", insertable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_edge_dep", foreignKeyDefinition = "FOREIGN KEY (dependency_version_id) REFERENCES artifact_versions(id) ON DELETE CASCADE"))
    private ArtifactVersionEntity dependencyVersionRef;

    // ─── Constructors ────────────────────────────────────────────

    public DependencyEdgeEntity() {
    }

    public DependencyEdgeEntity(Long rootVersionId, Long dependencyVersionId,
            int depth, boolean isDirect, String scope) {
        this.rootVersionId = rootVersionId;
        this.dependencyVersionId = dependencyVersionId;
        this.depth = depth;
        this.isDirect = isDirect;
        this.scope = scope;
    }

    // ─── Getters ─────────────────────────────────────────────────

    public Long getRootVersionId() {
        return rootVersionId;
    }

    public Long getDependencyVersionId() {
        return dependencyVersionId;
    }

    public int getDepth() {
        return depth;
    }

    public boolean isDirect() {
        return isDirect;
    }

    public String getScope() {
        return scope;
    }
}
