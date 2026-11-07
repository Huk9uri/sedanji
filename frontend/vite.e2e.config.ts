import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  envDir: false,
  define: { 'import.meta.env.VITE_KAKAO_JAVASCRIPT_KEY': JSON.stringify('') },
  server: { host: '127.0.0.1', port: 18173, strictPort: true,
    proxy: { '/api': { target: 'http://127.0.0.1:18090', changeOrigin: true } } },
});
