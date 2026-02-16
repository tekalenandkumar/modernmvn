'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import Link from 'next/link';
import {
    Search as SearchIcon,
    Package,
    TrendingUp,
    Clock,
    ArrowRight,
    Loader2,
    Tag,
    ExternalLink,
} from 'lucide-react';
import {
    searchArtifacts,
    fetchRecentArtifacts,
    fetchTrendingArtifacts,
    formatTimestamp,
    relativeTime,
    getDisplayName,
    type SearchResultItem,
    type SearchResult,
} from '@/lib/search-api';

export default function SearchPageClient() {
    const [query, setQuery] = useState('');
    const [results, setResults] = useState<SearchResult | null>(null);
    const [trending, setTrending] = useState<SearchResultItem[]>([]);
    const [recent, setRecent] = useState<SearchResultItem[]>([]);
    const [loading, setLoading] = useState(false);
    const [trendingLoading, setTrendingLoading] = useState(true);
    const [recentLoading, setRecentLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [page, setPage] = useState(0);
    const searchTimeout = useRef<NodeJS.Timeout>(null);
    const inputRef = useRef<HTMLInputElement>(null);

    // Load trending and recent on mount
    useEffect(() => {
        fetchTrendingArtifacts()
            .then(setTrending)
            .catch(() => { })
            .finally(() => setTrendingLoading(false));

        fetchRecentArtifacts(15)
            .then(setRecent)
            .catch(() => { })
            .finally(() => setRecentLoading(false));
    }, []);

    // Focus search input on mount
    useEffect(() => {
        inputRef.current?.focus();
    }, []);

    const doSearch = useCallback(
        async (q: string, p: number = 0) => {
            if (!q.trim()) {
                setResults(null);
                setError(null);
                return;
            }
            setLoading(true);
            setError(null);
            try {
                const res = await searchArtifacts(q, p, 20);
                setResults(res);
                setPage(p);
            } catch (e: unknown) {
                setError(e instanceof Error ? e.message : 'Search failed');
            } finally {
                setLoading(false);
            }
        },
        []
    );

    // Debounced search
    const handleQueryChange = (val: string) => {
        setQuery(val);
        if (searchTimeout.current) clearTimeout(searchTimeout.current);
        searchTimeout.current = setTimeout(() => doSearch(val, 0), 350);
    };

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        if (searchTimeout.current) clearTimeout(searchTimeout.current);
        doSearch(query, 0);
    };

    const hasSearchResults = results && results.items.length > 0;
    const showDiscovery = !query.trim() && !results;

    return (
        <div className="min-h-screen bg-black text-white font-[family-name:var(--font-geist-sans)]">
            {/* Navbar */}
            <nav className="sticky top-0 z-50 bg-black/80 backdrop-blur-xl border-b border-gray-800/50">
                <div className="max-w-6xl mx-auto px-6 h-14 flex items-center justify-between">
                    <Link
                        href="/"
                        className="text-lg font-extrabold bg-gradient-to-r from-blue-400 to-purple-500 bg-clip-text text-transparent"
                    >
                        modernmvn.
                    </Link>
                    <div className="flex gap-4 text-sm text-gray-400">
                        <Link href="/" className="hover:text-white transition-colors">Home</Link>
                        <Link href="/analyze" className="hover:text-white transition-colors">Analyze</Link>
                        <span className="text-blue-400 font-medium">Search</span>
                    </div>
                </div>
            </nav>

            {/* Hero + Search */}
            <div className="relative overflow-hidden">
                <div className="absolute inset-0 bg-gradient-to-b from-blue-950/30 via-transparent to-transparent" />
                <div className="relative max-w-4xl mx-auto px-6 pt-16 pb-10 text-center">
                    <h1 className="text-4xl sm:text-5xl font-extrabold tracking-tight mb-4">
                        <span className="bg-gradient-to-r from-blue-400 via-purple-400 to-blue-400 bg-clip-text text-transparent">
                            Search Maven Artifacts
                        </span>
                    </h1>
                    <p className="text-gray-400 text-lg mb-10 max-w-xl mx-auto">
                        Find any Java or Kotlin library on Maven Central. Search by name, groupId, or keyword.
                    </p>

                    <form onSubmit={handleSubmit} className="relative max-w-2xl mx-auto">
                        <div className="relative">
                            <SearchIcon className="absolute left-5 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-500" />
                            <input
                                ref={inputRef}
                                id="artifact-search"
                                type="text"
                                value={query}
                                onChange={(e) => handleQueryChange(e.target.value)}
                                placeholder="e.g. spring-boot, guava, org.apache.commons..."
                                className="w-full pl-14 pr-28 py-4 rounded-2xl bg-gray-900/80 border border-gray-700/50 text-white text-lg placeholder:text-gray-600 focus:outline-none focus:border-blue-500/50 focus:ring-2 focus:ring-blue-500/20 transition-all"
                                autoComplete="off"
                            />
                            <button
                                type="submit"
                                disabled={loading || !query.trim()}
                                className="absolute right-2 top-1/2 -translate-y-1/2 px-5 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-700 disabled:bg-gray-700 disabled:text-gray-500 text-white text-sm font-bold transition-all"
                            >
                                {loading ? (
                                    <Loader2 className="w-4 h-4 animate-spin" />
                                ) : (
                                    'Search'
                                )}
                            </button>
                        </div>
                        {/* Quick suggestions */}
                        {!query && (
                            <div className="flex flex-wrap items-center justify-center gap-2 mt-4">
                                <span className="text-xs text-gray-600">Try:</span>
                                {['spring-boot', 'guava', 'jackson', 'lombok', 'kafka'].map((term) => (
                                    <button
                                        key={term}
                                        type="button"
                                        onClick={() => {
                                            setQuery(term);
                                            doSearch(term, 0);
                                        }}
                                        className="text-xs px-3 py-1 rounded-full bg-gray-800/60 border border-gray-700/50 text-gray-400 hover:text-blue-300 hover:border-blue-800/50 transition-all"
                                    >
                                        {term}
                                    </button>
                                ))}
                            </div>
                        )}
                    </form>
                </div>
            </div>

            {/* Error */}
            {error && (
                <div className="max-w-4xl mx-auto px-6 mb-6">
                    <div className="bg-red-950/30 border border-red-800/30 rounded-xl p-4 text-red-300 text-sm">
                        {error}
                    </div>
                </div>
            )}

            {/* Search Results */}
            {results && (
                <div className="max-w-4xl mx-auto px-6 pb-16">
                    <div className="flex items-center justify-between mb-6">
                        <p className="text-sm text-gray-500">
                            {results.totalResults.toLocaleString()} results for{' '}
                            <span className="text-gray-300 font-medium">&quot;{results.query}&quot;</span>
                        </p>
                        {results.totalResults > 20 && (
                            <div className="flex items-center gap-2">
                                <button
                                    onClick={() => doSearch(query, Math.max(0, page - 1))}
                                    disabled={page === 0 || loading}
                                    className="px-3 py-1.5 rounded-lg text-xs font-medium bg-gray-800 border border-gray-700 hover:bg-gray-700 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                                >
                                    Previous
                                </button>
                                <span className="text-xs text-gray-500">
                                    Page {page + 1}
                                </span>
                                <button
                                    onClick={() => doSearch(query, page + 1)}
                                    disabled={(page + 1) * 20 >= results.totalResults || loading}
                                    className="px-3 py-1.5 rounded-lg text-xs font-medium bg-gray-800 border border-gray-700 hover:bg-gray-700 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                                >
                                    Next
                                </button>
                            </div>
                        )}
                    </div>

                    {hasSearchResults ? (
                        <div className="space-y-3">
                            {results.items.map((item, i) => (
                                <ArtifactCard key={`${item.groupId}:${item.artifactId}:${i}`} item={item} />
                            ))}
                        </div>
                    ) : (
                        <div className="text-center py-16 text-gray-500">
                            <Package className="w-12 h-12 mx-auto mb-3 opacity-30" />
                            <p>No artifacts found for &quot;{query}&quot;</p>
                            <p className="text-xs mt-1 text-gray-600">Try a different search term or check the spelling</p>
                        </div>
                    )}
                </div>
            )}

            {/* Discovery Sections (shown when no search is active) */}
            {showDiscovery && (
                <div className="max-w-6xl mx-auto px-6 pb-20">
                    <div className="grid grid-cols-1 lg:grid-cols-5 gap-10">
                        {/* Trending */}
                        <div className="lg:col-span-3">
                            <div className="flex items-center gap-2 mb-6">
                                <TrendingUp className="w-5 h-5 text-orange-400" />
                                <h2 className="text-xl font-bold">Trending Artifacts</h2>
                            </div>
                            {trendingLoading ? (
                                <SkeletonCards count={6} />
                            ) : (
                                <div className="space-y-2">
                                    {trending.map((item, i) => (
                                        <ArtifactCard key={`trending-${i}`} item={item} compact />
                                    ))}
                                </div>
                            )}
                        </div>

                        {/* Recently Updated */}
                        <div className="lg:col-span-2">
                            <div className="flex items-center gap-2 mb-6">
                                <Clock className="w-5 h-5 text-green-400" />
                                <h2 className="text-xl font-bold">Recently Updated</h2>
                            </div>
                            {recentLoading ? (
                                <SkeletonCards count={5} small />
                            ) : (
                                <div className="space-y-2">
                                    {recent.map((item, i) => (
                                        <RecentCard key={`recent-${i}`} item={item} />
                                    ))}
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}

// ─── Sub-components ─────────────────────────────────────────────

function ArtifactCard({ item, compact }: { item: SearchResultItem; compact?: boolean }) {
    const displayName = getDisplayName(item.groupId, item.artifactId);
    return (
        <Link
            href={`/artifact/${item.groupId}/${item.artifactId}`}
            className={`block rounded-xl border border-gray-800/60 bg-gray-950/40 hover:bg-gray-900/50 hover:border-blue-800/40 transition-all group ${compact ? 'p-4' : 'p-5'}`}
        >
            <div className="flex items-start justify-between gap-4">
                <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 mb-1">
                        <Package className="w-4 h-4 text-blue-400 shrink-0" />
                        <h3 className="font-bold text-white group-hover:text-blue-300 transition-colors truncate">
                            {displayName}
                        </h3>
                    </div>
                    <p className="text-xs text-gray-500 font-mono truncate mb-2">
                        {item.groupId}:{item.artifactId}
                    </p>
                    {item.description && !compact && (
                        <p className="text-sm text-gray-400 line-clamp-2">{item.description}</p>
                    )}
                </div>
                <div className="flex flex-col items-end gap-1.5 shrink-0">
                    {item.latestVersion && (
                        <span className="text-xs px-2.5 py-1 rounded-md bg-blue-500/10 border border-blue-500/20 text-blue-300 font-mono">
                            {item.latestVersion}
                        </span>
                    )}
                    {item.versionCount > 0 && (
                        <span className="text-[10px] text-gray-600 flex items-center gap-1">
                            <Tag className="w-3 h-3" />
                            {item.versionCount} versions
                        </span>
                    )}
                </div>
            </div>
            <div className="flex items-center gap-4 mt-3 text-[11px] text-gray-600">
                {item.packaging && (
                    <span className="flex items-center gap-1">
                        <Package className="w-3 h-3" /> {item.packaging}
                    </span>
                )}
                {item.timestamp > 0 && (
                    <span className="flex items-center gap-1">
                        <Clock className="w-3 h-3" /> {relativeTime(item.timestamp)}
                    </span>
                )}
                <span className="ml-auto text-blue-500 group-hover:text-blue-400 flex items-center gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity">
                    View <ArrowRight className="w-3 h-3" />
                </span>
            </div>
        </Link>
    );
}

function RecentCard({ item }: { item: SearchResultItem }) {
    return (
        <Link
            href={`/artifact/${item.groupId}/${item.artifactId}`}
            className="flex items-center gap-3 p-3 rounded-lg border border-gray-800/40 bg-gray-950/30 hover:bg-gray-900/40 hover:border-green-800/30 transition-all group"
        >
            <div className="w-8 h-8 rounded-lg bg-green-500/10 border border-green-500/20 flex items-center justify-center shrink-0">
                <ExternalLink className="w-3.5 h-3.5 text-green-400" />
            </div>
            <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-gray-200 group-hover:text-green-300 transition-colors truncate">
                    {item.artifactId}
                </p>
                <p className="text-[10px] text-gray-600 font-mono truncate">{item.groupId}</p>
            </div>
            <div className="flex flex-col items-end shrink-0">
                <span className="text-[10px] font-mono text-gray-500">{item.latestVersion}</span>
                <span className="text-[9px] text-gray-700">{formatTimestamp(item.timestamp)}</span>
            </div>
        </Link>
    );
}

function SkeletonCards({ count, small }: { count: number; small?: boolean }) {
    return (
        <div className="space-y-2">
            {Array.from({ length: count }).map((_, i) => (
                <div
                    key={i}
                    className={`rounded-xl border border-gray-800/30 bg-gray-950/30 animate-pulse ${small ? 'h-14' : 'h-24'}`}
                />
            ))}
        </div>
    );
}
