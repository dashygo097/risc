import { defineConfig } from "vite";
import remarkGfm from "remark-gfm";
import { resolve } from "path";
import react from "@vitejs/plugin-react";
import mdx from "@mdx-js/rollup";
import { remarkWaveDrom } from "./lib/remark-wavedrom";

export default defineConfig({
  resolve: {
    alias: {
      "@": resolve(__dirname, "src"),
      "@assets": resolve(__dirname, "src/assets"),
      "@styles": resolve(__dirname, "src/styles"),
      "@components": resolve(__dirname, "src/components"),
      "@layout": resolve(__dirname, "src/layout"),
    },
  },
  plugins: [
    react(),
    mdx({
      providerImportSource: "@mdx-js/react",
      remarkPlugins: [remarkGfm, remarkWaveDrom],
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
