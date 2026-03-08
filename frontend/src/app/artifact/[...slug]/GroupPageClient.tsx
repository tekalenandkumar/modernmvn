'use client';

import { useState } from 'react';
import Link from 'next/link';
import {
    Package,
    Tag,
    Clock,
    ChevronLeft,
    ChevronRight,
    ExternalLink,
    ArrowRight,
    Layers,
} from 'lucide-react';
import { type GroupArtifactItem, timeAgo } from '@/lib/artifact-api';

interface Props {
    groupId: string;
    totalResults: number;
    initialItems: GroupArtifactItem[];
    initialPage: number;
    pageSize: number;
}

export default function GroupPageClient({ groupId, totalResults, initialItems, initialPage, pageSize }: Props) {
    const [items, setItems] = useState<GroupArtifactItem[]>(initialItems);
    const [page, setPage] = useState(initialPage);
    const [loading, setLoading] = useState(false);
    const [filter, setFilter] = useState('');

    const totalPages = Math.ceil(totalResults / pageSize);

    const goToPage = async (newPage: number) => {
        if (newPage < 0 || newPage >= totalPages || loading) return;
        setLoading(true);
        try {
            const params = new URLSearchParams({ page: String(newPage), size: String(pageSize) });
            const res = await fetch(`/api/maven/group/${encodeURIComponent(groupId)}?${params}`);
            if (res.ok) {
                const data = await res.json();
                setItems(data.items);
                setPage(newPage);
                window.scrollTo({ top: 0, behavior: 'smooth' });
            }
        } catch { /* ignore */ }
        setLoading(false);
    };

    const filteredItems = filter
        ? items.filter(i =>
            i.artifactId.toLowerCase().includes(filter.toLowerCase()) ||
            (i.description?.toLowerCase().includes(filter.toLowerCase()))
        )
        : items;

    // JSON-LD
    const jsonLd = {
        '@context': 'https://schema.org',
        '@type': 'ItemList',
        name: `${groupId} Maven Artifacts`,
        description: `All Maven artifacts published under the ${groupId} group`,
        numberOfItems: totalResults,
    };

    return (
        <div className="min-h-screen bg-black text-white font-[family-name:var(--font-geist-sans)]">
            <script
                type="application/ld+json"
                dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
            />

            {/* Nav */}
            <nav className="sticky top-0 z-50 border-b border-gray-800 bg-black/80 backdrop-blur-xl">
                <div className="max-w-6xl mx-auto px-6 py-4 flex items-center justify-between">
                    <Link href="/" className="text-xl font-extrabold bg-gradient-to-r from-blue-500 to-purple-600 bg-clip-text text-transparent">
                        modernmvn.
                    </Link>
                    <div className="flex items-center gap-4 text-sm">
                        <Link href="/" className="text-gray-400 hover:text-white transition-colors">Home</Link>
                        <Link href="/analyze" className="text-gray-400 hover:text-white transition-colors">Analyze</Link>
                    </div>
                </div>
            </nav>

            <main className="max-w-6xl mx-auto px-6 py-10">
                {/* Breadcrumb */}
                <nav className="text-sm text-gray-500 mb-6 flex items-center gap-1.5">
                    <Link href="/" className="hover:text-blue-400 transition-colors">Home</Link>
                    <span>/</span>
                    <span className="text-white font-medium font-mono">{groupId}</span>
                </nav>

                {/* Hero */}
                <div className="mb-8 pb-8 border-b border-gray-800">
                    <div className="flex items-start gap-4">
                        <div className="p-3 rounded-xl bg-gradient-to-br from-violet-600/20 to-blue-600/20 border border-violet-800/30 shrink-0">
                            <Layers size={32} className="text-violet-400" />
                        </div>
                        <div className="flex-1 min-w-0">
                            <h1 className="text-3xl md:text-4xl font-extrabold tracking-tight font-mono break-all">
                                {groupId}
                            </h1>
                            <p className="text-gray-400 mt-2">
                                Maven Group · <span className="text-violet-300 font-bold">{totalResults.toLocaleString()}</span> artifact{totalResults !== 1 ? 's' : ''}
                            </p>
                            <div className="flex flex-wrap gap-3 mt-4">
                                <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium bg-violet-900/30 text-violet-300 border border-violet-800/40">
                                    <Package size={11} /> {totalResults} artifacts
                                </span>
                                <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium bg-gray-800 text-gray-400 border border-gray-700">
                                    Page {page + 1} of {totalPages}
                                </span>
                            </div>
                        </div>
                    </div>
                </div>

                {/* Filter */}
                <div className="mb-5">
                    <div className="relative max-w-sm">
                        <Package size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-600" />
                        <input
                            type="text"
                            placeholder="Filter artifacts on this page..."
                            value={filter}
                            onChange={e => setFilter(e.target.value)}
                            className="w-full pl-9 pr-4 py-2.5 rounded-xl bg-gray-900 border border-gray-800 text-sm text-gray-300 placeholder-gray-600 focus:border-violet-500 focus:outline-none transition-colors"
                        />
                    </div>
                    {filter && (
                        <p className="text-xs text-gray-600 mt-2">
                            Showing {filteredItems.length} of {items.length} on this page
                        </p>
                    )}
                </div>

                {/* Artifact Grid */}
                <div className={`grid grid-cols-1 gap-3 transition-opacity duration-200 ${loading ? 'opacity-40 pointer-events-none' : 'opacity-100'}`}>
                    {filteredItems.map((item, i) => (
                        <ArtifactRow key={`${item.artifactId}-${i}`} item={item} />
                    ))}
                    {filteredItems.length === 0 && (
                        <div className="py-16 text-center text-gray-600 text-sm">
                            No artifacts match &quot;{filter}&quot; on this page.
                        </div>
                    )}
                </div>

                {/* Pagination */}
                {totalPages > 1 && (
                    <div className="mt-10 flex items-center justify-between">
                        <button
                            onClick={() => goToPage(page - 1)}
                            disabled={page === 0 || loading}
                            className="flex items-center gap-2 px-4 py-2.5 rounded-lg text-sm font-medium bg-gray-900 border border-gray-800 hover:bg-gray-800 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                        >
                            <ChevronLeft size={16} /> Previous
                        </button>

                        <div className="flex items-center gap-1">
                            {getPaginationPages(page, totalPages).map((p, i) =>
                                p === '...' ? (
                                    <span key={`ellipsis-${i}`} className="px-2 text-gray-600">…</span>
                                ) : (
                                    <button
                                        key={p}
                                        onClick={() => goToPage(Number(p) - 1)}
                                        className={`min-w-[36px] h-9 rounded-lg text-sm font-medium transition-colors ${Number(p) - 1 === page
                                            ? 'bg-violet-600 text-white'
                                            : 'bg-gray-900 border border-gray-800 text-gray-400 hover:bg-gray-800'
                                            }`}
                                    >
                                        {p}
                                    </button>
                                )
                            )}
                        </div>

                        <button
                            onClick={() => goToPage(page + 1)}
                            disabled={page >= totalPages - 1 || loading}
                            className="flex items-center gap-2 px-4 py-2.5 rounded-lg text-sm font-medium bg-gray-900 border border-gray-800 hover:bg-gray-800 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                        >
                            Next <ChevronRight size={16} />
                        </button>
                    </div>
                )}
            </main>

            {/* Footer */}
            <footer className="border-t border-gray-800 mt-20 py-8 text-center text-gray-500 text-sm">
                <p>© 2026 Modern Maven. All rights reserved.</p>
            </footer>
        </div>
    );
}

// ─── Sub-components ────────────────────────────────────────────

function ArtifactRow({ item }: { item: GroupArtifactItem }) {
    return (
        <Link
            href={`/artifact/${item.groupId}/${item.artifactId}`}
            className="flex items-center gap-4 px-5 py-4 rounded-xl border border-gray-800/60 bg-gray-950/40 hover:bg-gray-900/50 hover:border-violet-800/30 transition-all group"
        >
            {/* Icon */}
            <div className="w-10 h-10 rounded-lg bg-violet-500/10 border border-violet-500/20 flex items-center justify-center shrink-0">
                <Package size={18} className="text-violet-400 group-hover:text-violet-300 transition-colors" />
            </div>

            {/* Main info */}
            <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 flex-wrap">
                    <span className="text-sm font-bold text-gray-100 group-hover:text-violet-300 transition-colors font-mono">
                        {item.artifactId}
                    </span>
                    {item.packaging && item.packaging !== 'jar' && (
                        <span className="px-1.5 py-0.5 rounded text-[10px] font-medium bg-gray-800 text-gray-500 border border-gray-700">
                            {item.packaging}
                        </span>
                    )}
                </div>
                {item.description && (
                    <p className="text-xs text-gray-500 mt-0.5 truncate max-w-xl">{item.description}</p>
                )}
            </div>

            {/* Meta */}
            <div className="flex items-center gap-5 shrink-0 text-xs text-gray-600">
                {item.latestVersion && (
                    <div className="flex items-center gap-1">
                        <ExternalLink size={11} className="text-blue-500" />
                        <span className="font-mono text-blue-400">{item.latestVersion}</span>
                    </div>
                )}
                {item.versionCount > 0 && (
                    <div className="flex items-center gap-1 hidden sm:flex">
                        <Tag size={11} />
                        <span>{item.versionCount}</span>
                    </div>
                )}
                {item.timestamp > 0 && (
                    <div className="flex items-center gap-1 hidden md:flex">
                        <Clock size={11} />
                        <span>{timeAgo(item.timestamp)}</span>
                    </div>
                )}
                <ArrowRight size={14} className="text-gray-700 group-hover:text-violet-400 transition-colors" />
            </div>
        </Link>
    );
}

// ─── Pagination helper ─────────────────────────────────────────

function getPaginationPages(current: number, total: number): (number | string)[] {
    const pages: (number | string)[] = [];
    const delta = 2;
    const left = current - delta;
    const right = current + delta + 1;

    for (let i = 1; i <= total; i++) {
        if (i === 1 || i === total || (i >= left && i < right)) {
            pages.push(i);
        } else if (pages[pages.length - 1] !== '...') {
            pages.push('...');
        }
    }
    return pages;
}
