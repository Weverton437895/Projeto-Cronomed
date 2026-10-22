import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// Em desenvolvimento, o Vite encaminha /api para o backend (evita CORS e mantem os cookies HttpOnly no mesmo site).
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const target = env.VITE_API_PROXY || 'http://localhost:8080';
  const proxy = { '/api': { target, changeOrigin: false } };
  return {
    plugins: [react()],
    server: { port: 5173, proxy },
    preview: { port: 4173, proxy },
    test: { environment: 'node', include: ['src/**/*.test.js'] },
  };
});
