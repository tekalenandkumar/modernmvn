package com.modernmvn.backend.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for DependencyEdgeEntity.
 */
public class DependencyEdgeId implements Serializable {

    private Long rootVersionId;
    private Long dependencyVersionId;

    public DependencyEdgeId() {
    }

    public DependencyEdgeId(Long rootVersionId, Long dependencyVersionId) {
        this.rootVersionId = rootVersionId;
        this.dependencyVersionId = dependencyVersionId;
    }

    public Long getRootVersionId() {
        return rootVersionId;
    }

    public Long getDependencyVersionId() {
        return dependencyVersionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        DependencyEdgeId that = (DependencyEdgeId) o;
        return Objects.equals(rootVersionId, that.rootVersionId)
                && Objects.equals(dependencyVersionId, that.dependencyVersionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rootVersionId, dependencyVersionId);
    }
}
