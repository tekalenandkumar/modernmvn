package com.modernmvn.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Single-row table that persists the Maven Central crawler's progress
 * so it can resume across restarts.
 */
@Entity
@Table(name = "crawler_state")
public class CrawlerStateEntity {

    @Id
    private Long id = 1L; // always 1 — singleton row

    @Column(name = "cursor_offset")
    private Long cursorOffset = 0L;

    /** Current Solr cursorMark — used for deep pagination in modern Solr. */
    @Column(name = "cursor_mark")
    private String cursorMark = "*";

    /** Total number of artifacts seen by the crawler. */
    @Column(name = "total_discovered")
    private Long totalDiscovered = 0L;

    /**
     * Live total artifact count from Maven Central Solr numFound — updated each
     * batch.
     */
    @Column(name = "total_artifacts")
    private Long totalArtifacts = 0L;

    /** Whether the crawler is actively running. */
    @Column(name = "is_running", nullable = false)
    private boolean running = false;

    @Column(name = "last_run")
    private Instant lastRun;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public CrawlerStateEntity() {
    }

    public Long getId() {
        return id;
    }

    public Long getCursorOffset() {
        return cursorOffset;
    }

    public void setCursorOffset(Long cursorOffset) {
        this.cursorOffset = cursorOffset;
    }

    public String getCursorMark() {
        return cursorMark;
    }

    public void setCursorMark(String cursorMark) {
        this.cursorMark = cursorMark;
    }

    public Long getTotalDiscovered() {
        return totalDiscovered;
    }

    public void setTotalDiscovered(Long totalDiscovered) {
        this.totalDiscovered = totalDiscovered;
    }

    public Long getTotalArtifacts() {
        return totalArtifacts;
    }

    public void setTotalArtifacts(Long totalArtifacts) {
        this.totalArtifacts = totalArtifacts;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public Instant getLastRun() {
        return lastRun;
    }

    public void setLastRun(Instant lastRun) {
        this.lastRun = lastRun;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
