import Link from 'next/link';
import {
  Search,
  TrendingUp,
  Shield,
  GitBranch,
  Zap,
  Code2,
  ArrowRight,
  Package,
  Star,
  BarChart3,
} from 'lucide-react';
import HomeContent from '@/components/HomeContent';

export const metadata = {
  title: 'Modern Maven — Dependency Intelligence for Java',
  description: 'Browse Maven artifacts, detect vulnerabilities, visualize dependency trees, and get version recommendations. The modern developer tool for Java dependency management.',
};

const FEATURES = [
  {
    icon: Search,
    color: 'blue',
    title: 'Smart Artifact Search',
    desc: 'Search 500k+ Maven Central artifacts with full-text and GAV matching. Browse by group or artifact.',
  },
  {
    icon: Shield,
    color: 'red',
    title: 'Security Intelligence',
    desc: 'OSV.dev + NVD CVSS scores for every version. Vulnerability trends persisted in PostgreSQL.',
  },
  {
    icon: GitBranch,
    color: 'purple',
    title: 'Dependency Trees',
    desc: 'Visualize transitive dependencies with conflict detection and resolution paths.',
  },
  {
    icon: Star,
    color: 'green',
    title: 'Smart Recommendations',
    desc: 'Version recommendations that skip CVE-affected releases and prefer stable major lines.',
  },
  {
    icon: BarChart3,
    color: 'orange',
    title: 'Historical Trends',
    desc: 'Track vulnerability count history for any artifact over time with CVSS trending.',
  },
  {
    icon: Code2,
    color: 'cyan',
    title: 'Embeddable Badges',
    desc: 'Dynamic SVG version badges for README files. Support for Markdown, HTML, and RST.',
  },
];

const POPULAR_GROUPS = [
  { group: 'org.springframework.boot', label: 'Spring Boot' },
  { group: 'com.google.guava', label: 'Google Guava' },
  { group: 'org.apache.commons', label: 'Apache Commons' },
  { group: 'io.netty', label: 'Netty' },
  { group: 'org.slf4j', label: 'SLF4J' },
  { group: 'com.fasterxml.jackson.core', label: 'Jackson' },
  { group: 'io.micrometer', label: 'Micrometer' },
  { group: 'org.hibernate.orm', label: 'Hibernate' },
];

const POPULAR_ARTIFACTS = [
  { g: 'org.springframework.boot', a: 'spring-boot-starter-web' },
  { g: 'com.google.guava', a: 'guava' },
  { g: 'org.apache.commons', a: 'commons-lang3' },
  { g: 'com.fasterxml.jackson.core', a: 'jackson-databind' },
  { g: 'org.slf4j', a: 'slf4j-api' },
  { g: 'io.netty', a: 'netty-all' },
];

function colorClass(color: string) {
  const map: Record<string, { bg: string; text: string; border: string; icon: string }> = {
    blue: { bg: 'bg-blue-500/10', text: 'text-blue-400', border: 'border-blue-500/20', icon: 'text-blue-400' },
    red: { bg: 'bg-red-500/10', text: 'text-red-400', border: 'border-red-500/20', icon: 'text-red-400' },
    purple: { bg: 'bg-purple-500/10', text: 'text-purple-400', border: 'border-purple-500/20', icon: 'text-purple-400' },
    green: { bg: 'bg-green-500/10', text: 'text-green-400', border: 'border-green-500/20', icon: 'text-green-400' },
    orange: { bg: 'bg-orange-500/10', text: 'text-orange-400', border: 'border-orange-500/20', icon: 'text-orange-400' },
    cyan: { bg: 'bg-cyan-500/10', text: 'text-cyan-400', border: 'border-cyan-500/20', icon: 'text-cyan-400' },
  };
  return map[color] ?? map.blue;
}

export default function Home() {
  return (
    <div className="min-h-screen bg-black text-white font-[family-name:var(--font-geist-sans)] overflow-x-hidden">

      {/* ── Nav ─────────────────────────────────────────── */}
      <nav className="sticky top-0 z-50 border-b border-gray-800/60 bg-black/80 backdrop-blur-xl">
        <div className="max-w-7xl mx-auto px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-2">
            {/* SVG logo inline */}
            <svg width="28" height="28" viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect width="64" height="64" rx="10" fill="#090909" />
              <defs>
                <linearGradient id="navmg" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stopColor="#3b82f6" />
                  <stop offset="100%" stopColor="#8b5cf6" />
                </linearGradient>
              </defs>
              <path d="M10 48 L10 16 L22 16 L32 34 L42 16 L54 16 L54 48 L44 48 L44 30 L34 48 L30 48 L20 30 L20 48 Z" fill="url(#navmg)" />
              <circle cx="32" cy="56" r="3" fill="#ef4444" />
            </svg>
            <span className="text-xl font-extrabold bg-gradient-to-r from-blue-500 to-purple-600 bg-clip-text text-transparent">modernmvn.</span>
          </div>
          <div className="flex items-center gap-6 text-sm">
            <Link href="/search" className="text-gray-400 hover:text-white transition-colors hidden sm:block">Browse</Link>
            <Link href="/analyze" className="text-gray-400 hover:text-white transition-colors hidden sm:block">Analyze</Link>
            <Link
              href="/search"
              className="flex items-center gap-1.5 bg-blue-600 hover:bg-blue-700 px-4 py-2 rounded-lg text-sm font-medium transition-colors"
            >
              <Search size={14} /> Search
            </Link>
          </div>
        </div>
      </nav>

      {/* ── Hero ─────────────────────────────────────────── */}
      <section className="relative overflow-hidden">
        {/* Background radial glows */}
        <div className="absolute inset-0 pointer-events-none" aria-hidden>
          <div className="absolute top-0 left-1/2 -translate-x-1/2 w-[900px] h-[500px] bg-blue-600/10 rounded-full blur-[120px]" />
          <div className="absolute top-20 left-1/4 w-[400px] h-[400px] bg-purple-600/8 rounded-full blur-[100px]" />
          <div className="absolute top-0 right-1/4 w-[300px] h-[300px] bg-cyan-600/8 rounded-full blur-[80px]" />
        </div>

        <div className="relative max-w-7xl mx-auto px-6 pt-24 pb-20 text-center">
          {/* Pill badge */}
          <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full border border-blue-800/50 bg-blue-500/10 text-blue-400 text-xs font-medium mb-8">
            <Zap size={11} className="text-yellow-400" />
            <span>Now with NVD CVSS scores + historical vulnerability trends</span>
          </div>

          <h1 className="text-5xl sm:text-6xl md:text-7xl font-extrabold tracking-tighter leading-[0.95] mb-6">
            <span className="bg-gradient-to-br from-white via-gray-200 to-gray-500 bg-clip-text text-transparent">
              Dependency intelligence
            </span>
            <br />
            <span className="bg-gradient-to-r from-blue-500 via-purple-500 to-cyan-400 bg-clip-text text-transparent">
              for modern Java.
            </span>
          </h1>

          <p className="text-xl text-gray-400 max-w-2xl mx-auto leading-relaxed mb-10">
            Browse 500k+ Maven Central artifacts. Detect CVEs with real CVSS scores.
            Visualize dependency trees. Get security-aware version recommendations.
          </p>

          {/* Search bar */}
          <Link
            href="/search"
            id="hero-search"
            className="flex items-center gap-3 max-w-xl mx-auto px-5 py-4 rounded-2xl bg-gray-900/80 border border-gray-700/80 hover:border-blue-500/50 transition-all group shadow-2xl shadow-black/40 cursor-pointer"
          >
            <Search size={18} className="text-gray-500 group-hover:text-blue-400 transition-colors shrink-0" />
            <span className="text-gray-500 group-hover:text-gray-400 transition-colors text-base">Search Maven artifacts... e.g. spring-boot-starter</span>
            <kbd className="ml-auto text-[10px] px-2 py-0.5 rounded bg-gray-800 border border-gray-700 text-gray-500 shrink-0 hidden sm:block">⌘ K</kbd>
          </Link>

          {/* CTA row */}
          <div className="flex flex-wrap items-center justify-center gap-4 mt-8">
            <Link
              href="/analyze"
              className="inline-flex items-center gap-2 bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-500 hover:to-purple-500 text-white px-8 py-3.5 rounded-xl font-semibold transition-all shadow-lg shadow-blue-900/30 hover:scale-[1.02] active:scale-[0.98]"
            >
              <GitBranch size={16} /> Analyze POM File
            </Link>
            <Link
              href="/search"
              className="inline-flex items-center gap-2 border border-gray-700 hover:border-gray-500 hover:bg-gray-900 text-gray-300 px-8 py-3.5 rounded-xl font-semibold transition-all"
            >
              Browse Artifacts <ArrowRight size={16} />
            </Link>
          </div>

          {/* Stats row */}
          <div className="flex flex-wrap items-center justify-center gap-8 mt-14 text-sm text-gray-500">
            {[
              ['500k+', 'Maven artifacts indexed'],
              ['OSV + NVD', 'dual CVE data sources'],
              ['CVSS v3.1', 'scoring for every advisory'],
              ['PostgreSQL', 'powered trend tracking'],
            ].map(([val, label]) => (
              <div key={val} className="flex items-center gap-2">
                <span className="font-bold text-white text-base">{val}</span>
                <span>{label}</span>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── Feature grid ─────────────────────────────────── */}
      <section className="max-w-7xl mx-auto px-6 py-16">
        <div className="text-center mb-12">
          <h2 className="text-3xl font-extrabold tracking-tight mb-3">Everything in one place</h2>
          <p className="text-gray-500 text-lg">Built for Java developers who care about what they ship.</p>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {FEATURES.map(({ icon: Icon, color, title, desc }) => {
            const c = colorClass(color);
            return (
              <div
                key={title}
                className="p-6 rounded-2xl border border-gray-800/60 bg-gray-950/40 hover:bg-gray-900/50 hover:border-gray-700 transition-all group"
              >
                <div className={`w-10 h-10 rounded-xl ${c.bg} border ${c.border} flex items-center justify-center mb-4`}>
                  <Icon size={20} className={c.icon} />
                </div>
                <h3 className="font-bold text-gray-100 mb-1.5">{title}</h3>
                <p className="text-sm text-gray-500 leading-relaxed">{desc}</p>
              </div>
            );
          })}
        </div>
      </section>

      {/* ── Popular groups quick-links ────────────────────── */}
      <section className="max-w-7xl mx-auto px-6 pb-10">
        <div className="border border-gray-800/60 rounded-2xl bg-gray-950/30 p-6">
          <div className="flex items-center gap-2 mb-5">
            <Package size={16} className="text-violet-400" />
            <h2 className="text-sm font-bold text-gray-400 uppercase tracking-wider">Popular Groups</h2>
          </div>
          <div className="flex flex-wrap gap-2">
            {POPULAR_GROUPS.map(({ group, label }) => (
              <Link
                key={group}
                href={`/artifact/${group}`}
                className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg border border-gray-800 bg-gray-900/50 hover:bg-gray-800 hover:border-violet-800/50 text-sm text-gray-300 hover:text-violet-300 transition-all font-mono"
              >
                {label}
              </Link>
            ))}
          </div>

          <div className="mt-5 pt-5 border-t border-gray-800/50">
            <div className="flex items-center gap-2 mb-4">
              <TrendingUp size={14} className="text-orange-400" />
              <span className="text-xs font-bold text-gray-500 uppercase tracking-wider">Popular Artifacts</span>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-2">
              {POPULAR_ARTIFACTS.map(({ g, a }) => (
                <Link
                  key={`${g}:${a}`}
                  href={`/artifact/${g}/${a}`}
                  className="flex items-center gap-2 px-3 py-2.5 rounded-lg border border-gray-800/60 hover:border-orange-800/40 hover:bg-gray-900/60 transition-all group"
                >
                  <Package size={13} className="text-orange-400/60 group-hover:text-orange-400 shrink-0" />
                  <div className="min-w-0">
                    <p className="text-xs font-mono text-gray-300 group-hover:text-orange-300 truncate">{a}</p>
                    <p className="text-[10px] text-gray-600 truncate">{g}</p>
                  </div>
                  <ArrowRight size={11} className="ml-auto text-gray-700 group-hover:text-orange-400" />
                </Link>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* ── Live Trending + What's New ───────────────────── */}
      <section className="max-w-7xl mx-auto px-6 pb-16">
        <HomeContent />
      </section>

      {/* ── Footer ─────────────────────────────────────────── */}
      <footer className="border-t border-gray-800/60 py-10 text-center text-sm text-gray-600">
        <div className="max-w-7xl mx-auto px-6 flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <svg width="20" height="20" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
              <rect width="64" height="64" rx="10" fill="#090909" />
              <defs><linearGradient id="footmg" x1="0%" y1="0%" x2="100%" y2="100%"><stop offset="0%" stopColor="#3b82f6" /><stop offset="100%" stopColor="#8b5cf6" /></linearGradient></defs>
              <path d="M10 48 L10 16 L22 16 L32 34 L42 16 L54 16 L54 48 L44 48 L44 30 L34 48 L30 48 L20 30 L20 48 Z" fill="url(#footmg)" />
            </svg>
            <span className="font-bold text-gray-500">modernmvn.</span>
          </div>
          <p>© 2026 Modern Maven. Powered by Maven Central, OSV.dev & NVD.</p>
          <div className="flex gap-5">
            <Link href="/search" className="hover:text-gray-400 transition-colors">Search</Link>
            <Link href="/analyze" className="hover:text-gray-400 transition-colors">Analyze</Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
