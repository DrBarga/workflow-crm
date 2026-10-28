import type { NextConfig } from "next";
// Importing this here makes a missing NEXT_PUBLIC_API_BASE_URL fail
// `next dev` / `next build` / `next start` immediately, not just at
// request time inside the app.
import "./src/shared/config";

const nextConfig: NextConfig = {
  // Сборка кладёт в .next/standalone собственный сервер и только рантайм-зависимости.
  // Без этого образ клиента пришлось бы возить вместе со всем node_modules.
  output: "standalone",
  async headers() {
    return ["/set-password", "/profile"].map((source) => ({
      source,
      headers: [
        { key: "Referrer-Policy", value: "no-referrer" },
        { key: "Cache-Control", value: "no-store" },
      ],
    }));
  },
};

export default nextConfig;
