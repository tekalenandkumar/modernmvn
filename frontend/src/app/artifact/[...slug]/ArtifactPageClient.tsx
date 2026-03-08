'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import {
    ArtifactInfo,
    ArtifactDetail,
    ArtifactVersion,
    formatDate,
    timeAgo,
    formatCount,
    SNIPPET_LABELS,
    fetchReverseDependencies,
    fetchReverseDependencyCount,
    type ReverseDependencyItem,
} from '@/lib/artifact-api';
import {
    Package,
    Copy,
    Check,
    Shield,
    ExternalLink,
    Clock,
    Tag,
    GitBranch,
    ChevronDown,
    ChevronUp,
    Star,
    FileCode,
    Layers,
    Search,
    Users,
    Image as ImageIcon,
    ArrowRight,
    Loader2,
} from 'lucide-react';
import SecurityBadge from '@/components/SecurityBadge';
import VulnerabilityPanel from '@/components/VulnerabilityPanel';
import VulnerabilityTrendChart from '@/components/VulnerabilityTrendChart';

interface Props {
    info: ArtifactInfo;
    detail: ArtifactDetail | null;
    selectedVersion: string | null;
}

export default function ArtifactPageClient({ info, detail, selectedVersion }: Props) {
    const [activeSnippet, setActiveSnippet] = useState('maven');
    const [copiedSnippet, setCopiedSnippet] = useState<string | null>(null);
    const [showAllVersions, setShowAllVersions] = useState(false);
    const [versionFilter, setVersionFilter] = useState('');
    const [showPreRelease, setShowPreRelease] = useState(false);
    const [useLatestForSnippet, setUseLatestForSnippet] = useState(false);

    // Used By state
    const [usedByCount, setUsedByCount] = useState<number>(info.usedByCount || 0);
    const [usedByItems, setUsedByItems] = useState<ReverseDependencyItem[]>([]);
    const [usedByLoading, setUsedByLoading] = useState(false);
    const [usedByPage, setUsedByPage] = useState(0);
    const [usedByTotal, setUsedByTotal] = useState(0);
    const [usedByExpanded, setUsedByExpanded] = useState(false);

    // Badge state
    const [copiedBadge, setCopiedBadge] = useState<string | null>(null);

    const currentVersion = selectedVersion || info.latestReleaseVersion || info.latestVersion;

    // Fetch "Used By" count on mount (fallback if not in info)
    useEffect(() => {
        if (info.usedByCount === undefined) {
            fetchReverseDependencyCount(info.groupId, info.artifactId)
                .then(setUsedByCount)
                .catch(() => setUsedByCount(0));
        }
    }, [info.groupId, info.artifactId, info.usedByCount]);

    // Fetch "Used By" items when expanded
    useEffect(() => {
        if (!usedByExpanded) return;

        // eslint-disable-next-line react-hooks/set-state-in-effect
        setUsedByLoading(true);
        fetchReverseDependencies(info.groupId, info.artifactId, usedByPage, 10)
            .then((result) => {
                setUsedByItems(result.items);
                setUsedByTotal(result.totalResults);
            })
            .catch(() => { })
            .finally(() => setUsedByLoading(false));
    }, [usedByExpanded, usedByPage, info.groupId, info.artifactId]);

    const copyToClipboard = (text: string, key: string) => {
        navigator.clipboard.writeText(text);
        setCopiedSnippet(key);
        setTimeout(() => setCopiedSnippet(null), 2000);
    };

    const copyBadgeCode = (text: string, key: string) => {
        navigator.clipboard.writeText(text);
        setCopiedBadge(key);
        setTimeout(() => setCopiedBadge(null), 2000);
    };

    // Filter versions
    const filteredVersions = info.versions.filter(v => {
        if (versionFilter && !v.version.toLowerCase().includes(versionFilter.toLowerCase())) return false;
        if (!showPreRelease && !v.isRelease) return false;
        return true;
    });

    const displayVersions = showAllVersions ? filteredVersions : filteredVersions.slice(0, 10);

    // Badge URLs — use relative /badge/ path which is proxied via Next.js rewrites
    const badgeUrl = `/badge/${info.groupId}/${info.artifactId}`;
    const artifactPageUrl = `https://modernmvn.com/artifact/${info.groupId}/${info.artifactId}`;
    const securityBadgeUrl = `/api/security/${info.groupId}/${info.artifactId}/${currentVersion}/badge.svg`;

    const badgeSnippets = {
        markdown: `[![Maven Version](${badgeUrl})](${artifactPageUrl})`,
        html: `<a href="${artifactPageUrl}"><img src="${badgeUrl}" alt="Maven Version" /></a>`,
        rst: `.. image:: ${badgeUrl}\n   :target: ${artifactPageUrl}\n   :alt: Maven Version`,
    };

    const securityBadgeSnippets = {
        markdown: `[![Security](${securityBadgeUrl})](${artifactPageUrl})`,
        html: `<a href="${artifactPageUrl}"><img src="${securityBadgeUrl}" alt="Security" /></a>`,
        rst: `.. image:: ${securityBadgeUrl}\n   :target: ${artifactPageUrl}\n   :alt: Security`,
    };

    // Version to use in snippets — recommended (default) or latest
    const snippetVersion = useLatestForSnippet
        ? (info.latestVersion || currentVersion)
        : (info.latestReleaseVersion || currentVersion);

    // JSON-LD structured data
    const jsonLd = {
        '@context': 'https://schema.org',
        '@type': 'SoftwareSourceCode',
        name: info.artifactId,
        description: info.description || `Maven artifact ${info.groupId}:${info.artifactId}`,
        codeRepository: info.url || undefined,
        programmingLanguage: 'Java',
        runtimePlatform: 'JVM',
        version: currentVersion,
        license: info.licenses?.length > 0 ? info.licenses[0].url || info.licenses[0].name : undefined,
    };

    return (
        <div className="min-h-screen bg-black text-white font-[family-name:var(--font-geist-sans)]">
            {/* JSON-LD */}
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
                {/* ── Hero Section ──────────────────────────────── */}
                <div className="mb-10">
                    {/* Breadcrumb */}
                    <nav aria-label="Breadcrumb" className="text-sm text-gray-500 mb-4 flex items-center gap-1.5">
                        <Link href="/" className="hover:text-blue-400 transition-colors">Home</Link>
                        <span>/</span>
                        <Link href={`/artifact/${info.groupId}`} className="hover:text-violet-400 transition-colors font-mono">
                            {info.groupId}
                        </Link>
                        <span>/</span>
                        {selectedVersion ? (
                            <>
                                <Link href={`/artifact/${info.groupId}/${info.artifactId}`} className="hover:text-blue-400 transition-colors">
                                    {info.artifactId}
                                </Link>
                                <span>/</span>
                                <span className="text-blue-400 font-mono">{selectedVersion}</span>
                            </>
                        ) : (
                            <span className="text-white font-medium">{info.artifactId}</span>
                        )}
                    </nav>

                    <div className="flex flex-col md:flex-row md:items-start justify-between gap-6">
                        <div className="flex-1">
                            <div className="flex items-center gap-3 mb-3">
                                <div className="p-2.5 rounded-xl bg-gradient-to-br from-blue-600/20 to-purple-600/20 border border-blue-800/30">
                                    <Package size={28} className="text-blue-400" />
                                </div>
                                <div>
                                    <h1 className="text-3xl md:text-3xl font-extrabold tracking-tight">
                                        <span className="text-gray-500 font-mono font-normal">{info.groupId}:</span>
                                        {info.artifactId}
                                    </h1>
                                </div>
                            </div>

                            {info.description && (
                                <p className="text-gray-300 text-lg leading-relaxed max-w-2xl mt-4">
                                    {info.description}
                                </p>
                            )}

                            {/* Meta badges */}
                            <div className="flex flex-wrap items-center gap-3 mt-5">
                                {info.latestReleaseVersion && (
                                    <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-bold bg-green-900/30 text-green-300 border border-green-800/50">
                                        <Star size={12} /> Recommended: {info.latestReleaseVersion}
                                    </span>
                                )}
                                <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium bg-gray-800 text-gray-300 border border-gray-700">
                                    <Tag size={12} /> {info.versionCount} versions
                                </span>
                                <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium bg-gray-800 text-gray-300 border border-gray-700">
                                    <FileCode size={12} /> {info.packaging}
                                </span>
                                {info.lastUpdated > 0 && (
                                    <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium bg-gray-800 text-gray-300 border border-gray-700">
                                        <Clock size={12} /> Updated {timeAgo(info.lastUpdated)}
                                    </span>
                                )}
                                {info.licenses?.length > 0 && (
                                    <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium bg-purple-900/30 text-purple-300 border border-purple-800/50">
                                        <Shield size={12} /> {info.licenses[0].name}
                                    </span>
                                )}
                                {/* Used By badge */}
                                {usedByCount >= 0 && (
                                    <button
                                        onClick={() => setUsedByExpanded(!usedByExpanded)}
                                        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium bg-cyan-900/30 text-cyan-300 border border-cyan-800/50 hover:bg-cyan-900/50 transition-colors cursor-pointer"
                                    >
                                        <Users size={12} /> Used by {formatCount(usedByCount)}
                                    </button>
                                )}
                                <SecurityBadge
                                    groupId={info.groupId}
                                    artifactId={info.artifactId}
                                    version={currentVersion}
                                />
                            </div>
                        </div>

                        {/* Quick actions */}
                        <div className="flex flex-col gap-2">
                            <Link
                                href={`/analyze?groupId=${info.groupId}&artifactId=${info.artifactId}&version=${currentVersion}`}
                                className="flex items-center gap-2 px-5 py-2.5 rounded-lg bg-blue-600 hover:bg-blue-700 text-white text-sm font-bold transition-all hover:scale-[1.02] active:scale-[0.98] shadow-lg shadow-blue-900/30"
                            >
                                <GitBranch size={16} />
                                Analyze Dependencies
                            </Link>
                            {info.url && (
                                <a
                                    href={info.url}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    className="flex items-center gap-2 px-5 py-2.5 rounded-lg border border-gray-700 hover:bg-gray-800 text-gray-300 text-sm font-medium transition-colors"
                                >
                                    <ExternalLink size={16} />
                                    Project Website
                                </a>
                            )}
                        </div>
                    </div>
                </div>

                {/* ── Main Content Grid ────────────────────────── */}
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">

                    {/* LEFT: Snippets + Detail + Used By + Badges */}
                    <div className="lg:col-span-2 space-y-8">

                        {/* Dependency Snippets */}
                        <section className="rounded-xl border border-gray-800 bg-gray-950/50 overflow-hidden">
                            <div className="px-6 py-4 border-b border-gray-800 flex items-center justify-between gap-2">
                                <div className="flex items-center gap-2">
                                    <Layers size={18} className="text-blue-400" />
                                    <h2 className="font-bold text-lg">
                                        Add to your project
                                        <span className="text-gray-500 font-mono text-xs ml-2">{snippetVersion}</span>
                                    </h2>
                                </div>
                                {/* Latest / Recommended toggle */}
                                {info.latestReleaseVersion && info.latestVersion && info.latestReleaseVersion !== info.latestVersion && (
                                    <div className="flex items-center gap-1 rounded-lg bg-gray-900 border border-gray-800 p-1 text-xs">
                                        <button
                                            onClick={() => setUseLatestForSnippet(false)}
                                            className={`px-2.5 py-1 rounded-md font-medium transition-colors ${!useLatestForSnippet
                                                ? 'bg-green-900/60 text-green-300 border border-green-800/50'
                                                : 'text-gray-500 hover:text-gray-300'
                                                }`}
                                        >
                                            ✓ Recommended
                                        </button>
                                        <button
                                            onClick={() => setUseLatestForSnippet(true)}
                                            className={`px-2.5 py-1 rounded-md font-medium transition-colors ${useLatestForSnippet
                                                ? 'bg-blue-900/60 text-blue-300 border border-blue-800/50'
                                                : 'text-gray-500 hover:text-gray-300'
                                                }`}
                                        >
                                            Latest
                                        </button>
                                    </div>
                                )}
                            </div>

                            {/* Snippet tabs */}
                            <div className="border-b border-gray-800 px-6 flex gap-1 overflow-x-auto scrollbar-hidden">
                                {detail && Object.keys(detail.dependencySnippets).map(key => (
                                    <button
                                        key={key}
                                        onClick={() => setActiveSnippet(key)}
                                        className={`px-3 py-2.5 text-xs font-medium whitespace-nowrap border-b-2 transition-colors ${activeSnippet === key
                                            ? 'border-blue-500 text-blue-400'
                                            : 'border-transparent text-gray-500 hover:text-gray-300'
                                            }`}
                                    >
                                        {SNIPPET_LABELS[key] || key}
                                    </button>
                                ))}
                            </div>

                            {/* Snippet code — version is swapped based on toggle */}
                            <div className="relative">
                                <pre className="p-6 text-sm font-mono text-gray-200 overflow-x-auto leading-relaxed">
                                    {(detail?.dependencySnippets?.[activeSnippet] || `<dependency>\n    <groupId>${info.groupId}</groupId>\n    <artifactId>${info.artifactId}</artifactId>\n    <version>${currentVersion}</version>\n</dependency>`)
                                        .replace(currentVersion, snippetVersion)}
                                </pre>
                                <button
                                    onClick={() => copyToClipboard(
                                        (detail?.dependencySnippets?.[activeSnippet] || '').replace(currentVersion, snippetVersion),
                                        activeSnippet
                                    )}
                                    className="absolute top-4 right-4 p-2 rounded-lg bg-gray-800 hover:bg-gray-700 text-gray-400 hover:text-white transition-all"
                                    title="Copy to clipboard"
                                >
                                    {copiedSnippet === activeSnippet
                                        ? <Check size={16} className="text-green-400" />
                                        : <Copy size={16} />}
                                </button>
                            </div>
                        </section>

                        {/* Detail Info */}
                        {detail && (
                            <section className="rounded-xl border border-gray-800 bg-gray-950/50 p-6 space-y-4">
                                <h2 className="font-bold text-lg flex items-center gap-2">
                                    <Package size={18} className="text-blue-400" />
                                    Version Details — {detail.version}
                                </h2>

                                <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
                                    <InfoCard label="Packaging" value={detail.packaging} />
                                    <InfoCard label="Direct Deps" value={String(detail.directDependencyCount || 0)} />
                                    <InfoCard label="Total Deps" value={String(detail.dependencyCount)} />
                                    <InfoCard label="Published" value={formatDate(detail.timestamp)} />
                                    <InfoCard
                                        label="License"
                                        value={detail.licenses?.length > 0 ? detail.licenses[0].name : 'Unknown'}
                                    />
                                </div>

                                {detail.description && (
                                    <div className="pt-4 border-t border-gray-800">
                                        <p className="text-gray-400 text-sm leading-relaxed">{detail.description}</p>
                                    </div>
                                )}
                            </section>
                        )}

                        {/* Used By Section */}
                        {usedByExpanded && (
                            <section className="rounded-xl border border-gray-800 bg-gray-950/50 overflow-hidden">
                                <div className="px-6 py-4 border-b border-gray-800 flex items-center justify-between">
                                    <h2 className="font-bold text-lg flex items-center gap-2">
                                        <Users size={18} className="text-cyan-400" />
                                        Used By
                                        {usedByTotal > 0 && (
                                            <span className="text-gray-500 text-sm font-normal">
                                                ({formatCount(usedByTotal)} artifacts)
                                            </span>
                                        )}
                                    </h2>
                                    <button
                                        onClick={() => setUsedByExpanded(false)}
                                        className="text-xs text-gray-500 hover:text-gray-300 transition-colors"
                                    >
                                        Close
                                    </button>
                                </div>

                                {usedByLoading ? (
                                    <div className="flex items-center justify-center py-12">
                                        <Loader2 className="w-6 h-6 animate-spin text-gray-600" />
                                    </div>
                                ) : (
                                    <>
                                        <div className="divide-y divide-gray-800/50">
                                            {usedByItems.map((item, i) => (
                                                <Link
                                                    key={`ub-${i}`}
                                                    href={`/artifact/${item.groupId}/${item.artifactId}`}
                                                    className="flex items-center justify-between px-6 py-3 hover:bg-gray-800/40 transition-colors group"
                                                >
                                                    <div className="min-w-0 flex-1">
                                                        <div className="flex items-center gap-2">
                                                            <Package size={14} className="text-cyan-400 shrink-0" />
                                                            <span className="text-sm font-medium text-gray-200 group-hover:text-cyan-300 transition-colors truncate">
                                                                {item.artifactId}
                                                            </span>
                                                        </div>
                                                        <p className="text-[11px] text-gray-600 font-mono truncate ml-6">{item.groupId}</p>
                                                    </div>
                                                    <div className="flex items-center gap-3 shrink-0">
                                                        {item.latestVersion && (
                                                            <span className="text-[10px] font-mono text-gray-500">{item.latestVersion}</span>
                                                        )}
                                                        <ArrowRight size={12} className="text-gray-700 group-hover:text-cyan-400 transition-colors" />
                                                    </div>
                                                </Link>
                                            ))}
                                        </div>

                                        {/* Pagination */}
                                        {usedByTotal > 10 && (
                                            <div className="px-6 py-3 border-t border-gray-800 flex items-center justify-between">
                                                <button
                                                    onClick={() => setUsedByPage(Math.max(0, usedByPage - 1))}
                                                    disabled={usedByPage === 0}
                                                    className="px-3 py-1.5 rounded-lg text-xs font-medium bg-gray-800 border border-gray-700 hover:bg-gray-700 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                                                >
                                                    Previous
                                                </button>
                                                <span className="text-xs text-gray-500">
                                                    Page {usedByPage + 1} of {Math.ceil(usedByTotal / 10)}
                                                </span>
                                                <button
                                                    onClick={() => setUsedByPage(usedByPage + 1)}
                                                    disabled={(usedByPage + 1) * 10 >= usedByTotal}
                                                    className="px-3 py-1.5 rounded-lg text-xs font-medium bg-gray-800 border border-gray-700 hover:bg-gray-700 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                                                >
                                                    Next
                                                </button>
                                            </div>
                                        )}
                                    </>
                                )}
                            </section>
                        )}

                        {/* Security & Version Intelligence — moved above Badges per PR_Review suggestion */}
                        <div className="space-y-6">
                            <VulnerabilityPanel
                                groupId={info.groupId}
                                artifactId={info.artifactId}
                                version={currentVersion}
                            />

                            <VulnerabilityTrendChart
                                groupId={info.groupId}
                                artifactId={info.artifactId}
                            />
                        </div>

                        {/* Badges Section */}
                        <section className="rounded-xl border border-gray-800 bg-gray-950/50 overflow-hidden">
                            <div className="px-6 py-4 border-b border-gray-800 flex items-center gap-2">
                                <ImageIcon size={18} className="text-amber-400" />
                                <h2 className="font-bold text-lg">Badges</h2>
                            </div>
                            <div className="p-6 space-y-8">
                                {/* Version Badge */}
                                <div>
                                    <h3 className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-3">Version Badge</h3>
                                    <div className="flex items-center gap-4 mb-4">
                                        <span className="text-xs text-gray-500">Preview:</span>
                                        {/* eslint-disable-next-line @next/next/no-img-element */}
                                        <img
                                            src={badgeUrl}
                                            alt={`Maven version badge for ${info.artifactId}`}
                                            className="h-5"
                                        />
                                    </div>
                                    <div className="space-y-3">
                                        {(Object.entries(badgeSnippets) as [string, string][]).map(([format, code]) => (
                                            <div key={format}>
                                                <div className="flex items-center justify-between mb-1">
                                                    <span className="text-xs font-medium text-gray-400 uppercase tracking-wider">{format}</span>
                                                    <button
                                                        onClick={() => copyBadgeCode(code, format)}
                                                        className="text-xs text-gray-500 hover:text-white flex items-center gap-1 transition-colors"
                                                    >
                                                        {copiedBadge === format ? (
                                                            <><Check size={12} className="text-green-400" /> Copied</>
                                                        ) : (
                                                            <><Copy size={12} /> Copy</>
                                                        )}
                                                    </button>
                                                </div>
                                                <pre className="px-4 py-2.5 rounded-lg bg-gray-900 border border-gray-800 text-xs font-mono text-gray-400 overflow-x-auto whitespace-pre-wrap break-all">
                                                    {code}
                                                </pre>
                                            </div>
                                        ))}
                                    </div>
                                </div>

                                {/* Security Badge — surfaces the /badge.svg API in the UI */}
                                <div className="border-t border-gray-800 pt-6">
                                    <h3 className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-3 flex items-center gap-1.5">
                                        <Shield size={12} className="text-green-400" />
                                        Security Badge
                                    </h3>
                                    <div className="flex items-center gap-4 mb-4">
                                        <span className="text-xs text-gray-500">Preview:</span>
                                        {/* eslint-disable-next-line @next/next/no-img-element */}
                                        <img
                                            src={securityBadgeUrl}
                                            alt={`Security badge for ${info.artifactId}`}
                                            className="h-5"
                                        />
                                    </div>
                                    <div className="space-y-3">
                                        {(Object.entries(securityBadgeSnippets) as [string, string][]).map(([format, code]) => (
                                            <div key={`sec-${format}`}>
                                                <div className="flex items-center justify-between mb-1">
                                                    <span className="text-xs font-medium text-gray-400 uppercase tracking-wider">{format}</span>
                                                    <button
                                                        onClick={() => copyBadgeCode(code, `sec-${format}`)}
                                                        className="text-xs text-gray-500 hover:text-white flex items-center gap-1 transition-colors"
                                                    >
                                                        {copiedBadge === `sec-${format}` ? (
                                                            <><Check size={12} className="text-green-400" /> Copied</>
                                                        ) : (
                                                            <><Copy size={12} /> Copy</>
                                                        )}
                                                    </button>
                                                </div>
                                                <pre className="px-4 py-2.5 rounded-lg bg-gray-900 border border-gray-800 text-xs font-mono text-gray-400 overflow-x-auto whitespace-pre-wrap break-all">
                                                    {code}
                                                </pre>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            </div>
                        </section>
                    </div>

                    {/* RIGHT: Versions List */}
                    <div className="space-y-6">
                        <section className="rounded-xl border border-gray-800 bg-gray-950/50 overflow-hidden">
                            <div className="px-5 py-4 border-b border-gray-800">
                                <h2 className="font-bold text-lg flex items-center gap-2">
                                    <Tag size={18} className="text-blue-400" />
                                    Versions
                                </h2>
                                {/* Search + filter */}
                                <div className="mt-3 space-y-2">
                                    <div className="relative">
                                        <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-600" />
                                        <input
                                            type="text"
                                            placeholder="Filter versions..."
                                            value={versionFilter}
                                            onChange={(e) => setVersionFilter(e.target.value)}
                                            className="w-full pl-9 pr-3 py-2 rounded-lg bg-gray-900 border border-gray-800 text-sm text-gray-300 placeholder-gray-600 focus:border-blue-500 focus:outline-none transition-colors"
                                        />
                                    </div>
                                    <label className="flex items-center gap-2 text-xs text-gray-500 cursor-pointer">
                                        <input
                                            type="checkbox"
                                            checked={showPreRelease}
                                            onChange={(e) => setShowPreRelease(e.target.checked)}
                                            className="accent-blue-500"
                                        />
                                        Show pre-release versions
                                    </label>
                                </div>
                            </div>

                            <div className="divide-y divide-gray-800/50 max-h-[500px] overflow-y-auto">
                                {displayVersions.map((v) => (
                                    <VersionRow
                                        key={v.version}
                                        v={v}
                                        groupId={info.groupId}
                                        artifactId={info.artifactId}
                                        isActive={v.version === currentVersion}
                                    />
                                ))}
                                {filteredVersions.length === 0 && (
                                    <div className="px-5 py-8 text-center text-gray-600 text-sm">
                                        No versions match your filter.
                                    </div>
                                )}
                            </div>

                            {filteredVersions.length > 10 && (
                                <button
                                    onClick={() => setShowAllVersions(!showAllVersions)}
                                    className="w-full px-5 py-3 text-sm text-blue-400 hover:text-blue-300 hover:bg-gray-900/50 border-t border-gray-800 flex items-center justify-center gap-1 transition-colors"
                                >
                                    {showAllVersions ? (
                                        <>Show less <ChevronUp size={14} /></>
                                    ) : (
                                        <>Show all {filteredVersions.length} versions <ChevronDown size={14} /></>
                                    )}
                                </button>
                            )}
                        </section>

                        {/* License Section */}
                        {info.licenses?.length > 0 && (
                            <section className="rounded-xl border border-gray-800 bg-gray-950/50 p-5">
                                <h3 className="text-sm font-bold text-gray-400 uppercase tracking-wider mb-3 flex items-center gap-2">
                                    <Shield size={14} className="text-purple-400" />
                                    License
                                </h3>
                                {info.licenses.map((lic, i) => (
                                    <div key={i} className="text-sm">
                                        {lic.url ? (
                                            <a
                                                href={lic.url}
                                                target="_blank"
                                                rel="noopener noreferrer"
                                                className="text-blue-400 hover:underline flex items-center gap-1.5"
                                            >
                                                {lic.name} <ExternalLink size={12} />
                                            </a>
                                        ) : (
                                            <span className="text-gray-300">{lic.name}</span>
                                        )}
                                    </div>
                                ))}
                            </section>
                        )}
                    </div>
                </div>
            </main>

            {/* Footer */}
            <footer className="border-t border-gray-800 mt-20 py-8 text-center text-gray-500 text-sm">
                <p>© 2026 Modern Maven. All rights reserved.</p>
            </footer>
        </div>
    );
}

// ─── Sub-components ────────────────────────────────────────────

function VersionRow({
    v,
    groupId,
    artifactId,
    isActive,
}: {
    v: ArtifactVersion;
    groupId: string;
    artifactId: string;
    isActive: boolean;
}) {
    // Derive safety indicator from version data
    const isSafe = v.vulnerabilityCount === 0 || v.vulnerabilityCount === undefined || v.vulnerabilityCount === null;
    const hasVulns = v.vulnerabilityCount !== undefined && v.vulnerabilityCount !== null && v.vulnerabilityCount > 0;

    return (
        <Link
            href={`/artifact/${groupId}/${artifactId}/${v.version}`}
            className={`flex items-center justify-between px-5 py-3 text-sm hover:bg-gray-800/40 transition-colors group ${isActive ? 'bg-blue-950/30 border-l-2 border-l-blue-500' : ''
                }`}
        >
            <div className="flex items-center gap-2 min-w-0">
                <span className={`font-mono font-medium truncate ${isActive ? 'text-blue-300' : 'text-gray-200'}`}>
                    {v.version}
                </span>
                {v.isRecommended && (
                    <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-green-900/40 text-green-300 border border-green-800/50 flex-shrink-0">
                        REC
                    </span>
                )}
                {!v.isRelease && (
                    <span className="px-1.5 py-0.5 rounded text-[10px] font-medium bg-yellow-900/30 text-yellow-400 border border-yellow-800/40 flex-shrink-0">
                        PRE
                    </span>
                )}
            </div>
            <div className="flex items-center gap-2 flex-shrink-0 ml-2">
                {/* Security indicator per version */}
                {hasVulns ? (
                    <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-red-900/30 text-red-400 border border-red-800/40" title={`${v.vulnerabilityCount} vulnerabilities`}>
                        ⚠ {v.vulnerabilityCount}
                    </span>
                ) : isSafe && v.isRelease ? (
                    <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-green-900/20 text-green-500 border border-green-900/40" title="No known vulnerabilities">
                        SAFE
                    </span>
                ) : null}
                <span className="text-xs text-gray-600 group-hover:text-gray-400 transition-colors">
                    {formatDate(v.timestamp)}
                </span>
            </div>
        </Link>
    );
}

function InfoCard({ label, value }: { label: string; value: string }) {
    return (
        <div className="rounded-lg bg-gray-900/60 border border-gray-800 p-3">
            <div className="text-[10px] uppercase tracking-wider text-gray-500 font-medium mb-1">{label}</div>
            <div className="text-sm font-bold text-gray-200 truncate" title={value}>{value}</div>
        </div>
    );
}
