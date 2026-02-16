// ─── Security & Vulnerability Types ─────────────────────────────

export type Severity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'UNKNOWN';

export interface SecurityAdvisory {
    id: string;
    summary: string;
    details: string | null;
    severity: Severity;
    cvssScore: number;
    cvssVector: string | null;
    cweIds: string[];
    aliases: string[];
    published: string | null;
    modified: string | null;
    fixedVersion: string | null;
    referenceUrl: string | null;
}

export interface VulnerabilityReport {
    groupId: string;
    artifactId: string;
    version: string;
    totalVulnerabilities: number;
    criticalCount: number;
    highCount: number;
    mediumCount: number;
    lowCount: number;
    highestSeverity: Severity | null;
    advisories: SecurityAdvisory[];
    disclaimer: string;
}

export type SafetyIndicator = 'SAFE' | 'CAUTION' | 'WARNING' | 'DANGER';
export type StabilityGrade = 'STABLE' | 'RECENT' | 'PRE_RELEASE' | 'OUTDATED' | 'UNKNOWN';

export interface VersionAssessment {
    version: string;
    isRelease: boolean;
    timestamp: number;
    vulnerabilityCount: number;
    highestSeverity: Severity | null;
    stabilityGrade: StabilityGrade;
    stabilityScore: number;
    safetyIndicator: SafetyIndicator;
    safetyLabel: string;
}

export interface VersionIntelligence {
    groupId: string;
    artifactId: string;
    recommendedVersion: VersionAssessment | null;
    versions: VersionAssessment[];
}

export interface SafetyBadge {
    groupId: string;
    artifactId: string;
    version: string;
    indicator: SafetyIndicator;
    label: string;
    vulnerabilityCount: number;
    highestSeverity: Severity | string;
}

// ─── API Functions ──────────────────────────────────────────────

const API_BASE = '/api/security';

/**
 * Fetch full vulnerability report for a specific GAV.
 */
export async function fetchVulnerabilityReport(
    groupId: string, artifactId: string, version: string
): Promise<VulnerabilityReport> {
    const res = await fetch(`${API_BASE}/${groupId}/${artifactId}/${version}`);
    if (!res.ok) throw new Error(`Failed to fetch vulnerabilities: ${res.status}`);
    return res.json();
}

/**
 * Fetch lightweight safety badge for a specific GAV.
 * Use this for inline indicators where you don't need full advisory details.
 */
export async function fetchSafetyBadge(
    groupId: string, artifactId: string, version: string
): Promise<SafetyBadge> {
    const res = await fetch(`${API_BASE}/${groupId}/${artifactId}/${version}/badge`);
    if (!res.ok) throw new Error(`Failed to fetch safety badge: ${res.status}`);
    return res.json();
}

/**
 * Fetch version intelligence (security + stability) for an artifact.
 */
export async function fetchVersionIntelligence(
    groupId: string, artifactId: string, versionCount: number = 10
): Promise<VersionIntelligence> {
    const res = await fetch(`${API_BASE}/${groupId}/${artifactId}/intelligence?versions=${versionCount}`);
    if (!res.ok) throw new Error(`Failed to fetch version intelligence: ${res.status}`);
    return res.json();
}

// ─── Display Helpers ────────────────────────────────────────────

export function severityColor(severity: Severity | null | string): string {
    switch (severity) {
        case 'CRITICAL': return '#ff4757';
        case 'HIGH': return '#ff6b6b';
        case 'MEDIUM': return '#ffa502';
        case 'LOW': return '#7bed9f';
        case 'UNKNOWN': return '#747d8c';
        default: return '#747d8c';
    }
}

export function severityBgColor(severity: Severity | null | string): string {
    switch (severity) {
        case 'CRITICAL': return 'rgba(255, 71, 87, 0.15)';
        case 'HIGH': return 'rgba(255, 107, 107, 0.15)';
        case 'MEDIUM': return 'rgba(255, 165, 2, 0.12)';
        case 'LOW': return 'rgba(123, 237, 159, 0.12)';
        case 'UNKNOWN': return 'rgba(116, 125, 140, 0.12)';
        default: return 'rgba(116, 125, 140, 0.12)';
    }
}

export function safetyColor(indicator: SafetyIndicator): string {
    switch (indicator) {
        case 'SAFE': return '#2ed573';
        case 'CAUTION': return '#ffa502';
        case 'WARNING': return '#ff6348';
        case 'DANGER': return '#ff4757';
    }
}

export function safetyBgColor(indicator: SafetyIndicator): string {
    switch (indicator) {
        case 'SAFE': return 'rgba(46, 213, 115, 0.12)';
        case 'CAUTION': return 'rgba(255, 165, 2, 0.12)';
        case 'WARNING': return 'rgba(255, 99, 72, 0.12)';
        case 'DANGER': return 'rgba(255, 71, 87, 0.15)';
    }
}

export function safetyIcon(indicator: SafetyIndicator): string {
    switch (indicator) {
        case 'SAFE': return '✓';
        case 'CAUTION': return '⚠';
        case 'WARNING': return '⚠';
        case 'DANGER': return '✕';
    }
}

export function stabilityLabel(grade: StabilityGrade): string {
    switch (grade) {
        case 'STABLE': return 'Stable';
        case 'RECENT': return 'Recent';
        case 'PRE_RELEASE': return 'Pre-release';
        case 'OUTDATED': return 'Outdated';
        case 'UNKNOWN': return 'Unknown';
    }
}

export function stabilityColor(grade: StabilityGrade): string {
    switch (grade) {
        case 'STABLE': return '#2ed573';
        case 'RECENT': return '#70a1ff';
        case 'PRE_RELEASE': return '#ffa502';
        case 'OUTDATED': return '#ff6348';
        case 'UNKNOWN': return '#747d8c';
    }
}

/** Format a date string into a relative time. */
export function relativeTime(dateStr: string | null): string {
    if (!dateStr) return 'Unknown';
    const date = new Date(dateStr);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const days = Math.floor(diffMs / (1000 * 60 * 60 * 24));
    if (days < 1) return 'Today';
    if (days < 30) return `${days}d ago`;
    if (days < 365) return `${Math.floor(days / 30)}mo ago`;
    return `${Math.floor(days / 365)}y ago`;
}
