import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './assets/main.css'
import { bootstrapAuth } from './auth/authStore.js'

// Auto-login bằng refresh token trước khi mount (giống fe_web_admin main.js bootstrap),
// để router guard biết đã đăng nhập hay chưa ngay từ lần điều hướng đầu tiên.
async function bootstrap() {
  try {
    await bootstrapAuth()
  } catch (e) {
    console.warn('[auth] bootstrap failed:', e?.message)
  }
  createApp(App).use(router).mount('#root')
}

bootstrap().then(r => {})
