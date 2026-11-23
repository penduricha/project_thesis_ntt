import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: true,
    port: 5173,
    // Cho phép preview của sandbox (https://<port>-<id>.e2b.app) và mọi domain dev khác.
    // Chỉ áp dụng cho dev server, không ảnh hưởng bản build production.
    allowedHosts: true,
    strictPort: false,
    cors: true,
    // Khi VITE_API_BASE_URL=/api/web (tương đối) thì proxy sang Spring Boot local,
    // tránh CORS lúc dev. Khi dùng URL tuyệt đối (mặc định) thì không cần proxy.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  preview: {
    host: true,
    port: 5173,
  },
})
