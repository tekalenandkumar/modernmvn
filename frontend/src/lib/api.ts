export interface DependencyNode {
    groupId: string;
    artifactId: string;
    version: string;
    scope: string;
    type: string;
    children: DependencyNode[];
    resolutionStatus: "RESOLVED" | "CONFLICT" | "OPTIONAL" | "ERROR" | "MISSING";
    conflictMessage?: string;
    // Computed fields (not from API)
    id?: string;
}

export type FetchGraphParams = {
    groupId?: string;
    artifactId?: string;
    version?: string;
    pomContent?: string;
}

export const fetchDependencyGraph = async (params: FetchGraphParams): Promise<DependencyNode | null> => {
    if (params.pomContent) {
        const response = await fetch('/api/maven/resolve/pom', {
            method: 'POST',
            headers: {
                'Content-Type': 'text/plain',
            },
            body: params.pomContent,
        });
        if (!response.ok) {
            // Ideally parse error message
            throw new Error(`Failed to resolve POM: ${response.statusText}`);
        }
        return response.json();
    }

    if (params.groupId && params.artifactId && params.version) {
        const qs = new URLSearchParams({
            groupId: params.groupId,
            artifactId: params.artifactId,
            version: params.version
        });
        const response = await fetch(`/api/maven/resolve?${qs.toString()}`);
        if (!response.ok) {
            throw new Error(`Failed to resolve artifact: ${response.statusText}`);
        }
        return response.json();
    }

    return null;
}
