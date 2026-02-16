import type { Metadata } from 'next';
import SearchPageClient from './SearchPageClient';

export const metadata: Metadata = {
    title: 'Search Maven Artifacts',
    description: 'Search for Java and Kotlin libraries on Maven Central. Find artifacts by name, group ID, or keyword.',
    openGraph: {
        title: 'Search Maven Artifacts — Modern Maven',
        description: 'Search for Java and Kotlin libraries on Maven Central.',
    },
};

export default function SearchPage() {
    return <SearchPageClient />;
}
