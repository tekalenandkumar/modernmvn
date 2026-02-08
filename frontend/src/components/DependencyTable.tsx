import React from 'react';
import { DependencyNode } from '@/lib/api';
import { Package, AlertTriangle, FileQuestion } from 'lucide-react';

interface DependencyTableProps {
    rootNode: DependencyNode;
}

const flattenDependencies = (node: DependencyNode, depth = 0, list: any[] = []) => {
    list.push({ ...node, depth });
    node.children.forEach(child => flattenDependencies(child, depth + 1, list));
    return list;
};

// Remove duplicates if desired, but tree view usually implies hierarchy. 
// If it's a "Dependency Table" as in "All dependencies", we might want a set of unique artifacts.
// Let's do a unique list approach for a "Bill of Materials" style view.
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

export default function DependencyTable({ rootNode }: DependencyTableProps) {
    const dependencies = getUniqueDependencies(rootNode);

    return (
        <div className="overflow-x-auto rounded-lg border border-gray-800 bg-gray-900/50">
            <table className="w-full text-left text-sm text-gray-400">
                <thead className="bg-gray-900 text-xs uppercase text-gray-500">
                    <tr>
                        <th className="px-6 py-4 font-medium">Artifact</th>
                        <th className="px-6 py-4 font-medium">Group</th>
                        <th className="px-6 py-4 font-medium">Version</th>
                        <th className="px-6 py-4 font-medium">Scope</th>
                        <th className="px-6 py-4 font-medium">Status</th>
                    </tr>
                </thead>
                <tbody className="divide-y divide-gray-800">
                    {dependencies.map((dep, index) => {
                        const key = `${dep.groupId}:${dep.artifactId}:${dep.version}-${index}`; // unique key
                        return (
                            <tr key={key} className="hover:bg-gray-800/50 transition-colors">
                                <td className="px-6 py-4 font-medium text-white flex items-center gap-2">
                                    <Package size={16} className="text-blue-500" />
                                    {dep.artifactId}
                                </td>
                                <td className="px-6 py-4 font-mono">{dep.groupId}</td>
                                <td className="px-6 py-4 font-mono text-blue-300 bg-blue-900/20 rounded inline-block my-2 mx-6 w-fit px-2 py-0.5">{dep.version}</td>
                                <td className="px-6 py-4">{dep.scope || 'compile'}</td>
                                <td className="px-6 py-4">
                                    {dep.resolutionStatus === 'CONFLICT' ? (
                                        <span className="flex items-center gap-1 text-red-400 bg-red-900/20 px-2 py-1 rounded w-fit">
                                            <AlertTriangle size={14} /> Conflict
                                        </span>
                                    ) : dep.resolutionStatus === 'OPTIONAL' ? (
                                        <span className="flex items-center gap-1 text-yellow-400 bg-yellow-900/20 px-2 py-1 rounded w-fit">
                                            <FileQuestion size={14} /> Optional
                                        </span>
                                    ) : (
                                        <span className="text-green-400 flex items-center gap-1">
                                            Resolved
                                        </span>
                                    )}
                                </td>
                            </tr>
                        );
                    })}
                </tbody>
            </table>
        </div>
    );
}
