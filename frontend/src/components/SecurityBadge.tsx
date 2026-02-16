'use client';

import React, { useEffect, useState } from 'react';
import { Shield, ShieldAlert, ShieldCheck, ShieldX, Loader2 } from 'lucide-react';
import {
    SafetyBadge as SafetyBadgeData,
    SafetyIndicator,
    fetchSafetyBadge,
    safetyColor,
    safetyBgColor,
} from '@/lib/security-api';

interface SecurityBadgeProps {
    groupId: string;
    artifactId: string;
    version: string;
    /** Show as compact inline badge or expanded */
    compact?: boolean;
    /** Click handler */
    onClick?: () => void;
}

/**
 * A traffic-light style security badge that auto-fetches safety status.
 * Shows SAFE / CAUTION / WARNING / DANGER with appropriate colors.
 */
export default function SecurityBadge({ groupId, artifactId, version, compact = false, onClick }: SecurityBadgeProps) {
    const [badge, setBadge] = useState<SafetyBadgeData | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(false);

    useEffect(() => {
        if (!groupId || !artifactId || !version) return;
        setLoading(true);
        setError(false);
        fetchSafetyBadge(groupId, artifactId, version)
            .then(setBadge)
            .catch(() => setError(true))
            .finally(() => setLoading(false));
    }, [groupId, artifactId, version]);

    if (loading) {
        return (
            <span style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '4px',
                padding: compact ? '2px 8px' : '4px 12px',
                borderRadius: '9999px',
                background: 'rgba(116, 125, 140, 0.1)',
                color: '#747d8c',
                fontSize: compact ? '11px' : '12px',
            }}>
                <Loader2 size={compact ? 10 : 12} style={{ animation: 'spin 1s linear infinite' }} />
                {!compact && 'Checking...'}
            </span>
        );
    }

    if (error || !badge) {
        return (
            <span style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '4px',
                padding: compact ? '2px 8px' : '4px 12px',
                borderRadius: '9999px',
                background: 'rgba(116, 125, 140, 0.1)',
                color: '#747d8c',
                fontSize: compact ? '11px' : '12px',
            }}>
                <Shield size={compact ? 10 : 12} />
                {!compact && 'Unknown'}
            </span>
        );
    }

    const color = safetyColor(badge.indicator);
    const bg = safetyBgColor(badge.indicator);
    const Icon = getIcon(badge.indicator);

    return (
        <span
            role="button"
            tabIndex={0}
            onClick={onClick}
            onKeyDown={(e) => { if (e.key === 'Enter' && onClick) onClick(); }}
            style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '5px',
                padding: compact ? '2px 8px' : '5px 14px',
                borderRadius: '9999px',
                background: bg,
                color: color,
                fontSize: compact ? '11px' : '12px',
                fontWeight: 600,
                letterSpacing: '0.02em',
                cursor: onClick ? 'pointer' : 'default',
                border: `1px solid ${color}22`,
                transition: 'all 0.2s ease',
                whiteSpace: 'nowrap',
            }}
            title={badge.label}
        >
            <Icon size={compact ? 11 : 13} />
            {compact
                ? (badge.vulnerabilityCount > 0 ? badge.vulnerabilityCount : '✓')
                : badge.label}
        </span>
    );
}

function getIcon(indicator: SafetyIndicator) {
    switch (indicator) {
        case 'SAFE': return ShieldCheck;
        case 'CAUTION': return ShieldAlert;
        case 'WARNING': return ShieldAlert;
        case 'DANGER': return ShieldX;
    }
}
