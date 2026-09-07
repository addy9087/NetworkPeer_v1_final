import { defineConfig } from "@lovable.dev/vite-tanstack-config";

export default defineConfig({
  tanstackStart: {
    // Redirect TanStack Start's bundled server entry to src/server.ts (our SSR error wrapper).
    // nitro/vite builds from this
    server: { entry: "server" },
  },
  vite: {
    server: {
      proxy: {
        "/api/v1": {
          target: "http://networkpeer-staging-api-alb-969746120.eu-north-1.elb.amazonaws.com",
          changeOrigin: true,
        },
      },
    },
  },
});
