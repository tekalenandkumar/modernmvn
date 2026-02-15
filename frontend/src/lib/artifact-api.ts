// ─── Artifact API Types ─────────────────────────────────────────

export interface LicenseInfo {
    name: string;
    url: string;
}

export interface ArtifactVersion {
    version: string;
    packaging: string;
    timestamp: number;
    repository: string;
    isRelease: boolean;
    isRecommended: boolean;
}

export interface ArtifactInfo {
    groupId: string;
    artifactId: string;
    latestVersion: string;
    latestReleaseVersion: string;
    packaging: string;
    versionCount: number;
    versions: ArtifactVersion[];
    description: string | null;
    url: string | null;
    licenses: LicenseInfo[];
    lastUpdated: number;
}

export interface ArtifactDetail {
    groupId: string;
    artifactId: string;
    version: string;
    packaging: string;
    description: string | null;
    url: string | null;
    name: string | null;
    licenses: LicenseInfo[];
    dependencySnippets: Record<string, string>;
    dependencyCount: number;
    timestamp: number;
}

// ─── API Functions ──────────────────────────────────────────────

const BACKEND_BASE = process.env.BACKEND_URL || 'http://localhost:8080';

/**
 * Fetch artifact info (all versions, recommended, metadata).
 */
export async function fetchArtifactInfo(groupId: string, artifactId: string): Promise<ArtifactInfo> {
    const res = await fetch(`${BACKEND_BASE}/api/maven/artifact/${encodeURIComponent(groupId)}/${encodeURIComponent(artifactId)}`, {
        next: { revalidate: 3600 }, // ISR: revalidate every 1 hour
    });
    if (!res.ok) {
        const err = await res.json().catch(() => null);
        throw new Error(err?.error || `Failed to fetch artifact: ${res.statusText}`);
    }
    return res.json();
}

/**
 * Fetch detailed info for a specific version.
 */
export async function fetchArtifactDetail(groupId: string, artifactId: string, version: string): Promise<ArtifactDetail> {
    const res = await fetch(`${BACKEND_BASE}/api/maven/artifact/${encodeURIComponent(groupId)}/${encodeURIComponent(artifactId)}/${encodeURIComponent(version)}`, {
        next: { revalidate: 86400 }, // ISR: revalidate every 24 hours (versions are immutable)
    });
    if (!res.ok) {
        const err = await res.json().catch(() => null);
        throw new Error(err?.error || `Failed to fetch artifact details: ${res.statusText}`);
    }
    return res.json();
}

// ─── Utility ────────────────────────────────────────────────────

/**
 * Format a timestamp (millis since epoch) into a human-readable date.
 */
export function formatDate(timestamp: number): string {
    if (!timestamp) return 'Unknown';
    return new Date(timestamp).toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
    });
}

/**
 * Format relative time (e.g., "3 months ago").
 */
export function timeAgo(timestamp: number): string {
    if (!timestamp) return '';
    const seconds = Math.floor((Date.now() - timestamp) / 1000);
    const intervals: [number, string][] = [
        [31536000, 'year'],
        [2592000, 'month'],
        [86400, 'day'],
        [3600, 'hour'],
        [60, 'minute'],
    ];
    for (const [secs, label] of intervals) {
        const count = Math.floor(seconds / secs);
        if (count >= 1) return `${count} ${label}${count > 1 ? 's' : ''} ago`;
    }
    return 'just now';
}

/**
 * Snippet display labels for build tools.
 */
export const SNIPPET_LABELS: Record<string, string> = {
    maven: 'Maven',
    gradle: 'Gradle (Groovy)',
    gradle_kotlin: 'Gradle (Kotlin)',
    sbt: 'SBT',
    ivy: 'Ivy',
    leiningen: 'Leiningen',
    buildr: 'Apache Buildr',
};
