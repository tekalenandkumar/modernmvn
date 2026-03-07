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

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    public IndexingJobEntity() {
    }

    public IndexingJobEntity(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.status = IndexingJobStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.retryCount = 0;
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

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
        this.updatedAt = Instant.now();
    }

    public void incrementRetryCount() {
        this.retryCount++;
        this.updatedAt = Instant.now();
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError != null && lastError.length() > 1000 ? lastError.substring(0, 997) + "..."
                : lastError;
        this.updatedAt = Instant.now();
    }
}
