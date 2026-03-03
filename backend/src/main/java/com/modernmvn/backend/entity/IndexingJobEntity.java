package com.modernmvn.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "indexing_jobs", uniqueConstraints = {
        @UniqueConstraint(name = "idx_indexing_job_unique", columnNames = { "group_id", "artifact_id", "version" })
}, indexes = {
        @Index(name = "idx_job_status_created", columnList = "status, created_at")
})
public class IndexingJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private String groupId;

    @Column(name = "artifact_id", nullable = false)
    private String artifactId;

    @Column(name = "version", nullable = false)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IndexingJobStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public IndexingJobEntity() {
    }

    public IndexingJobEntity(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.status = IndexingJobStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public IndexingJobStatus getStatus() {
        return status;
    }

    public void setStatus(IndexingJobStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
