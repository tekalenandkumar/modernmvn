import { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { fetchArtifactInfo, fetchArtifactDetail, fetchGroupArtifacts } from '@/lib/artifact-api';
import ArtifactPageClient from './ArtifactPageClient';
import GroupPageClient from './GroupPageClient';

// ISR: regenerate every hour for artifact pages, 6h for group pages
export const revalidate = 3600;

interface PageProps {
    params: Promise<{ slug: string[] }>;
}

type ParsedSlug =
    | { kind: 'group'; groupId: string }
    | { kind: 'artifact'; groupId: string; artifactId: string; version?: string }
    | null;

/**
 * Parse the slug segments:
 *   /artifact/org.springframework.boot                     → group page
 *   /artifact/org.apache.commons/commons-lang3             → artifact page
 *   /artifact/org.apache.commons/commons-lang3/3.14.0     → artifact version page
 */
function parseSlug(slug: string[]): ParsedSlug {
    if (slug.length === 1) {
        return { kind: 'group', groupId: slug[0] };
    }
    if (slug.length === 2) {
        return { kind: 'artifact', groupId: slug[0], artifactId: slug[1] };
    }
    if (slug.length === 3) {
        return { kind: 'artifact', groupId: slug[0], artifactId: slug[1], version: slug[2] };
    }
    return null;
}

// ─── Dynamic Metadata ───────────────────────────────────────────

export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
    const { slug } = await params;
    const parsed = parseSlug(slug);
    if (!parsed) return { title: 'Not Found | Modern Maven' };

    if (parsed.kind === 'group') {
        const { groupId } = parsed;
        return {
            title: `${groupId} Artifacts | Modern Maven`,
            description: `Browse all Maven artifacts published under the ${groupId} group on Modern Maven.`,
            openGraph: {
                title: `${groupId} Maven Artifacts`,
                description: `All artifacts in the ${groupId} Maven group.`,
                type: 'website',
                siteName: 'Modern Maven',
            },
            alternates: { canonical: `/artifact/${groupId}` },
        };
    }

    // Artifact or artifact+version
    const { groupId, artifactId, version } = parsed;
    const coord = version ? `${groupId}:${artifactId}:${version}` : `${groupId}:${artifactId}`;

    try {
        const info = await fetchArtifactInfo(groupId, artifactId);
        const desc = info.description || `Maven artifact ${coord} — versions, dependency snippets, and license information.`;
        const title = version
            ? `${artifactId} ${version} — ${groupId} | Modern Maven`
            : `${artifactId} — ${groupId} | Modern Maven`;

        return {
            title,
            description: desc,
            openGraph: {
                title,
                description: desc,
                type: 'website',
                siteName: 'Modern Maven',
                url: `/artifact/${groupId}/${artifactId}${version ? `/${version}` : ''}`,
            },
            alternates: {
                canonical: `/artifact/${groupId}/${artifactId}${version ? `/${version}` : ''}`,
            },
            other: { 'application-name': 'Modern Maven' },
        };
    } catch {
        return {
            title: `${artifactId} — ${groupId} | Modern Maven`,
            description: `Maven artifact ${coord}`,
        };
    }
}

// ─── Page Component ─────────────────────────────────────────────

export default async function ArtifactPage({ params }: PageProps) {
    const { slug } = await params;
    const parsed = parseSlug(slug);
    if (!parsed) notFound();

    // ── Group Page ──────────────────────────────────────────────
    if (parsed.kind === 'group') {
        const { groupId } = parsed;
        let data = null;
        try {
            data = await fetchGroupArtifacts(groupId, 0, 20);
        } catch {
            notFound();
        }

        return (
            <GroupPageClient
                groupId={groupId}
                totalResults={data.totalResults}
                initialItems={data.items}
                initialPage={0}
                pageSize={20}
            />
        );
    }

    // ── Artifact / Version Page ─────────────────────────────────
    const { groupId, artifactId, version } = parsed;
    let info = null;
    try {
        info = await fetchArtifactInfo(groupId, artifactId);
    } catch {
        notFound();
    }

    let detail = null;
    const targetVersion = version || info.latestReleaseVersion || info.latestVersion;
    if (targetVersion) {
        try {
            detail = await fetchArtifactDetail(groupId, artifactId, targetVersion);
        } catch {
            // detail fetch may fail for some artifacts
        }
    }

    return (
        <ArtifactPageClient
            info={info}
            detail={detail}
            selectedVersion={version || null}
        />
    );
}
