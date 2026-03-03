package com.modernmvn.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Precomputed security summary for an artifact version.
 * Aggregated from dependency_edges + artifact_vulnerabilities during indexing.
 * This is what the controller reads — no live OSV calls needed.
 */
@Entity
@Table(name = "artifact_security_summaries", indexes = {
        @Index(name = "idx_summary_date", columnList = "last_calculated_at")
})
public class SecuritySummaryEntity {

    @Id
    @Column(name = "artifact_version_id")
    private Long artifactVersionId;

    @Column(name = "total_vulns")
    private int totalVulns;

    @Column(name = "direct_vulns")
    private int directVulns;

    @Column(name = "transitive_vulns")
    private int transitiveVulns;

    @Column(name = "critical_count")
    private int criticalCount;

    @Column(name = "high_count")
    private int highCount;

    @Column(name = "medium_count")
    private int mediumCount;

    @Column(name = "low_count")
    private int lowCount;

    @Column(name = "max_cvss")
    private double maxCvss = -1.0;

    @Column(name = "risk_score")
    private int riskScore;

    @Column(name = "last_calculated_at")
    private Instant lastCalculatedAt;

    // ─── Constructors ────────────────────────────────────────────

    public SecuritySummaryEntity() {
    }

    public SecuritySummaryEntity(Long artifactVersionId) {
        this.artifactVersionId = artifactVersionId;
        this.lastCalculatedAt = Instant.now();
    }

    // ─── Getters & Setters ───────────────────────────────────────

    public Long getArtifactVersionId() {
        return artifactVersionId;
    }

    public int getTotalVulns() {
        return totalVulns;
    }

    public void setTotalVulns(int totalVulns) {
        this.totalVulns = totalVulns;
    }

    public int getDirectVulns() {
        return directVulns;
    }

    public void setDirectVulns(int directVulns) {
        this.directVulns = directVulns;
    }

    public int getTransitiveVulns() {
        return transitiveVulns;
    }

    public void setTransitiveVulns(int transitiveVulns) {
        this.transitiveVulns = transitiveVulns;
    }

    public int getCriticalCount() {
        return criticalCount;
    }

    public void setCriticalCount(int criticalCount) {
        this.criticalCount = criticalCount;
    }

    public int getHighCount() {
        return highCount;
    }

    public void setHighCount(int highCount) {
        this.highCount = highCount;
    }

    public int getMediumCount() {
        return mediumCount;
    }

    public void setMediumCount(int mediumCount) {
        this.mediumCount = mediumCount;
    }

    public int getLowCount() {
        return lowCount;
    }

    public void setLowCount(int lowCount) {
        this.lowCount = lowCount;
    }

    public double getMaxCvss() {
        return maxCvss;
    }

    public void setMaxCvss(double maxCvss) {
        this.maxCvss = maxCvss;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(int riskScore) {
        this.riskScore = riskScore;
    }

    public Instant getLastCalculatedAt() {
        return lastCalculatedAt;
    }

    public void setLastCalculatedAt(Instant lastCalculatedAt) {
        this.lastCalculatedAt = lastCalculatedAt;
    }

    /**
     * Compute risk score from severity counts:
     * critical*10 + high*7 + medium*4 + low*1
     */
    public void computeRiskScore() {
        this.riskScore = (criticalCount * 10) + (highCount * 7) + (mediumCount * 4) + (lowCount);
    }
}
