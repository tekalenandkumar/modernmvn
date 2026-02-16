// ─── Search API Types ───────────────────────────────────────────

export interface SearchResultItem {
    groupId: string;
    artifactId: string;
    latestVersion: string;
    packaging: string;
    description: string | null;
    timestamp: number;
    versionCount: number;
}

export interface SearchResult {
    query: string;
    totalResults: number;
    page: number;
    pageSize: number;
    items: SearchResultItem[];
}

// ─── API Functions ──────────────────────────────────────────────

/**
 * Search artifacts. Uses the client-side proxy (/api/maven/search).
 */
export async function searchArtifacts(
    query: string,
    page: number = 0,
    size: number = 20
): Promise<SearchResult> {
    const params = new URLSearchParams({ q: query, page: String(page), size: String(size) });
    const res = await fetch(`/api/maven/search?${params}`);
    if (!res.ok) {
        const err = await res.json().catch(() => null);
        throw new Error(err?.error || `Search failed: ${res.statusText}`);
    }
    return res.json();
}

/**
 * Fetch recently updated artifacts.
 */
export async function fetchRecentArtifacts(count: number = 20): Promise<SearchResultItem[]> {
    const res = await fetch(`/api/maven/recent?count=${count}`);
    if (!res.ok) {
        throw new Error(`Failed to fetch recent artifacts: ${res.statusText}`);
    }
    return res.json();
}

/**
 * Fetch trending (popular) artifacts.
 */
export async function fetchTrendingArtifacts(): Promise<SearchResultItem[]> {
    const res = await fetch(`/api/maven/trending`);
    if (!res.ok) {
        throw new Error(`Failed to fetch trending artifacts: ${res.statusText}`);
    }
    return res.json();
}

// ─── Utility ────────────────────────────────────────────────────

/**
 * Format a timestamp (millis since epoch) into a human-readable date.
 */
export function formatTimestamp(timestamp: number): string {
    if (!timestamp) return '';
    return new Date(timestamp).toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
    });
}

/**
 * Format relative time (e.g., "3 months ago").
 */
export function relativeTime(timestamp: number): string {
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
 * Well-known artifact display names for prettier labels.
 */
export const KNOWN_ARTIFACTS: Record<string, string> = {
    'org.springframework.boot:spring-boot-starter-web': 'Spring Boot Web',
    'org.springframework.boot:spring-boot-starter-data-jpa': 'Spring Boot JPA',
    'com.google.guava:guava': 'Google Guava',
    'org.apache.commons:commons-lang3': 'Apache Commons Lang',
    'com.fasterxml.jackson.core:jackson-databind': 'Jackson Databind',
    'org.projectlombok:lombok': 'Project Lombok',
    'org.slf4j:slf4j-api': 'SLF4J API',
    'ch.qos.logback:logback-classic': 'Logback Classic',
    'org.mockito:mockito-core': 'Mockito Core',
    'com.google.code.gson:gson': 'Google Gson',
    'io.netty:netty-all': 'Netty',
    'org.apache.kafka:kafka-clients': 'Kafka Clients',
    'com.zaxxer:HikariCP': 'HikariCP',
    'org.postgresql:postgresql': 'PostgreSQL JDBC',
    'org.hibernate.orm:hibernate-core': 'Hibernate ORM',
    'io.micrometer:micrometer-core': 'Micrometer',
    'com.squareup.okhttp3:okhttp': 'OkHttp',
    'org.apache.httpcomponents.client5:httpclient5': 'Apache HttpClient 5',
    'io.projectreactor:reactor-core': 'Project Reactor',
    'org.junit.jupiter:junit-jupiter': 'JUnit Jupiter',
};

export function getDisplayName(groupId: string, artifactId: string): string {
    return KNOWN_ARTIFACTS[`${groupId}:${artifactId}`] || artifactId;
}
