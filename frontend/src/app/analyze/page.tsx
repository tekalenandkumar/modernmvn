import { Metadata } from 'next';
import { Suspense } from 'react';
import AnalyzePageClient from './AnalyzePageClient';

interface AnalyzePageProps {
    searchParams: Promise<{ [key: string]: string | string[] | undefined }>;
}

export async function generateMetadata({ searchParams }: AnalyzePageProps): Promise<Metadata> {
    const params = await searchParams;
    const g = (params.g || params.groupId) as string | undefined;
    const a = (params.a || params.artifactId) as string | undefined;
    const v = (params.v || params.version) as string | undefined;

    if (g && a && v) {
        return {
            title: `Analyze ${a} ${v} Dependencies | ${g}`,
            description: `Full dependency tree analysis and security audit for ${g}:${a}:${v} on modernmvn. Detect conflicts and vulnerabilities.`,
        };
    }

    return {
        title: 'Analyze Maven Dependencies',
        description: 'Free online tool to analyze Maven dependency trees. Paste a POM file, upload pom.xml, or enter coordinates to visualize transitive dependencies and detect security risks.',
    };
}

export default function AnalyzePage() {
    return (
        <Suspense fallback={
            <div className="min-h-screen bg-black text-white flex items-center justify-center">
                <div className="text-gray-400">Loading analyzer...</div>
            </div>
        }>
            <AnalyzePageClient />
        </Suspense>
    );
}
