// ─── Types ──────────────────────────────────────────────────────

export interface DependencyNode {
    groupId: string;
    artifactId: string;
    version: string;
    scope: string;
    type: string;
    children: DependencyNode[];
    resolutionStatus: "RESOLVED" | "CONFLICT" | "OPTIONAL" | "ERROR" | "MISSING" | "LOCAL";
    conflictMessage?: string;
    // Computed fields (not from API)
    id?: string;
}

export interface ModuleInfo {
    moduleName: string;
    groupId: string;
    artifactId: string;
    version: string;
    packaging: string;
    dependencyTree: DependencyNode;
}

export interface MultiModuleResult {
    parentGroupId: string;
    parentArtifactId: string;
    parentVersion: string;
    isMultiModule: boolean;
    modules: ModuleInfo[];
    mergedTree: DependencyNode;
}

export interface SecurityLimits {
    maxPomSizeKB: number;
    maxCustomRepos: number;
    allowedRepoSchemes: string[];
    sessionTimeoutMinutes: number;
    disclaimer: string;
}

// ─── Params ─────────────────────────────────────────────────────

export type FetchGraphParams = {
    groupId?: string;
    artifactId?: string;
    version?: string;
    pomContent?: string;
    customRepositories?: string[];
}

// ─── API functions ──────────────────────────────────────────────

export const fetchDependencyGraph = async (params: FetchGraphParams): Promise<DependencyNode | null> => {
    if (params.pomContent) {
        const hasCustomRepos = params.customRepositories && params.customRepositories.length > 0;

        if (hasCustomRepos) {
            // Use advanced endpoint
            const response = await fetch('/api/maven/resolve/pom/advanced', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    pomContent: params.pomContent,
                    customRepositories: params.customRepositories,
                    detectMultiModule: false,
                }),
            });
            if (!response.ok) {
                const errorData = await response.json().catch(() => null);
                throw new Error(errorData?.error || `Failed to resolve POM: ${response.statusText}`);
            }
            return response.json();
        }

        const response = await fetch('/api/maven/resolve/pom', {
            method: 'POST',
            headers: { 'Content-Type': 'text/plain' },
            body: params.pomContent,
        });
        if (!response.ok) {
            throw new Error(`Failed to resolve POM: ${response.statusText}`);
        }
        return response.json();
    }

    if (params.groupId && params.artifactId && params.version) {
        const qs = new URLSearchParams({
            groupId: params.groupId,
            artifactId: params.artifactId,
            version: params.version,
        });
        if (params.customRepositories) {
            params.customRepositories.forEach(url => qs.append('repos', url));
        }
        const response = await fetch(`/api/maven/resolve?${qs.toString()}`);
        if (!response.ok) {
            throw new Error(`Failed to resolve artifact: ${response.statusText}`);
        }
        return response.json();
    }

    return null;
}

/**
 * Upload a POM file for analysis with optional multi-module detection and custom repos.
 */
export const uploadPomFile = async (
    file: File,
    customRepositories: string[],
    detectMultiModule: boolean
): Promise<MultiModuleResult | DependencyNode> => {
    const formData = new FormData();
    formData.append('file', file);

    const qs = new URLSearchParams();
    qs.set('detectMultiModule', String(detectMultiModule));
    customRepositories.forEach(url => {
        if (url.trim()) qs.append('repos', url.trim());
    });

    const response = await fetch(`/api/maven/resolve/upload?${qs.toString()}`, {
        method: 'POST',
        body: formData,
    });

    if (!response.ok) {
        const errorData = await response.json().catch(() => null);
        throw new Error(errorData?.error || `Upload failed: ${response.statusText}`);
    }

    return response.json();
}

/**
 * Resolve a POM with multi-module detection via advanced endpoint.
 */
export const fetchMultiModuleGraph = async (
    pomContent: string,
    customRepositories: string[]
): Promise<MultiModuleResult> => {
    const response = await fetch('/api/maven/resolve/pom/advanced', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            pomContent,
            customRepositories,
            detectMultiModule: true,
        }),
    });

    if (!response.ok) {
        const errorData = await response.json().catch(() => null);
        throw new Error(errorData?.error || `Failed to resolve POM: ${response.statusText}`);
    }

    return response.json();
}

/**
 * Fetch platform security limits and disclaimers.
 */
export const fetchSecurityLimits = async (): Promise<SecurityLimits> => {
    const response = await fetch('/api/maven/limits');
    if (!response.ok) {
        throw new Error('Failed to fetch limits');
    }
    return response.json();
}

/**
 * Type guard to check if a result is a MultiModuleResult.
 */
export function isMultiModuleResult(result: unknown): result is MultiModuleResult {
    return (
        typeof result === 'object' &&
        result !== null &&
        'isMultiModule' in result &&
        'modules' in result &&
        'mergedTree' in result
    );
}
