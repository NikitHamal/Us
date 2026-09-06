import { defineConfig } from 'vite';

/**
 * Vite configuration.
 *
 * `allowedHosts: true` is required because the dev server is reached through a
 * proxied preview host rather than localhost.
 */
export default defineConfig({
  server: {
    host: '0.0.0.0',
    port: 5173,
    allowedHosts: true,
    strictPort: true,
  },
  preview: {
    host: '0.0.0.0',
    allowedHosts: true,
  },
  build: {
    target: 'es2022',
    sourcemap: true,
  },
});
