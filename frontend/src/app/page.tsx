
export default function Home() {
  return (
    <div className="grid grid-rows-[20px_1fr_20px] items-center justify-items-center min-h-screen p-8 pb-20 gap-16 sm:p-20 font-[family-name:var(--font-geist-sans)] bg-black text-white">
      <main className="flex flex-col gap-8 row-start-2 items-center sm:items-start max-w-2xl">
        <div className="bg-gradient-to-r from-blue-500 to-purple-600 bg-clip-text text-transparent">
           <h1 className="text-6xl font-extrabold tracking-tighter sm:text-7xl">modernmvn.</h1>
        </div>
        
        <p className="text-xl text-gray-400 font-light leading-relaxed">
          Dependency intelligence for the modern Java developer. 
          Stop guessing why a version was selected. Start visualizing your build.
        </p>

        <div className="flex flex-col gap-4 w-full">
            <div className="flex gap-4 items-center flex-col sm:flex-row">
              <span className="px-4 py-2 rounded-full border border-gray-800 bg-gray-900 text-sm text-gray-400">
                🚀 Coming Soon
              </span>
              <span className="px-4 py-2 rounded-full border border-gray-800 bg-gray-900 text-sm text-gray-400">
                📦 Maven Central Search
              </span>
              <span className="px-4 py-2 rounded-full border border-gray-800 bg-gray-900 text-sm text-gray-400">
                 🕸️ Dependency Graph
              </span>
            </div>
        </div>

        <div className="flex gap-4 items-center flex-col sm:flex-row mt-8">
          <a
            className="rounded-full border border-solid border-transparent transition-colors flex items-center justify-center bg-blue-600 text-white gap-2 hover:bg-blue-700 text-sm sm:text-base h-10 sm:h-12 px-4 sm:px-5"
            href="https://github.com/tekalenandkumar/modernmvn"
            target="_blank"
            rel="noopener noreferrer"
          >
            Star on GitHub
          </a>
          <a
            className="rounded-full border border-solid border-gray-700 transition-colors flex items-center justify-center hover:bg-gray-800 text-sm sm:text-base h-10 sm:h-12 px-4 sm:px-5 sm:min-w-44"
            href="#"
            target="_blank"
            rel="noopener noreferrer"
          >
            Read the Vision
          </a>
        </div>
      </main>
      <footer className="row-start-3 flex gap-6 flex-wrap items-center justify-center text-gray-500 text-sm">
        <p>© 2026 Modern Maven. All rights reserved.</p>
      </footer>
    </div>
  );
}
