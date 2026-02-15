import React, { memo } from 'react';
import { Handle, Position, NodeProps } from '@xyflow/react';
import { Package, AlertTriangle, FileQuestion } from 'lucide-react';

const DependencyNodeComponent = ({ data, selected }: NodeProps) => {
    const { status, artifactId, groupId, version, scope } = data as { status: string, artifactId: string, groupId: string, version: string, scope: string };

    let borderColor = 'border-blue-500';
    let bgColor = 'bg-slate-800';
    let icon = <Package size={16} className="text-blue-400" />;

    const isTestOrProvided = scope === 'test' || scope === 'provided';

    if (status === 'CONFLICT') {
        borderColor = 'border-red-500';
        bgColor = 'bg-red-950/80';
        icon = <AlertTriangle size={16} className="text-red-400" />;
    } else if (status === 'LOCAL') {
        borderColor = 'border-cyan-500 border-dashed';
        bgColor = 'bg-cyan-950/30';
        icon = <Package size={16} className="text-cyan-400" />;
    } else if (status === 'OPTIONAL') {
        borderColor = 'border-yellow-500 border-dashed';
        bgColor = 'bg-yellow-950/20';
        icon = <FileQuestion size={16} className="text-yellow-400" />;
    } else if (status === 'ERROR') {
        borderColor = 'border-red-600';
        bgColor = 'bg-red-950';
        icon = <AlertTriangle size={16} className="text-red-500" />;
    } else if (isTestOrProvided) {
        borderColor = 'border-green-500/50';
        bgColor = 'bg-green-950/20';
        icon = <Package size={16} className="text-green-400" />;
    }

    return (
        <div
            className={`relative rounded-lg border-2 ${borderColor} ${bgColor} p-3 shadow-lg transition-all min-w-[200px] ${selected ? 'ring-2 ring-white ring-offset-2 ring-offset-black' : ''
                }`}
        >
            <Handle type="target" position={Position.Left} className="!bg-gray-400" />

            <div className="flex items-start gap-3">
                <div className="mt-1 p-1.5 rounded-md bg-black/20">{icon}</div>
                <div className="flex-1 overflow-hidden">
                    <div className="font-bold text-sm text-white truncate" title={artifactId}>
                        {artifactId || 'Unknown Artifact'}
                    </div>
                    <div className="text-xs text-slate-400 truncate mb-2" title={groupId}>
                        {groupId || 'Unknown Group'}
                    </div>

                    <div className="flex items-center gap-2 flex-wrap">
                        <span className="px-1.5 py-0.5 rounded text-[10px] font-mono bg-blue-900/50 text-blue-200 border border-blue-800">
                            {version}
                        </span>
                        {scope && scope !== 'compile' && (
                            <span className="px-1.5 py-0.5 rounded text-[10px] font-mono bg-slate-700 text-slate-300 border border-slate-600">
                                {scope}
                            </span>
                        )}
                        {status === 'CONFLICT' && (
                            <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-red-900/50 text-red-200 border border-red-800 animate-pulse">
                                CONFLICT
                            </span>
                        )}
                        {status === 'LOCAL' && (
                            <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-cyan-900/50 text-cyan-200 border border-cyan-800">
                                LOCAL MODULE
                            </span>
                        )}
                    </div>
                </div>
            </div>

            <Handle type="source" position={Position.Right} className="!bg-gray-400" />
        </div>
    );
};

export default memo(DependencyNodeComponent);
