import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => ({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': mode === 'demo' ? 'http://127.0.0.1:8090' : process.env.VITE_PROXY_TARGET ?? 'http://localhost:8080',
    },
  },
}));
