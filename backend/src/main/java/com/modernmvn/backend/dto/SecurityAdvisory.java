package com.modernmvn.backend.dto;

import java.util.List;

/**
 * Represents a single security advisory (CVE) affecting an artifact version.
 */
public record SecurityAdvisory(
        String id, // e.g. "GHSA-xxxx" or "CVE-2024-xxxx"
        String summary, // human-readable title
        String details, // longer description (may be null)
        Severity severity, // CRITICAL, HIGH, MEDIUM, LOW, UNKNOWN
        double cvssScore, // 0.0 – 10.0, -1 = unknown
        String cvssVector, // CVSS vector string (may be null)
        List<String> cweIds, // e.g. ["CWE-79","CWE-502"]
        List<String> aliases, // all known IDs (CVE, GHSA, etc.)
        String published, // ISO-8601 date string
        String modified, // ISO-8601 date string
        String fixedVersion, // first patched version, null if none
        String referenceUrl // link to advisory page
) {
    public enum Severity {
        CRITICAL, HIGH, MEDIUM, LOW, UNKNOWN
    }
}
