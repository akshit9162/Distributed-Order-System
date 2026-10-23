import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Builds into Spring Boot's static folder, so the jar and the Docker image serve the page with no extra step.
// `npm run dev` serves it on :5173 and proxies the API to an auction-service running on :8084.
export default defineConfig({
  plugins: [react()],
  build: { outDir: '../src/main/resources/static', emptyOutDir: true },
  server: { proxy: { '/auctions': 'http://localhost:8084' } },
  test: { environment: 'jsdom', setupFiles: './src/setupTests.js' },
});
