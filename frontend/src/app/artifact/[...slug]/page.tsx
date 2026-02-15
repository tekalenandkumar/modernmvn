import { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { fetchArtifactInfo, fetchArtifactDetail } from '@/lib/artifact-api';
import ArtifactPageClient from './ArtifactPageClient';

// ISR: regenerate every hour
export const revalidate = 3600;

interface PageProps {
    params: Promise<{ slug: string[] }>;
}

/**
 * Parse the slug segments:
 *   /artifact/org.apache.commons/commons-lang3        → [groupId, artifactId]
 *   /artifact/org.apache.commons/commons-lang3/3.14.0 → [groupId, artifactId, version]
 */
function parseSlug(slug: string[]): { groupId: string; artifactId: string; version?: string } | null {
    if (slug.length === 2) {
        return { groupId: slug[0], artifactId: slug[1] };
    }
    if (slug.length === 3) {
        return { groupId: slug[0], artifactId: slug[1], version: slug[2] };
    }
    return null;
}

// ─── Dynamic Metadata ───────────────────────────────────────────

export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
    const { slug } = await params;
    const parsed = parseSlug(slug);
    if (!parsed) return { title: 'Artifact Not Found | Modern Maven' };

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
            other: {
                'application-name': 'Modern Maven',
            },
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

    const { groupId, artifactId, version } = parsed;

    try {
        const info = await fetchArtifactInfo(groupId, artifactId);

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
    } catch {
        notFound();
    }
}
