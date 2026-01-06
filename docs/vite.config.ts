import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import mdx from "@mdx-js/rollup";

export default defineConfig({
  plugins: [
    react(),
    mdx({
      providerImportSource: "@mdx-js/react",
    }),
  ],
  optimizeDeps: {
    include: ["@mdx-js/react"],
  },
  server: {
    port: 5173,
    open: true,
  },
});
