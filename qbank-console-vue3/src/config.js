// Cấu hình mạng cho QBank Console (ghép từ fe_web_admin/services/NetworkConfiguration.js).
// Ưu tiên VITE_API_BASE_URL (đầy đủ, vd http://localhost:8080/api/web hoặc /api/web qua proxy).
// Nếu không có, ghép từ VITE_SPRING_* giống fe_web_admin để giữ cùng một .env.
const ENV = import.meta.env

function readString(key, fallback) {
  const v = String(ENV[key] ?? '').trim()
  return v === '' ? fallback : v
}
function readPrefix(key, fallback) {
  const v = readString(key, fallback)
  return (v.startsWith('/') ? v : `/${v}`).replace(/\/+$/, '')
}
function readNumber(key, fallback) {
  const v = Number.parseInt(String(ENV[key] ?? '').trim(), 10)
  return Number.isNaN(v) ? fallback : v
}

function buildBase() {
  const direct = String(ENV.VITE_API_BASE_URL ?? '').trim()
  if (direct) return direct.replace(/\/+$/, '')
  const proto = readString('VITE_SPRING_PROTOCOL', 'http')
  const host = readString('VITE_SPRING_HOST', 'localhost')
  const port = readNumber('VITE_SPRING_PORT', 8080)
  const prefix = readPrefix('VITE_SPRING_API_PREFIX', '/api/web')
  return `${proto}://${host}:${port}${prefix}`
}

export const API_BASE = buildBase()
export const REQUEST_TIMEOUT_MS = readNumber('VITE_REQUEST_TIMEOUT_MS', 15000)
// Demo mode: CHỈ để xem UI khi chưa có Spring Boot/MariaDB/Redis. Production phải để false.
// Khi true, đăng nhập bỏ qua backend và dùng dữ liệu mẫu (xem auth/authStore.js).
export const DEMO_MODE = String(ENV.VITE_DEMO_MODE ?? '').toLowerCase() === 'true'
