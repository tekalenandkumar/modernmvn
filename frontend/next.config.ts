import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  typescript: {
    ignoreBuildErrors: true,
  },
  async rewrites() {
    const backendUrl = process.env.BACKEND_URL || "http://localhost:8080";
    return [
      {
        source: '/api/maven/:path*',
        destination: `${backendUrl}/api/maven/:path*`,
      },
      {
        source: '/api/security/:path*',
        destination: `${backendUrl}/api/security/:path*`,
      },
    ]
  },
};

export default nextConfig;

