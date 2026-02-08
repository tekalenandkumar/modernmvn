import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  typescript: {
    ignoreBuildErrors: true,
  },
  async rewrites() {
    return [
      {
        source: '/api/maven/:path*',
        destination: 'http://localhost:8080/api/maven/:path*',
      },
    ]
  },
};

export default nextConfig;
