"use client";

import React, { useState, useEffect, useRef } from 'react';
import { useSearchParams, useRouter } from 'next/navigation';
import GraphViewer from '@/components/GraphViewer';
import DependencyTable from '@/components/DependencyTable';
import ConflictSummary from '@/components/ConflictSummary';
import {
    fetchDependencyGraph, uploadPomFile, fetchMultiModuleGraph,
    DependencyNode, MultiModuleResult, isMultiModuleResult
} from '@/lib/api';
import {
    AlertCircle, Search, FileCode, Layers, List, ClipboardList,
    Download, Share2, Upload, Plus, Trash2, ShieldAlert, Clock, Boxes
} from 'lucide-react';

import Link from 'next/link';

// Session timeout in minutes
const SESSION_TIMEOUT_MINUTES = 30;

export default function AnalyzePage() {
    const searchParams = useSearchParams();
    const router = useRouter();

    // ─── Mode & view state ──────────────────────────────────────
    const [mode, setMode] = useState<'coordinates' | 'pom' | 'upload'>('coordinates');
    const [viewMode, setViewMode] = useState<'graph' | 'list' | 'summary'>('graph');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [graph, setGraph] = useState<DependencyNode | null>(null);
    const [multiModuleResult, setMultiModuleResult] = useState<MultiModuleResult | null>(null);
    const [activeModuleIndex, setActiveModuleIndex] = useState<number | null>(null);

    // ─── Form inputs ────────────────────────────────────────────
    const [groupId, setGroupId] = useState('');
    const [artifactId, setArtifactId] = useState('');
    const [version, setVersion] = useState('');
    const [pomContent, setPomContent] = useState('');
    const [uploadedFile, setUploadedFile] = useState<File | null>(null);
    const [detectMultiModule, setDetectMultiModule] = useState(true);
    const fileInputRef = useRef<HTMLInputElement>(null);

    // ─── Custom repositories ────────────────────────────────────
    const [customRepos, setCustomRepos] = useState<string[]>([]);
    const [showRepoInput, setShowRepoInput] = useState(false);
    const [newRepoUrl, setNewRepoUrl] = useState('');

    // ─── Security & Session ─────────────────────────────────────
    const [showDisclaimer, setShowDisclaimer] = useState(false);
    const [sessionExpiry, setSessionExpiry] = useState<Date | null>(null);
    const [sessionWarning, setSessionWarning] = useState(false);

    // ─── URL sync on mount ──────────────────────────────────────
    useEffect(() => {
        // Support both short (g/a/v) and long (groupId/artifactId/version) param names
        const g = searchParams.get('g') || searchParams.get('groupId');
        const a = searchParams.get('a') || searchParams.get('artifactId');
        const v = searchParams.get('v') || searchParams.get('version');

        if (g && a && v) {
            setGroupId(g);
            setArtifactId(a);
            setVersion(v);
            doFetch(g, a, v);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [searchParams]);

    // ─── Session timeout logic ──────────────────────────────────
    useEffect(() => {
        if (!graph && !multiModuleResult) return;

        const expiry = new Date(Date.now() + SESSION_TIMEOUT_MINUTES * 60 * 1000);
        setSessionExpiry(expiry);
        setSessionWarning(false);

        // Warning 5 minutes before expiry
        const warningTimer = setTimeout(() => {
            setSessionWarning(true);
        }, (SESSION_TIMEOUT_MINUTES - 5) * 60 * 1000);

        // Cleanup on expiry
        const expiryTimer = setTimeout(() => {
            setGraph(null);
            setMultiModuleResult(null);
            setActiveModuleIndex(null);
            setSessionExpiry(null);
            setSessionWarning(false);
            setError('Session expired. Please re-analyze to continue.');
        }, SESSION_TIMEOUT_MINUTES * 60 * 1000);

        return () => {
            clearTimeout(warningTimer);
            clearTimeout(expiryTimer);
        };
    }, [graph, multiModuleResult]);

    // ─── Fetchers ───────────────────────────────────────────────
    const doFetch = async (g?: string, a?: string, v?: string, pom?: string) => {
        setLoading(true);
        setError(null);
        setMultiModuleResult(null);
        setActiveModuleIndex(null);
        try {
            const data = await fetchDependencyGraph({
                groupId: g,
                artifactId: a,
                version: v,
                pomContent: pom,
                customRepositories: customRepos.filter(r => r.trim()),
            });
            setGraph(data);
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : "An unknown error occurred");
        } finally {
            setLoading(false);
        }
    }

    const doUpload = async () => {
        if (!uploadedFile) return;
        setLoading(true);
        setError(null);
        setMultiModuleResult(null);
        setActiveModuleIndex(null);
        try {
            const result = await uploadPomFile(
                uploadedFile,
                customRepos.filter(r => r.trim()),
                detectMultiModule
            );

            if (isMultiModuleResult(result)) {
                setMultiModuleResult(result);
                setGraph(result.mergedTree);
            } else {
                setGraph(result as DependencyNode);
            }
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : "An unknown error occurred");
        } finally {
            setLoading(false);
        }
    }

    const doMultiModulePom = async () => {
        setLoading(true);
        setError(null);
        setMultiModuleResult(null);
        setActiveModuleIndex(null);
        try {
            const result = await fetchMultiModuleGraph(pomContent, customRepos.filter(r => r.trim()));
            setMultiModuleResult(result);
            setGraph(result.mergedTree);
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : "An unknown error occurred");
        } finally {
            setLoading(false);
        }
    }

    const handleSearch = async (e: React.FormEvent) => {
        e.preventDefault();

        if (mode === 'coordinates') {
            const params = new URLSearchParams();
            params.set('g', groupId);
            params.set('a', artifactId);
            params.set('v', version);
            router.push(`/analyze?${params.toString()}`);
            doFetch(groupId, artifactId, version);
        } else if (mode === 'pom') {
            if (detectMultiModule) {
                doMultiModulePom();
            } else {
                doFetch(undefined, undefined, undefined, pomContent);
            }
        } else if (mode === 'upload') {
            doUpload();
        }
    };

    // ─── Custom repos management ────────────────────────────────
    const addRepo = () => {
        const url = newRepoUrl.trim();
        if (!url) return;
        if (!url.startsWith('https://')) {
            setError('Custom repository URLs must use HTTPS.');
            return;
        }
        if (customRepos.length >= 5) {
            setError('Maximum 5 custom repositories allowed.');
            return;
        }
        setCustomRepos([...customRepos, url]);
        setNewRepoUrl('');
    };

    const removeRepo = (idx: number) => {
        setCustomRepos(customRepos.filter((_, i) => i !== idx));
    };

    // ─── File handling ──────────────────────────────────────────
    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file) return;

        if (file.size > 512 * 1024) {
            setError('File exceeds maximum size of 512 KB.');
            return;
        }
        if (!file.name.endsWith('.xml') && !file.name.endsWith('.pom')) {
            setError('Only .xml and .pom files are accepted.');
            return;
        }
        setUploadedFile(file);
        setError(null);
    };

    const handleDrop = (e: React.DragEvent) => {
        e.preventDefault();
        const file = e.dataTransfer.files?.[0];
        if (file) {
            if (file.size > 512 * 1024) {
                setError('File exceeds maximum size of 512 KB.');
                return;
            }
            if (!file.name.endsWith('.xml') && !file.name.endsWith('.pom')) {
                setError('Only .xml and .pom files are accepted.');
                return;
            }
            setUploadedFile(file);
            setError(null);
        }
    };

    // ─── Export & Share ─────────────────────────────────────────
    const handleExport = () => {
        const data = multiModuleResult || graph;
        if (!data) return;
        const jsonString = JSON.stringify(data, null, 2);
        const blob = new Blob([jsonString], { type: "application/json" });
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        link.download = `dependency-graph-${groupId || 'pom'}-${artifactId || 'upload'}-${version || 'analysis'}.json`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    };

    const handleShare = () => {
        if (mode === 'coordinates') {
            navigator.clipboard.writeText(window.location.href);
            alert("Link copied to clipboard!");
        } else {
            alert("Sharing is only available for coordinate-based analysis.");
        }
    };

    // Determine active graph for display
    const activeGraph = activeModuleIndex !== null && multiModuleResult
        ? multiModuleResult.modules[activeModuleIndex]?.dependencyTree
        : graph;

    return (
        <div className="min-h-screen bg-black text-white p-8 pb-20 font-[family-name:var(--font-geist-sans)]">
            <header className="mb-12 max-w-7xl mx-auto flex items-center justify-between">
                <div>
                    <h1 className="text-3xl font-extrabold tracking-tighter bg-gradient-to-r from-blue-500 to-purple-600 bg-clip-text text-transparent">modernmvn.</h1>
                    <p className="text-gray-500 text-sm mt-1">Dependency Intelligence Platform</p>
                </div>
                <nav className="flex gap-4 items-center">
                    <Link href="/" className="text-gray-400 hover:text-white transition-colors text-sm">Home</Link>
                    <Link href="/analyze" className="text-blue-400 font-bold border-b-2 border-blue-500 text-sm">Analyze</Link>
                    <button onClick={() => setShowDisclaimer(!showDisclaimer)} className="text-gray-500 hover:text-yellow-400 transition-colors" title="Security & Limits">
                        <ShieldAlert size={18} />
                    </button>
                </nav>
            </header>

            <main className="max-w-7xl mx-auto flex flex-col gap-8">
                {/* Security Disclaimer */}
                {showDisclaimer && (
                    <div className="bg-yellow-900/10 border border-yellow-800/40 rounded-xl p-5 text-sm text-yellow-300/80">
                        <div className="flex items-center gap-2 font-bold text-yellow-400 mb-3">
                            <ShieldAlert size={16} /> Security & Limits
                        </div>
                        <ul className="list-disc pl-5 space-y-1">
                            <li>Uploaded POM files are processed <strong>in-memory only</strong> and never stored on disk.</li>
                            <li>Maximum POM file size: <strong>512 KB</strong>.</li>
                            <li>Custom repository URLs must use <strong>HTTPS only</strong>. Maximum <strong>5</strong> custom repos.</li>
                            <li>Analysis results are cached for <strong>24 hours</strong> (coordinate-based only).</li>
                            <li>Sessions expire after <strong>{SESSION_TIMEOUT_MINUTES} minutes</strong> of inactivity.</li>
                            <li>Do <strong>not</strong> upload POMs containing credentials, tokens, or proprietary repository URLs with embedded auth.</li>
                        </ul>
                    </div>
                )}

                {/* Session Warning */}
                {sessionWarning && (
                    <div className="bg-orange-900/20 border border-orange-700/40 text-orange-400 px-5 py-3 rounded-xl flex items-center gap-3 text-sm animate-pulse">
                        <Clock size={18} />
                        <span>Your session will expire in 5 minutes. Results will be cleared automatically.</span>
                    </div>
                )}

                {/* Search Panel */}
                <div className="bg-gray-900 border border-gray-800 rounded-xl p-6 shadow-xl">
                    {/* Mode Tabs */}
                    <div className="flex gap-3 mb-6 border-b border-gray-800 pb-4 flex-wrap">
                        <button
                            onClick={() => setMode('coordinates')}
                            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors ${mode === 'coordinates' ? 'bg-blue-600 text-white' : 'text-gray-400 hover:bg-gray-800'}`}
                        >
                            <Search size={16} /> Coordinates
                        </button>
                        <button
                            onClick={() => setMode('pom')}
                            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors ${mode === 'pom' ? 'bg-blue-600 text-white' : 'text-gray-400 hover:bg-gray-800'}`}
                        >
                            <FileCode size={16} /> POM Snippet
                        </button>
                        <button
                            onClick={() => setMode('upload')}
                            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors ${mode === 'upload' ? 'bg-blue-600 text-white' : 'text-gray-400 hover:bg-gray-800'}`}
                        >
                            <Upload size={16} /> Upload POM
                        </button>
                    </div>

                    <form onSubmit={handleSearch} className="flex flex-col gap-6">
                        {/* Coordinates Mode */}
                        {mode === 'coordinates' && (
                            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                                <div className="flex flex-col gap-2">
                                    <label className="text-xs font-semibold text-gray-500 uppercase">Group ID</label>
                                    <input type="text" value={groupId} onChange={e => setGroupId(e.target.value)}
                                        placeholder="org.springframework.boot"
                                        className="bg-gray-950 border border-gray-800 rounded-lg px-4 py-3 text-white focus:outline-none focus:border-blue-500 transition-colors" required />
                                </div>
                                <div className="flex flex-col gap-2">
                                    <label className="text-xs font-semibold text-gray-500 uppercase">Artifact ID</label>
                                    <input type="text" value={artifactId} onChange={e => setArtifactId(e.target.value)}
                                        placeholder="spring-boot-starter-web"
                                        className="bg-gray-950 border border-gray-800 rounded-lg px-4 py-3 text-white focus:outline-none focus:border-blue-500 transition-colors" required />
                                </div>
                                <div className="flex flex-col gap-2">
                                    <label className="text-xs font-semibold text-gray-500 uppercase">Version</label>
                                    <input type="text" value={version} onChange={e => setVersion(e.target.value)}
                                        placeholder="3.2.0"
                                        className="bg-gray-950 border border-gray-800 rounded-lg px-4 py-3 text-white focus:outline-none focus:border-blue-500 transition-colors" required />
                                </div>
                            </div>
                        )}

                        {/* POM Snippet Mode */}
                        {mode === 'pom' && (
                            <div className="flex flex-col gap-4">
                                <div className="flex flex-col gap-2">
                                    <label className="text-xs font-semibold text-gray-500 uppercase">POM XML Content</label>
                                    <textarea
                                        value={pomContent} onChange={e => setPomContent(e.target.value)}
                                        placeholder="<project>...</project>"
                                        className="bg-gray-950 border border-gray-800 rounded-lg px-4 py-3 text-white font-mono text-sm h-40 focus:outline-none focus:border-blue-500 transition-colors resize-y"
                                        required />
                                </div>
                                <label className="flex items-center gap-2 text-sm text-gray-400 cursor-pointer">
                                    <input type="checkbox" checked={detectMultiModule}
                                        onChange={e => setDetectMultiModule(e.target.checked)}
                                        className="rounded bg-gray-800 border-gray-700 text-blue-500 focus:ring-blue-500" />
                                    <Boxes size={14} /> Detect multi-module project
                                </label>
                            </div>
                        )}

                        {/* File Upload Mode */}
                        {mode === 'upload' && (
                            <div className="flex flex-col gap-4">
                                <div
                                    onDrop={handleDrop}
                                    onDragOver={e => e.preventDefault()}
                                    onClick={() => fileInputRef.current?.click()}
                                    className="border-2 border-dashed border-gray-700 rounded-xl p-8 text-center cursor-pointer hover:border-blue-500/50 hover:bg-blue-500/5 transition-all"
                                >
                                    <input ref={fileInputRef} type="file" accept=".xml,.pom" onChange={handleFileChange} className="hidden" />
                                    {uploadedFile ? (
                                        <div className="flex flex-col items-center gap-2">
                                            <FileCode className="text-blue-400" size={32} />
                                            <p className="text-white font-medium">{uploadedFile.name}</p>
                                            <p className="text-gray-500 text-sm">{(uploadedFile.size / 1024).toFixed(1)} KB</p>
                                        </div>
                                    ) : (
                                        <div className="flex flex-col items-center gap-2 text-gray-500">
                                            <Upload size={32} />
                                            <p>Drag & drop your <strong>pom.xml</strong> here, or click to browse</p>
                                            <p className="text-xs text-gray-600">Accepts .xml and .pom files up to 512 KB</p>
                                        </div>
                                    )}
                                </div>
                                <label className="flex items-center gap-2 text-sm text-gray-400 cursor-pointer">
                                    <input type="checkbox" checked={detectMultiModule}
                                        onChange={e => setDetectMultiModule(e.target.checked)}
                                        className="rounded bg-gray-800 border-gray-700 text-blue-500 focus:ring-blue-500" />
                                    <Boxes size={14} /> Detect multi-module project
                                </label>
                            </div>
                        )}

                        {/* Custom Repositories */}
                        <div className="border-t border-gray-800 pt-4">
                            <button type="button" onClick={() => setShowRepoInput(!showRepoInput)}
                                className="text-sm text-gray-400 hover:text-gray-200 transition-colors flex items-center gap-2">
                                <Plus size={14} /> Add custom repository (optional)
                            </button>

                            {showRepoInput && (
                                <div className="mt-3 flex flex-col gap-3">
                                    <div className="flex gap-2">
                                        <input type="url" value={newRepoUrl} onChange={e => setNewRepoUrl(e.target.value)}
                                            placeholder="https://your-nexus-server.com/repository/maven-public/"
                                            className="flex-1 bg-gray-950 border border-gray-800 rounded-lg px-4 py-2 text-white text-sm focus:outline-none focus:border-blue-500 transition-colors" />
                                        <button type="button" onClick={addRepo}
                                            className="px-4 py-2 bg-gray-800 hover:bg-gray-700 border border-gray-700 rounded-lg text-sm transition-colors">
                                            Add
                                        </button>
                                    </div>
                                    <p className="text-xs text-gray-600">HTTPS only. Max 5 repositories. Maven Central is always included.</p>

                                    {customRepos.length > 0 && (
                                        <div className="flex flex-col gap-1">
                                            {customRepos.map((repo, idx) => (
                                                <div key={idx} className="flex items-center justify-between bg-gray-950 border border-gray-800 rounded px-3 py-2 text-sm">
                                                    <span className="text-gray-300 font-mono text-xs truncate">{repo}</span>
                                                    <button type="button" onClick={() => removeRepo(idx)} className="text-red-400 hover:text-red-300 ml-2">
                                                        <Trash2 size={14} />
                                                    </button>
                                                </div>
                                            ))}
                                        </div>
                                    )}
                                </div>
                            )}
                        </div>

                        {/* Submit */}
                        <div className="flex justify-between items-center">
                            <div className="text-sm text-gray-500">
                                {loading ? 'Fetching resolution tree...' : graph ? 'Resolution complete.' : 'Ready to analyze.'}
                            </div>
                            <button type="submit" disabled={loading || (mode === 'upload' && !uploadedFile)}
                                className="bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed text-white px-8 py-3 rounded-lg font-semibold transition-all shadow-lg shadow-blue-900/20 flex items-center gap-2">
                                {loading && <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white"></div>}
                                {loading ? 'Resolving...' : 'Analyze'}
                            </button>
                        </div>
                    </form>
                </div>

                {/* Status Messages */}
                {error && (
                    <div className="bg-red-900/20 border border-red-900/50 text-red-400 px-6 py-4 rounded-xl flex items-center gap-3">
                        <AlertCircle size={20} />
                        <span>{error}</span>
                    </div>
                )}

                {/* Multi-Module Module Selector */}
                {multiModuleResult && multiModuleResult.isMultiModule && (
                    <div className="bg-gray-900 border border-gray-800 rounded-xl p-4">
                        <h3 className="text-sm font-bold text-purple-400 mb-3 flex items-center gap-2">
                            <Boxes size={16} />
                            Multi-Module Project: {multiModuleResult.parentGroupId}:{multiModuleResult.parentArtifactId}:{multiModuleResult.parentVersion}
                        </h3>
                        <div className="flex gap-2 flex-wrap">
                            <button
                                onClick={() => { setActiveModuleIndex(null); setGraph(multiModuleResult.mergedTree); }}
                                className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${activeModuleIndex === null ? 'bg-purple-600 text-white' : 'bg-gray-800 text-gray-400 hover:bg-gray-700'}`}
                            >
                                All Modules (Merged)
                            </button>
                            {multiModuleResult.modules.map((mod, idx) => (
                                <button key={idx}
                                    onClick={() => { setActiveModuleIndex(idx); setGraph(mod.dependencyTree); }}
                                    className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors flex items-center gap-1.5 ${activeModuleIndex === idx ? 'bg-purple-600 text-white' : 'bg-gray-800 text-gray-400 hover:bg-gray-700'}`}
                                >
                                    {mod.dependencyTree.resolutionStatus === 'LOCAL' && (
                                        <span className="w-2 h-2 rounded-full bg-cyan-500 inline-block" title="Local module"></span>
                                    )}
                                    {mod.moduleName}
                                </button>
                            ))}
                        </div>
                    </div>
                )}

                {/* View/Results Area */}
                {activeGraph ? (
                    <div className="flex flex-col gap-4">
                        <div className="flex justify-between border-b border-gray-800 pb-2 flex-wrap gap-2">
                            <div className="flex gap-4">
                                <button onClick={() => setViewMode('graph')} className={`flex items-center gap-2 px-4 py-2 text-sm font-medium ${viewMode === 'graph' ? 'text-blue-400 border-b-2 border-blue-500' : 'text-gray-500 hover:text-gray-300'}`}>
                                    <Layers size={16} /> Graph View
                                </button>
                                <button onClick={() => setViewMode('list')} className={`flex items-center gap-2 px-4 py-2 text-sm font-medium ${viewMode === 'list' ? 'text-blue-400 border-b-2 border-blue-500' : 'text-gray-500 hover:text-gray-300'}`}>
                                    <List size={16} /> Table View
                                </button>
                                <button onClick={() => setViewMode('summary')} className={`flex items-center gap-2 px-4 py-2 text-sm font-medium ${viewMode === 'summary' ? 'text-blue-400 border-b-2 border-blue-500' : 'text-gray-500 hover:text-gray-300'}`}>
                                    <ClipboardList size={16} /> Conflict Summary
                                </button>
                            </div>

                            <div className="flex gap-2">
                                <button onClick={handleShare} className="flex items-center gap-2 px-3 py-1 bg-gray-800 hover:bg-gray-700 rounded text-xs text-gray-300 border border-gray-700 transition">
                                    <Share2 size={14} /> Share Link
                                </button>
                                <button onClick={handleExport} className="flex items-center gap-2 px-3 py-1 bg-gray-800 hover:bg-gray-700 rounded text-xs text-gray-300 border border-gray-700 transition">
                                    <Download size={14} /> Export JSON
                                </button>
                            </div>
                        </div>

                        <div className="flex-1 min-h-[600px]">
                            {viewMode === 'graph' && <GraphViewer rootNode={activeGraph} />}
                            {viewMode === 'list' && <DependencyTable rootNode={activeGraph} />}
                            {viewMode === 'summary' && <ConflictSummary rootNode={activeGraph} />}
                        </div>
                    </div>
                ) : (
                    !loading && (
                        <div className="h-full border border-dashed border-gray-800 rounded-xl flex items-center justify-center text-gray-600 flex-col gap-4 p-12 min-h-[400px]">
                            <div className="w-16 h-16 rounded-full bg-gray-900 flex items-center justify-center">
                                <Layers className="text-gray-700" size={32} />
                            </div>
                            <p>Enter maven coordinates, paste a POM, or upload a file to analyze dependencies.</p>
                        </div>
                    )
                )}
            </main>
        </div>
    );
}
