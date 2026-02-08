"use client";

import React, { useState } from 'react';
import GraphViewer from '@/components/GraphViewer';
import DependencyTable from '@/components/DependencyTable';
import { fetchDependencyGraph, DependencyNode } from '@/lib/api';
import { AlertCircle, Search, FileCode, Layers, List } from 'lucide-react';

export default function AnalyzePage() {
    const [mode, setMode] = useState<'coordinates' | 'pom'>('coordinates');
    const [viewMode, setViewMode] = useState<'graph' | 'list'>('graph');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [graph, setGraph] = useState<DependencyNode | null>(null);

    // Form inputs
    const [groupId, setGroupId] = useState('');
    const [artifactId, setArtifactId] = useState('');
    const [version, setVersion] = useState('');
    const [pomContent, setPomContent] = useState('');

    const handleSearch = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);
        setError(null);
        // Do not clear graph immediately to avoid flicker, or do clear if distinct query.
        // setGraph(null);

        try {
            const data = await fetchDependencyGraph({
                groupId: mode === 'coordinates' ? groupId : undefined,
                artifactId: mode === 'coordinates' ? artifactId : undefined,
                version: mode === 'coordinates' ? version : undefined,
                pomContent: mode === 'pom' ? pomContent : undefined
            });
            setGraph(data);
        } catch (err: any) {
            setError(err.message || "An unknown error occurred");
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="min-h-screen bg-black text-white p-8 pb-20 font-[family-name:var(--font-geist-sans)]">
            <header className="mb-12 max-w-7xl mx-auto flex items-center justify-between">
                <div>
                    <h1 className="text-3xl font-extrabold tracking-tighter bg-gradient-to-r from-blue-500 to-purple-600 bg-clip-text text-transparent">modernmvn.</h1>
                    <p className="text-gray-500 text-sm mt-1">Dependency Intelligence Platform</p>
                </div>
                <nav className="flex gap-4">
                    <a href="/" className="text-gray-400 hover:text-white transition-colors text-sm">Home</a>
                    <a href="/analyze" className="text-blue-400 font-bold border-b-2 border-blue-500 text-sm">Analyze</a>
                </nav>
            </header>

            <main className="max-w-7xl mx-auto flex flex-col gap-8">
                {/* Search Panel */}
                <div className="bg-gray-900 border border-gray-800 rounded-xl p-6 shadow-xl">
                    <div className="flex gap-4 mb-6 border-b border-gray-800 pb-4">
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
                    </div>

                    <form onSubmit={handleSearch} className="flex flex-col gap-6">
                        {mode === 'coordinates' ? (
                            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                                <div className="flex flex-col gap-2">
                                    <label className="text-xs font-semibold text-gray-500 uppercase">Group ID</label>
                                    <input
                                        type="text"
                                        value={groupId}
                                        onChange={e => setGroupId(e.target.value)}
                                        placeholder="org.springframework.boot"
                                        className="bg-gray-950 border border-gray-800 rounded-lg px-4 py-3 text-white focus:outline-none focus:border-blue-500 transition-colors"
                                        required
                                    />
                                </div>
                                <div className="flex flex-col gap-2">
                                    <label className="text-xs font-semibold text-gray-500 uppercase">Artifact ID</label>
                                    <input
                                        type="text"
                                        value={artifactId}
                                        onChange={e => setArtifactId(e.target.value)}
                                        placeholder="spring-boot-starter-web"
                                        className="bg-gray-950 border border-gray-800 rounded-lg px-4 py-3 text-white focus:outline-none focus:border-blue-500 transition-colors"
                                        required
                                    />
                                </div>
                                <div className="flex flex-col gap-2">
                                    <label className="text-xs font-semibold text-gray-500 uppercase">Version</label>
                                    <input
                                        type="text"
                                        value={version}
                                        onChange={e => setVersion(e.target.value)}
                                        placeholder="3.2.0"
                                        className="bg-gray-950 border border-gray-800 rounded-lg px-4 py-3 text-white focus:outline-none focus:border-blue-500 transition-colors"
                                        required
                                    />
                                </div>
                            </div>
                        ) : (
                            <div className="flex flex-col gap-2">
                                <label className="text-xs font-semibold text-gray-500 uppercase">POM XML Content</label>
                                <textarea
                                    value={pomContent}
                                    onChange={e => setPomContent(e.target.value)}
                                    placeholder="<project>...</project>"
                                    className="bg-gray-950 border border-gray-800 rounded-lg px-4 py-3 text-white font-mono text-sm h-40 focus:outline-none focus:border-blue-500 transition-colors resize-y"
                                    required
                                />
                            </div>
                        )}

                        <div className="flex justify-between items-center">
                            <div className="text-sm text-gray-500">
                                {loading ? 'Fetching resolution tree...' : graph ? 'Resolution complete.' : 'Ready to analyze.'}
                            </div>
                            <button
                                type="submit"
                                disabled={loading}
                                className="bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed text-white px-8 py-3 rounded-lg font-semibold transition-all shadow-lg shadow-blue-900/20 flex items-center gap-2"
                            >
                                {loading && <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white"></div>}
                                {loading ? 'Resolving...' : 'Visualize'}
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

                {/* View/Results Area */}
                {graph && (
                    <div className="flex flex-col gap-4">
                        <div className="flex gap-4 border-b border-gray-800 pb-2">
                            <button onClick={() => setViewMode('graph')} className={`flex items-center gap-2 px-4 py-2 text-sm font-medium ${viewMode === 'graph' ? 'text-blue-400 border-b-2 border-blue-500' : 'text-gray-500 hover:text-gray-300'}`}>
                                <Layers size={16} /> Graph View
                            </button>
                            <button onClick={() => setViewMode('list')} className={`flex items-center gap-2 px-4 py-2 text-sm font-medium ${viewMode === 'list' ? 'text-blue-400 border-b-2 border-blue-500' : 'text-gray-500 hover:text-gray-300'}`}>
                                <List size={16} /> Table View
                            </button>
                        </div>

                        <div className="flex-1 min-h-[600px]">
                            {viewMode === 'graph' ? <GraphViewer rootNode={graph} /> : <DependencyTable rootNode={graph} />}
                        </div>
                    </div>
                )}

                {!graph && !loading && !error && (
                    <div className="h-full border border-dashed border-gray-800 rounded-xl flex items-center justify-center text-gray-600 flex-col gap-4 p-12 min-h-[400px]">
                        <div className="w-16 h-16 rounded-full bg-gray-900 flex items-center justify-center">
                            <Layers className="text-gray-700" size={32} />
                        </div>
                        <p>Enter maven coordinates to generate a dependency graph.</p>
                    </div>
                )}
            </main>
        </div>
    );
}
