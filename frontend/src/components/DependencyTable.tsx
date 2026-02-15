import React, { useState, useMemo } from 'react';
import { DependencyNode } from '@/lib/api';
import { Package, AlertTriangle, FileQuestion, ArrowUpDown, ArrowUp, ArrowDown, Search } from 'lucide-react';

interface DependencyTableProps {
    rootNode: DependencyNode;
}

// Helper to get unique flattened dependencies
const getUniqueDependencies = (node: DependencyNode) => {
    const map = new Map<string, DependencyNode>();

    const traverse = (n: DependencyNode) => {
        const key = `${n.groupId}:${n.artifactId}:${n.version}`;
        if (!map.has(key)) {
            map.set(key, n);
            n.children.forEach(traverse);
        }
    };
    traverse(node);
    return Array.from(map.values());
}

type SortKey = keyof DependencyNode;
type SortDirection = 'asc' | 'desc';

interface SortConfig {
    key: SortKey;
    direction: SortDirection;
}

export default function DependencyTable({ rootNode }: DependencyTableProps) {
    const [sortConfig, setSortConfig] = useState<SortConfig | null>(null);
    const [filters, setFilters] = useState({
        artifactId: '',
        groupId: '',
        version: '',
        scope: '',
        resolutionStatus: ''
    });

    // Memoize the base unique list so we don't re-traverse on every render
    const uniqueDependencies = useMemo(() => getUniqueDependencies(rootNode), [rootNode]);

    const handleSort = (key: SortKey) => {
        let direction: SortDirection = 'asc';
        if (sortConfig && sortConfig.key === key && sortConfig.direction === 'asc') {
            direction = 'desc';
        }
        setSortConfig({ key, direction });
    };

    const handleFilterChange = (key: string, value: string) => {
        setFilters(prev => ({ ...prev, [key]: value }));
    };

    const processedDependencies = useMemo(() => {
        let items = [...uniqueDependencies];

        // 1. Filter
        items = items.filter(item => {
            return (
                item.artifactId.toLowerCase().includes(filters.artifactId.toLowerCase()) &&
                item.groupId.toLowerCase().includes(filters.groupId.toLowerCase()) &&
                item.version.toLowerCase().includes(filters.version.toLowerCase()) &&
                (item.scope || 'compile').toLowerCase().includes(filters.scope.toLowerCase()) &&
                (item.resolutionStatus || '').toLowerCase().includes(filters.resolutionStatus.toLowerCase())
            );
        });

        // 2. Sort
        if (sortConfig) {
            items.sort((a, b) => {
                // Handle potential undefined/null values safely
                const aValue = String(a[sortConfig.key] || '').toLowerCase();
                const bValue = String(b[sortConfig.key] || '').toLowerCase();

                if (aValue < bValue) {
                    return sortConfig.direction === 'asc' ? -1 : 1;
                }
                if (aValue > bValue) {
                    return sortConfig.direction === 'asc' ? 1 : -1;
                }
                return 0;
            });
        }

        return items;
    }, [uniqueDependencies, filters, sortConfig]);

    const renderSortIcon = (key: SortKey) => {
        if (sortConfig?.key !== key) return <ArrowUpDown size={14} className="text-gray-600 opacity-0 group-hover:opacity-100 transition-opacity" />;
        return sortConfig.direction === 'asc'
            ? <ArrowUp size={14} className="text-blue-400" />
            : <ArrowDown size={14} className="text-blue-400" />;
    };

    return (
        <div className="space-y-4">
            {/* Filter Summary / Clear (Optional, but good UX if filters are active) */}
            {Object.values(filters).some(f => f) && (
                <div className="flex justify-end">
                    <button
                        onClick={() => setFilters({ artifactId: '', groupId: '', version: '', scope: '', resolutionStatus: '' })}
                        className="text-xs text-blue-400 hover:text-blue-300 flex items-center gap-1"
                    >
                        Clear Filters ✕
                    </button>
                </div>
            )}

            <div className="overflow-x-auto rounded-lg border border-gray-800 bg-gray-900/50">
                <table className="w-full text-left text-sm text-gray-400">
                    <thead className="bg-gray-900 text-xs uppercase text-gray-500">
                        {/* Header Row for Sorting */}
                        <tr>
                            <th className="px-6 py-3 font-medium cursor-pointer group hover:bg-gray-800/50 transition-colors" onClick={() => handleSort('artifactId')}>
                                <div className="flex items-center gap-2">
                                    Artifact {renderSortIcon('artifactId')}
                                </div>
                            </th>
                            <th className="px-6 py-3 font-medium cursor-pointer group hover:bg-gray-800/50 transition-colors" onClick={() => handleSort('groupId')}>
                                <div className="flex items-center gap-2">
                                    Group {renderSortIcon('groupId')}
                                </div>
                            </th>
                            <th className="px-6 py-3 font-medium cursor-pointer group hover:bg-gray-800/50 transition-colors" onClick={() => handleSort('version')}>
                                <div className="flex items-center gap-2">
                                    Version {renderSortIcon('version')}
                                </div>
                            </th>
                            <th className="px-6 py-3 font-medium cursor-pointer group hover:bg-gray-800/50 transition-colors" onClick={() => handleSort('scope')}>
                                <div className="flex items-center gap-2">
                                    Scope {renderSortIcon('scope')}
                                </div>
                            </th>
                            <th className="px-6 py-3 font-medium cursor-pointer group hover:bg-gray-800/50 transition-colors" onClick={() => handleSort('resolutionStatus')}>
                                <div className="flex items-center gap-2">
                                    Status {renderSortIcon('resolutionStatus')}
                                </div>
                            </th>
                        </tr>
                        {/* Filter Row */}
                        <tr className="bg-gray-900/50 border-b border-gray-800">
                            <th className="px-6 py-2">
                                <div className="relative">
                                    <Search size={12} className="absolute left-2 top-1/2 -translate-y-1/2 text-gray-600" />
                                    <input
                                        type="text"
                                        placeholder="Filter..."
                                        className="w-full bg-gray-950 border border-gray-800 rounded px-2 py-1 pl-7 text-[10px] text-gray-300 focus:border-blue-500 outline-none"
                                        value={filters.artifactId}
                                        onChange={(e) => handleFilterChange('artifactId', e.target.value)}
                                    />
                                </div>
                            </th>
                            <th className="px-6 py-2">
                                <div className="relative">
                                    <Search size={12} className="absolute left-2 top-1/2 -translate-y-1/2 text-gray-600" />
                                    <input
                                        type="text"
                                        placeholder="Filter..."
                                        className="w-full bg-gray-950 border border-gray-800 rounded px-2 py-1 pl-7 text-[10px] text-gray-300 focus:border-blue-500 outline-none"
                                        value={filters.groupId}
                                        onChange={(e) => handleFilterChange('groupId', e.target.value)}
                                    />
                                </div>
                            </th>
                            <th className="px-6 py-2">
                                <div className="relative">
                                    <Search size={12} className="absolute left-2 top-1/2 -translate-y-1/2 text-gray-600" />
                                    <input
                                        type="text"
                                        placeholder="Filter..."
                                        className="w-full bg-gray-950 border border-gray-800 rounded px-2 py-1 pl-7 text-[10px] text-gray-300 focus:border-blue-500 outline-none"
                                        value={filters.version}
                                        onChange={(e) => handleFilterChange('version', e.target.value)}
                                    />
                                </div>
                            </th>
                            <th className="px-6 py-2">
                                <select
                                    className="w-full bg-gray-950 border border-gray-800 rounded px-2 py-1 text-[10px] text-gray-300 focus:border-blue-500 outline-none"
                                    value={filters.scope}
                                    onChange={(e) => handleFilterChange('scope', e.target.value)}
                                >
                                    <option value="">All</option>
                                    <option value="compile">Compile</option>
                                    <option value="test">Test</option>
                                    <option value="provided">Provided</option>
                                    <option value="runtime">Runtime</option>
                                </select>
                            </th>
                            <th className="px-6 py-2">
                                <select
                                    className="w-full bg-gray-950 border border-gray-800 rounded px-2 py-1 text-[10px] text-gray-300 focus:border-blue-500 outline-none"
                                    value={filters.resolutionStatus}
                                    onChange={(e) => handleFilterChange('resolutionStatus', e.target.value)}
                                >
                                    <option value="">All</option>
                                    <option value="resolved">Resolved</option>
                                    <option value="conflict">Conflict</option>
                                    <option value="optional">Optional</option>
                                    <option value="local">Local Module</option>
                                    <option value="error">Error</option>
                                </select>
                            </th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-800">
                        {processedDependencies.length === 0 ? (
                            <tr>
                                <td colSpan={5} className="px-6 py-12 text-center text-gray-600 italic">
                                    No dependencies match your filters.
                                </td>
                            </tr>
                        ) : (
                            processedDependencies.map((dep, index) => {
                                const key = `${dep.groupId}:${dep.artifactId}:${dep.version}-${index}`;
                                return (
                                    <tr key={key} className="hover:bg-gray-800/50 transition-colors group">
                                        <td className="px-6 py-4 font-medium text-white flex items-center gap-2">
                                            <Package size={16} className="text-blue-500 group-hover:text-blue-400 transition-colors" />
                                            {dep.artifactId}
                                        </td>
                                        <td className="px-6 py-4 font-mono">{dep.groupId}</td>
                                        <td className="px-6 py-4">
                                            <span className="font-mono text-blue-300 bg-blue-900/20 px-2 py-0.5 rounded text-xs border border-blue-900/50">
                                                {dep.version}
                                            </span>
                                        </td>
                                        <td className="px-6 py-4">
                                            <span className={`px-2 py-0.5 rounded text-xs border ${dep.scope === 'test' ? 'bg-green-900/20 text-green-300 border-green-900/50' :
                                                dep.scope === 'provided' ? 'bg-purple-900/20 text-purple-300 border-purple-900/50' :
                                                    'bg-gray-800 text-gray-400 border-gray-700'
                                                }`}>
                                                {dep.scope || 'compile'}
                                            </span>
                                        </td>
                                        <td className="px-6 py-4">
                                            {dep.resolutionStatus === 'CONFLICT' ? (
                                                <span className="flex items-center gap-1 text-red-400 bg-red-900/20 px-2 py-1 rounded w-fit border border-red-900/50">
                                                    <AlertTriangle size={14} /> Conflict
                                                </span>
                                            ) : dep.resolutionStatus === 'OPTIONAL' ? (
                                                <span className="flex items-center gap-1 text-yellow-400 bg-yellow-900/20 px-2 py-1 rounded w-fit border border-yellow-900/50">
                                                    <FileQuestion size={14} /> Optional
                                                </span>
                                            ) : dep.resolutionStatus === 'LOCAL' ? (
                                                <span className="flex items-center gap-1 text-cyan-400 bg-cyan-900/20 px-2 py-1 rounded w-fit border border-cyan-900/50">
                                                    <Package size={14} /> Local Module
                                                </span>
                                            ) : dep.resolutionStatus === 'ERROR' ? (
                                                <span className="flex items-center gap-1 text-red-500 bg-red-950/40 px-2 py-1 rounded w-fit border border-red-900">
                                                    <AlertTriangle size={14} /> Error
                                                </span>
                                            ) : (
                                                <span className="text-green-400 flex items-center gap-1 text-xs px-2 py-1 bg-green-900/10 rounded border border-green-900/30 w-fit">
                                                    Resolved
                                                </span>
                                            )}
                                        </td>
                                    </tr>
                                );
                            })
                        )}
                    </tbody>
                </table>
            </div>

            <div className="text-right text-xs text-gray-600">
                Showing {processedDependencies.length} of {uniqueDependencies.length} dependencies
            </div>
        </div>
    );
}
