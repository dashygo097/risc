import { defineConfig } from "vite";
import remarkGfm from "remark-gfm";
import { resolve } from "path";
import react from "@vitejs/plugin-react";
import mdx from "@mdx-js/rollup";
import { remarkWaveDromChart } from "./lib/remark-wavedrom-chart";
import { remarkInfoBox } from "./lib/remark-info-box";

export default defineConfig({
  resolve: {
    alias: {
      "@": resolve(__dirname, "src"),
      "@assets": resolve(__dirname, "src/assets"),
      "@utils": resolve(__dirname, "src/utils"),
      "@styles": resolve(__dirname, "src/styles"),
      "@components": resolve(__dirname, "src/components"),
      "@layout": resolve(__dirname, "src/layout"),
    },
  },
  plugins: [
    react(),
    mdx({
      providerImportSource: "@mdx-js/react",
      remarkPlugins: [remarkGfm, remarkWaveDromChart, remarkInfoBox],
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
