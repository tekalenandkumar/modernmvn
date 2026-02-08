import Link from 'next/link';

export default function Home() {
  return (
    <div className="grid grid-rows-[20px_1fr_20px] items-center justify-items-center min-h-screen p-8 pb-20 gap-16 sm:p-20 font-[family-name:var(--font-geist-sans)] bg-black text-white">
      <main className="flex flex-col gap-8 row-start-2 items-center sm:items-start max-w-2xl">
        <div className="bg-gradient-to-r from-blue-500 to-purple-600 bg-clip-text text-transparent">
          <h1 className="text-6xl font-extrabold tracking-tighter sm:text-7xl">modernmvn.</h1>
        </div>

        <p className="text-xl text-gray-400 font-light leading-relaxed text-center sm:text-left">
          Dependency intelligence for the modern Java developer.
          <br className="hidden sm:block" />
          Stop guessing why a version was selected. Start visualizing your build.
        </p>

        <div className="flex flex-col gap-4 w-full items-center sm:items-start">
          <div className="flex gap-4">
            <Link href="/analyze" className="bg-blue-600 hover:bg-blue-700 text-white px-8 py-3 rounded-full font-bold transition-all shadow-lg shadow-blue-900/40 hover:scale-105 active:scale-95">
              Start Analyzing
            </Link>
            <a href="https://github.com/tekalenandkumar/modernmvn" target="_blank" className="border border-gray-700 hover:bg-gray-800 text-white px-8 py-3 rounded-full font-bold transition-colors">
              GitHub
            </a>
          </div>

          <div className="flex gap-6 mt-8 text-sm text-gray-500 font-mono">
            <div className="flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-blue-500"></span> Visual Graph
            </div>
            <div className="flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-purple-500"></span> Conflict Detection
            </div>
            <div className="flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-green-500"></span> Deep Analysis
            </div>
          </div>
        </div>
      </main>
      <footer className="row-start-3 flex gap-6 flex-wrap items-center justify-center text-gray-500 text-sm">
        <p>© 2026 Modern Maven. All rights reserved.</p>
      </footer>
    </div>
  );
}
