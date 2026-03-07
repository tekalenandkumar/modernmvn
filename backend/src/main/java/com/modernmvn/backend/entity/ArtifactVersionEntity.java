package com.modernmvn.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Tracks a specific version of an artifact along with its indexing state.
 * The indexing lifecycle: PENDING → INDEXING → COMPLETE (or FAILED).
 */
@Entity
@Table(name = "artifact_versions", uniqueConstraints = @UniqueConstraint(columnNames = { "artifact_id",
        "version" }), indexes = {
                @Index(name = "idx_av_lookup", columnList = "artifact_id, version"),
                @Index(name = "idx_av_status", columnList = "indexing_status"),
                @Index(name = "idx_av_created", columnList = "created_at")
        })
public class ArtifactVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artifact_id", nullable = false)
    private ArtifactEntity artifact;

    @Column(nullable = false, length = 100)
    private String version;

    @Column(length = 50)
    private String packaging;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "indexing_status", nullable = false, length = 20)
    private IndexingJobStatus indexingStatus = IndexingJobStatus.PENDING;

    @Column(name = "last_indexed_at")
    private Instant lastIndexedAt;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "dependency_count")
    private Integer dependencyCount;

    @Column(name = "direct_dependency_count")
    private Integer directDependencyCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // ─── Constructors ────────────────────────────────────────────

    public ArtifactVersionEntity() {
    }

    public ArtifactVersionEntity(ArtifactEntity artifact, String version) {
        this.artifact = artifact;
        this.version = version;
        this.indexingStatus = IndexingJobStatus.PENDING;
        this.createdAt = Instant.now();
    }

    // ─── Getters & Setters ───────────────────────────────────────

    public Long getId() {
        return id;
    }

    public ArtifactEntity getArtifact() {
        return artifact;
    }

    public String getVersion() {
        return version;
    }

    public String getPackaging() {
        return packaging;
    }

    public void setPackaging(String packaging) {
        this.packaging = packaging;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public IndexingJobStatus getIndexingStatus() {
        return indexingStatus;
    }

    public void setIndexingStatus(IndexingJobStatus indexingStatus) {
        this.indexingStatus = indexingStatus;
    }

    public Instant getLastIndexedAt() {
        return lastIndexedAt;
    }

    public void setLastIndexedAt(Instant lastIndexedAt) {
        this.lastIndexedAt = lastIndexedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int getDependencyCount() {
        return dependencyCount != null ? dependencyCount : 0;
    }

    public void setDependencyCount(int dependencyCount) {
        this.dependencyCount = dependencyCount;
    }

    public int getDirectDependencyCount() {
        return directDependencyCount != null ? directDependencyCount : 0;
    }

    public void setDirectDependencyCount(int directDependencyCount) {
        this.directDependencyCount = directDependencyCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
