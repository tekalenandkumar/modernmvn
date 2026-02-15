"use client";

import React, { useMemo } from 'react';
import { DependencyNode } from '@/lib/api';
import { AlertTriangle, CheckCircle, Info } from 'lucide-react';

interface ConflictSummaryProps {
    rootNode: DependencyNode;
}

interface ConflictItem {
    groupId: string;
    artifactId: string;
    version: string;
    conflictMessage: string;
    resolutionStatus: string;
}

export default function ConflictSummary({ rootNode }: ConflictSummaryProps) {
    const conflicts = useMemo(() => {
        const list: ConflictItem[] = [];
        const traverse = (node: DependencyNode) => {
            if (node.resolutionStatus === 'CONFLICT' || node.resolutionStatus === 'ERROR') {
                list.push({
                    groupId: node.groupId,
                    artifactId: node.artifactId,
                    version: node.version,
                    conflictMessage: node.conflictMessage || 'Unspecified conflict',
                    resolutionStatus: node.resolutionStatus
                });
            }
            node.children.forEach(traverse);
        };
        traverse(rootNode);
        // Remove duplicates based on G:A:V + message to avoid clutter
        const unique = new Map<string, ConflictItem>();
        list.forEach(item => {
            const key = `${item.groupId}:${item.artifactId}:${item.version}:${item.conflictMessage}`;
            if (!unique.has(key)) {
                unique.set(key, item);
            }
        });
        return Array.from(unique.values());
    }, [rootNode]);

    if (conflicts.length === 0) {
        return (
            <div className="bg-green-900/10 border border-green-900/30 rounded-lg p-6 flex items-center gap-4 text-green-400">
                <CheckCircle size={24} />
                <div>
                    <h3 className="font-bold">No Conflicts Found</h3>
                    <p className="text-sm opacity-80">All dependencies resolved successfully without version conflicts.</p>
                </div>
            </div>
        );
    }

    return (
        <div className="bg-gray-900 rounded-lg border border-gray-800 overflow-hidden">
            <div className="p-4 border-b border-gray-800 bg-gray-900/50 flex justify-between items-center">
                <h3 className="font-bold flex items-center gap-2 text-yellow-500">
                    <AlertTriangle size={18} />
                    Conflict Summary ({conflicts.length})
                </h3>
            </div>
            <div className="divide-y divide-gray-800 max-h-[400px] overflow-y-auto">
                {conflicts.map((conflict, idx) => (
                    <div key={idx} className="p-4 hover:bg-gray-800/50 transition-colors">
                        <div className="flex justify-between items-start mb-1">
                            <div className="font-mono text-sm text-blue-300">
                                {conflict.groupId}:<span className="font-bold text-white">{conflict.artifactId}</span>:{conflict.version}
                            </div>
                            <span className={`text-[10px] px-2 py-0.5 rounded uppercase font-bold ${conflict.resolutionStatus === 'ERROR' ? 'bg-red-900/20 text-red-400' : 'bg-yellow-900/20 text-yellow-400'
                                }`}>
                                {conflict.resolutionStatus}
                            </span>
                        </div>
                        <div className="flex items-start gap-2 mt-2 text-sm text-gray-400 bg-black/20 p-2 rounded">
                            <Info size={14} className="mt-0.5 flex-shrink-0 text-gray-500" />
                            <p>{conflict.conflictMessage}</p>
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}
