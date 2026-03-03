package com.modernmvn.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Represents a unique Maven artifact (groupId + artifactId).
 * Versions are tracked separately in ArtifactVersionEntity.
 */
@Entity
@Table(name = "artifacts", uniqueConstraints = @UniqueConstraint(columnNames = { "group_id",
        "artifact_id" }), indexes = {
                @Index(name = "idx_artifact_lookup", columnList = "group_id, artifact_id")
        })
public class ArtifactEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false, length = 255)
    private String groupId;

    @Column(name = "artifact_id", nullable = false, length = 255)
    private String artifactId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // ─── Constructors ────────────────────────────────────────────

    public ArtifactEntity() {
    }

    public ArtifactEntity(String groupId, String artifactId) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.createdAt = Instant.now();
    }

    // ─── Getters & Setters ───────────────────────────────────────

    public Long getId() {
        return id;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
