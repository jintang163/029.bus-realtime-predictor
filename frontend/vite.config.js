import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [
    vue()
  ],
  server: {
    port: 3000,
    proxy: {
      '/api/vehicle': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/api/traffic': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true
      },
      '/api/route': {
        target: 'http://localhost:8083',
        changeOrigin: true
      },
      '/api/stopboard': {
        target: 'http://localhost:8083',
        changeOrigin: true
      }
    }
  }
})
