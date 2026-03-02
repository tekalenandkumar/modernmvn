'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import {
    TrendingUp,
    Clock,
    ArrowRight,
    Package,
    Tag,
    ExternalLink,
} from 'lucide-react';
import {
    fetchTrendingArtifacts,
    fetchRecentArtifacts,
    relativeTime,
    formatTimestamp,
    getDisplayName,
    type SearchResultItem,
} from '@/lib/search-api';

export default function HomeContent() {
    const [trending, setTrending] = useState<SearchResultItem[]>([]);
    const [recent, setRecent] = useState<SearchResultItem[]>([]);
    const [trendingLoading, setTrendingLoading] = useState(true);
    const [recentLoading, setRecentLoading] = useState(true);

    useEffect(() => {
        fetchTrendingArtifacts()
            .then(setTrending)
            .catch(() => { })
            .finally(() => setTrendingLoading(false));

        fetchRecentArtifacts(12)
            .then(setRecent)
            .catch(() => { })
            .finally(() => setRecentLoading(false));
    }, []);

    return (
        <div className="w-full mt-8 pt-8 border-t border-gray-800">
            <div className="grid grid-cols-1 lg:grid-cols-5 gap-10">

                {/* Trending Libraries */}
                <div className="lg:col-span-3">
                    <div className="flex items-center gap-2 mb-4">
                        <TrendingUp className="w-5 h-5 text-orange-400" />
                        <h2 className="text-sm font-bold text-gray-400 uppercase tracking-wider">Trending Libraries</h2>
                        <Link href="/search" className="ml-auto text-xs text-blue-500 hover:text-blue-400 flex items-center gap-1 transition-colors">
                            View all <ArrowRight className="w-3 h-3" />
                        </Link>
                    </div>
                    {trendingLoading ? (
                        <SkeletonCards count={8} />
                    ) : (
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                            {trending.slice(0, 8).map((item, i) => (
                                <TrendingCard key={`t-${i}`} item={item} />
                            ))}
                        </div>
                    )}
                </div>

                {/* What's New */}
                <div className="lg:col-span-2">
                    <div className="flex items-center gap-2 mb-4">
                        <Clock className="w-5 h-5 text-green-400" />
                        <h2 className="text-sm font-bold text-gray-400 uppercase tracking-wider">What&apos;s New</h2>
                    </div>
                    {recentLoading ? (
                        <SkeletonCards count={6} small />
                    ) : (
                        <div className="space-y-1.5">
                            {recent.map((item, i) => (
                                <RecentCard key={`r-${i}`} item={item} />
                            ))}
                        </div>
                    )}
                </div>

            </div>
        </div>
    );
}

// ─── Sub-components ──────────────────────────────────

function TrendingCard({ item }: { item: SearchResultItem }) {
    const displayName = getDisplayName(item.groupId, item.artifactId);
    return (
        <Link
            href={`/artifact/${item.groupId}/${item.artifactId}`}
            className="flex items-center gap-3 p-3 rounded-xl border border-gray-800/60 bg-gray-950/40 hover:bg-gray-900/50 hover:border-orange-800/40 transition-all group"
        >
            <div className="w-9 h-9 rounded-lg bg-orange-500/10 border border-orange-500/20 flex items-center justify-center shrink-0">
                <Package className="w-4 h-4 text-orange-400" />
            </div>
            <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-gray-200 group-hover:text-orange-300 transition-colors truncate">
                    {displayName}
                </p>
                <p className="text-[10px] text-gray-600 font-mono truncate">{item.groupId}</p>
            </div>
            <div className="flex flex-col items-end shrink-0">
                <span className="text-[10px] font-mono text-blue-400">{item.latestVersion}</span>
                {item.versionCount > 0 && (
                    <span className="text-[9px] text-gray-600 flex items-center gap-0.5">
                        <Tag className="w-2.5 h-2.5" /> {item.versionCount}
                    </span>
                )}
            </div>
        </Link>
    );
}

function RecentCard({ item }: { item: SearchResultItem }) {
    return (
        <Link
            href={`/artifact/${item.groupId}/${item.artifactId}`}
            className="flex items-center gap-3 p-2.5 rounded-lg border border-gray-800/40 bg-gray-950/30 hover:bg-gray-900/40 hover:border-green-800/30 transition-all group"
        >
            <div className="w-7 h-7 rounded-md bg-green-500/10 border border-green-500/20 flex items-center justify-center shrink-0">
                <ExternalLink className="w-3 h-3 text-green-400" />
            </div>
            <div className="min-w-0 flex-1">
                <p className="text-xs font-medium text-gray-300 group-hover:text-green-300 transition-colors truncate">
                    {item.artifactId}
                </p>
                <p className="text-[10px] text-gray-600 font-mono truncate">{item.groupId}</p>
            </div>
            <div className="flex flex-col items-end shrink-0">
                <span className="text-[10px] font-mono text-gray-500">{item.latestVersion}</span>
                <span className="text-[9px] text-gray-700">{item.timestamp > 0 ? relativeTime(item.timestamp) : formatTimestamp(item.timestamp)}</span>
            </div>
        </Link>
    );
}

function SkeletonCards({ count, small }: { count: number; small?: boolean }) {
    return (
        <div className={small ? 'space-y-1.5' : 'grid grid-cols-1 sm:grid-cols-2 gap-2'}>
            {Array.from({ length: count }).map((_, i) => (
                <div
                    key={i}
                    className={`rounded-xl border border-gray-800/30 bg-gray-950/30 animate-pulse ${small ? 'h-12' : 'h-16'}`}
                />
            ))}
        </div>
    );
}
