import type { NextConfig } from "next";

// Backend the /api/v1/* rewrite proxies to. Read when the config is loaded at build time and
// baked into the build output, so it must be set before `next build` (e.g. a Docker build arg).
const API_URL = process.env.API_URL ?? "http://localhost:5011";

const nextConfig: NextConfig = {
  // Emits .next/standalone (minimal server.js + traced node_modules) for the Docker image.
  output: "standalone",

  // Proxy API calls through this app's own origin so the API's SameSite=Strict auth cookies and
  // the readable XSRF-TOKEN cookie are first-party to the frontend, even when the API is hosted
  // on a different site.
  async rewrites() {
    return [
      {
        source: "/api/v1/:path*",
        destination: `${API_URL}/api/v1/:path*`,
      },
    ];
  },
};

export default nextConfig;
