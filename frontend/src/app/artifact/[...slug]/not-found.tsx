import Link from 'next/link';

export default function ArtifactNotFound() {
    return (
        <div className="min-h-screen bg-black text-white flex items-center justify-center font-[family-name:var(--font-geist-sans)]">
            <div className="text-center max-w-md px-6">
                <div className="text-8xl font-extrabold bg-gradient-to-r from-blue-500 to-purple-600 bg-clip-text text-transparent mb-4">
                    404
                </div>
                <h1 className="text-2xl font-bold mb-3">Artifact Not Found</h1>
                <p className="text-gray-400 mb-8 leading-relaxed">
                    The artifact you&apos;re looking for doesn&apos;t exist on Maven Central, or the URL format is incorrect.
                </p>
                <div className="flex flex-col sm:flex-row gap-3 justify-center">
                    <Link
                        href="/"
                        className="px-6 py-2.5 rounded-lg bg-blue-600 hover:bg-blue-700 text-white text-sm font-bold transition-all"
                    >
                        Go Home
                    </Link>
                    <Link
                        href="/analyze"
                        className="px-6 py-2.5 rounded-lg border border-gray-700 hover:bg-gray-800 text-gray-300 text-sm font-medium transition-colors"
                    >
                        Analyze Dependencies
                    </Link>
                </div>
            </div>
        </div>
    );
}
