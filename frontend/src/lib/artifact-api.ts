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

/**
 * Fetch all artifacts under a given groupId (paginated, sorted alphabetically).
 */
export async function fetchGroupArtifacts(
    groupId: string,
    page: number = 0,
    size: number = 20
): Promise<{ query: string; totalResults: number; page: number; pageSize: number; items: GroupArtifactItem[] }> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    const res = await fetch(
        `${BACKEND_BASE}/api/maven/group/${encodeURIComponent(groupId)}?${params}`,
        { next: { revalidate: 21600 } } // ISR: revalidate every 6 hours
    );
    if (!res.ok) {
        const err = await res.json().catch(() => null);
        throw new Error(err?.error || `Group not found: ${groupId}`);
    }
    return res.json();
}

export interface GroupArtifactItem {
    groupId: string;
    artifactId: string;
    latestVersion: string;
    packaging: string;
    description: string | null;
    timestamp: number;
    versionCount: number;
}

// ─── Reverse Dependencies ("Used By") ───────────────────────────

export interface ReverseDependencyResult {
    query: string;
    totalResults: number;
    page: number;
    pageSize: number;
    items: ReverseDependencyItem[];
}

export interface ReverseDependencyItem {
    groupId: string;
    artifactId: string;
    latestVersion: string;
    packaging: string;
    description: string | null;
    timestamp: number;
    versionCount: number;
}

/**
 * Fetch artifacts that depend on the given artifact ("Used By").
 * Uses relative /api/maven/ path so it works from both server and client via Next.js rewrite proxy.
 */
export async function fetchReverseDependencies(
    groupId: string,
    artifactId: string,
    page: number = 0,
    size: number = 10
): Promise<ReverseDependencyResult> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    // Use relative path — works on both client (via Next.js rewrite) and server (via BACKEND_BASE)
    const base = typeof window !== 'undefined' ? '' : (process.env.BACKEND_URL || 'http://localhost:8080');
    const path = `${base}/api/maven/artifact/${encodeURIComponent(groupId)}/${encodeURIComponent(artifactId)}/usedby?${params}`;
    const res = await fetch(path, { next: { revalidate: 3600 } });
    if (!res.ok) {
        throw new Error(`Failed to fetch reverse dependencies: ${res.statusText}`);
    }
    return res.json();
}

/**
 * Fetch the count of artifacts that depend on the given artifact.
 * Uses relative /api/maven/ path so it works from the browser.
 */
export async function fetchReverseDependencyCount(
    groupId: string,
    artifactId: string
): Promise<number> {
    try {
        const base = typeof window !== 'undefined' ? '' : (process.env.BACKEND_URL || 'http://localhost:8080');
        const res = await fetch(
            `${base}/api/maven/artifact/${encodeURIComponent(groupId)}/${encodeURIComponent(artifactId)}/usedby/count`,
            { next: { revalidate: 3600 } }
        );
        if (!res.ok) return 0;
        const data = await res.json();
        return data.count || 0;
    } catch {
        return 0;
    }
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
 * Format large numbers for display (e.g., 16800 → "16.8k").
 */
export function formatCount(count: number): string {
    if (count >= 1000000) return `${(count / 1000000).toFixed(1)}M`;
    if (count >= 1000) return `${(count / 1000).toFixed(1)}k`;
    return String(count);
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
