import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// https://vite.dev/config/
export default defineConfig(({ command, mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_');

  if (command === 'build' && process.env.VERCEL && !env.VITE_API_URL) {
    throw new Error('Configura VITE_API_URL en Vercel antes de publicar el frontend.');
  }

  return { plugins: [react(), tailwindcss()] };
});
