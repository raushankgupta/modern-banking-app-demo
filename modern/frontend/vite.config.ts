/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// In dev, /api is proxied to the Spring Boot backend, so no CORS setup is needed.
// VITE_PROXY_TARGET overrides the backend address (default http://localhost:8080).
const proxyTarget = process.env.VITE_PROXY_TARGET ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': { target: proxyTarget, changeOrigin: true } },
  },
  css: { preprocessorOptions: { scss: { quietDeps: true } } },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
});
