"use client";

import React, { useCallback, useMemo, useState } from 'react';
import {
    ReactFlow,
    Background,
    Controls,
    MiniMap,
    useNodesState,
    useEdgesState,
    Node,
    Edge,
    MarkerType,
} from '@xyflow/react';
import dagre from 'dagre';
import { DependencyNode } from '@/lib/api';
import DependencyNodeComponent from './DependencyNodeComponent';
import { X } from 'lucide-react';

import '@xyflow/react/dist/style.css';

const nodeTypes = {
    dependency: DependencyNodeComponent,
};

export default function GraphViewer({ rootNode }: { rootNode: DependencyNode }) {
    const [selectedNode, setSelectedNode] = useState<DependencyNode | null>(null);

    const { nodes: initialNodes, edges: initialEdges, nodeMap } = useMemo(() => {
        return transformGraph(rootNode);
    }, [rootNode]);

    const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes);
    const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);

    React.useEffect(() => {
        setNodes(initialNodes);
        setEdges(initialEdges);
    }, [initialNodes, initialEdges, setNodes, setEdges]);

    const onNodeClick = useCallback((event: React.MouseEvent, node: Node) => {
        const originalNode = nodeMap.get(node.id);
        if (originalNode) {
            setSelectedNode(originalNode);
        }
    }, [nodeMap]);

    return (
        <div className="relative w-full h-[600px] border border-gray-800 rounded-lg bg-black overflow-hidden flex">
            <div className="flex-1 h-full relative">
                <ReactFlow
                    nodes={nodes}
                    edges={edges}
                    onNodesChange={onNodesChange}
                    onEdgesChange={onEdgesChange}
                    onNodeClick={onNodeClick}
                    nodeTypes={nodeTypes}
                    fitView
                    nodesConnectable={false}
                    nodesDraggable={true}
                    colorMode="dark"
                >
                    <Background gap={16} size={1} color="#333" />
                    <Controls className="!bg-gray-800 !border-gray-700 !text-white" />
                    <MiniMap nodeColor="#444" maskColor="#1a1a1a" className="!bg-gray-900 !border-gray-800" />
                </ReactFlow>
            </div>

            {/* Details Sidebar */}
            {/* Sidebar Details */}
            {selectedNode && (
                <div className="absolute top-0 right-0 w-80 h-full bg-gray-900 border-l border-gray-800 p-6 overflow-y-auto shadow-2xl z-20">
                    <div className="flex justify-between items-start mb-6">
                        <h2 className="text-lg font-bold">Node Details</h2>
                        <button onClick={() => setSelectedNode(null)} className="text-gray-500 hover:text-white">✕</button>
                    </div>

                    <div className="space-y-4 text-sm">
                        <div>
                            <span className="text-gray-500 block text-xs uppercase mb-1">Coordinates</span>
                            <div className="font-mono bg-black p-3 rounded border border-gray-800">
                                <div className="text-blue-400">{selectedNode.groupId}</div>
                                <div className="font-bold text-white text-base">{selectedNode.artifactId}</div>
                                <div className="text-purple-400 text-xs mt-1">{selectedNode.version}</div>
                            </div>
                        </div>

                        <div className="flex gap-4">
                            <div>
                                <span className="text-gray-500 block text-xs uppercase mb-1">Scope</span>
                                <span className="inline-block px-2 py-1 bg-gray-800 rounded">{selectedNode.scope}</span>
                            </div>
                            <div>
                                <span className="text-gray-500 block text-xs uppercase mb-1">Type</span>
                                <span className="inline-block px-2 py-1 bg-gray-800 rounded">{selectedNode.type}</span>
                            </div>
                        </div>

                        {selectedNode.resolutionStatus !== 'RESOLVED' && (
                            <div>
                                <span className="text-gray-500 block text-xs uppercase mb-1">Status</span>
                                <div className={`p-3 rounded border ${selectedNode.resolutionStatus === 'ERROR' ? 'bg-red-900/20 border-red-800 text-red-400' : 'bg-yellow-900/20 border-yellow-800 text-yellow-400'}`}>
                                    <div className="font-bold mb-1">{selectedNode.resolutionStatus}</div>
                                    <p>{selectedNode.conflictMessage || "No additional details."}</p>
                                </div>
                            </div>
                        )}

                        <div>
                            <span className="text-gray-500 block text-xs uppercase mb-1">Direct Children</span>
                            <div className="text-2xl font-bold">{selectedNode.children.length}</div>
                        </div>
                    </div>
                </div>
            )}

            {/* Legend */}
            <div className="absolute bottom-4 left-4 bg-gray-900/80 backdrop-blur border border-gray-800 p-4 rounded-lg shadow-lg z-10 text-xs">
                <h3 className="font-bold text-gray-400 mb-2 uppercase text-[10px]">Legend</h3>
                <div className="space-y-2">
                    <div className="flex items-center gap-2">
                        <div className="w-3 h-3 bg-gray-900 border border-blue-500 rounded-sm"></div>
                        <span className="text-gray-300">Resolved</span>
                    </div>
                    <div className="flex items-center gap-2">
                        <div className="w-3 h-3 bg-red-900/20 border-dotted border-red-500 rounded-sm"></div>
                        <span className="text-gray-300">Conflict / Error</span>
                    </div>
                    <div className="flex items-center gap-2">
                        <div className="w-3 h-3 bg-yellow-900/20 border-dashed border-yellow-500 rounded-sm"></div>
                        <span className="text-gray-300">Optional</span>
                    </div>
                    <div className="flex items-center gap-2">
                        <div className="w-3 h-3 bg-green-900/20 border-green-500 rounded-sm"></div>
                        <span className="text-gray-300">Test / Provided</span>
                    </div>
                </div>
            </div>

        </div>
    );
}

// Layout Logic 
const nodeWidth = 280;
const nodeHeight = 100;

const transformGraph = (root: DependencyNode): { nodes: Node[], edges: Edge[], nodeMap: Map<string, DependencyNode> } => {
    const nodes: Node[] = [];
    const edges: Edge[] = [];
    const nodeMap = new Map<string, DependencyNode>();

    // Create dagre graph
    const g = new dagre.graphlib.Graph();
    g.setGraph({ rankdir: 'LR', align: 'UL', ranksep: 120, nodesep: 60 });
    g.setDefaultEdgeLabel(() => ({}));

    // Traverse and populate graph
    const traverse = (node: DependencyNode, parentId?: string) => {
        const id = nodeId(node);
        nodeMap.set(id, node);

        // Avoid cycles/duplicates (simple check, Aether usually resolves to DAG)
        const existing = g.node(id);
        if (!existing) {
            g.setNode(id, { width: nodeWidth, height: nodeHeight, label: node.artifactId, nodeData: node });

            // Recursively add children
            node.children.forEach(child => traverse(child, id));
        }

        if (parentId) {
            const edgeId = `${parentId}-${id}`;
            g.setEdge(parentId, id);

            edges.push({
                id: edgeId,
                source: parentId,
                target: id,
                animated: true,
                type: 'smoothstep',
                style: { stroke: node.resolutionStatus === 'CONFLICT' ? '#ef4444' : '#475569', strokeWidth: 1.5, opacity: 0.6 },
                // markerEnd: { type: MarkerType.ArrowClosed, color: '#475569' },
            });
        }
    };

    traverse(root);

    dagre.layout(g);

    // Convert dagre nodes to React Flow nodes
    g.nodes().forEach((id) => {
        const n = g.node(id) as any;
        const nodeData = n.nodeData as DependencyNode; // access original data

        nodes.push({
            id,
            position: { x: n.x - nodeWidth / 2, y: n.y - nodeHeight / 2 },
            data: {
                label: nodeData.artifactId,
                artifactId: nodeData.artifactId,
                groupId: nodeData.groupId,
                version: nodeData.version,
                scope: nodeData.scope,
                status: nodeData.resolutionStatus
            },
            type: 'dependency',
        });
    });

    return {
        nodes,
        edges: Array.from(new Map(edges.map(e => [e.id, e])).values()),
        nodeMap
    };
}

const nodeId = (node: DependencyNode) => `${node.groupId}:${node.artifactId}:${node.version}`;
