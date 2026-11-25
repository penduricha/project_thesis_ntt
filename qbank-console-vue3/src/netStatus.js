/**
 * Theo dõi tình trạng kết nối tới backend.
 *
 * VẤN ĐỀ CẦN GIẢI QUYẾT
 * Trước module này, khi Spring Boot không chạy, ứng dụng chỉ ghi `console.warn`
 * rồi rơi về dữ liệu mẫu (mock). Người dùng thấy dữ liệu hiển thị bình thường,
 * không biết mình đang xem dữ liệu giả — nguy hiểm khi demo trước hội đồng vì
 * thao tác "thành công" nhưng thực ra chưa hề gọi backend.
 *
 * CÁCH HOẠT ĐỘNG — hai nguồn tín hiệu, vì mỗi nguồn có điểm mù riêng:
 *
 *   1. `navigator.onLine` + sự kiện online/offline của trình duyệt.
 *      Bắt được: rút dây mạng, tắt Wi-Fi, mất 4G.
 *      KHÔNG bắt được: mạng vẫn lên Google nhưng server Spring ở localhost chết.
 *
 *   2. Kết quả của các request thật (interceptor axios báo về).
 *      Bắt được: server không chạy, sai cổng, backend trả 502/503.
 *      KHÔNG bắt được: khi người dùng chưa thao tác gì (chưa phát sinh request).
 *
 * Gộp cả hai thì phủ được hầu hết trường hợp thực tế. Đây cũng là điều yêu cầu
 * đề bài nói tới: "Nếu server spring KHÔNG chạy, vuejs nhanh chóng thông báo".
 *
 * KIỂM TRA ĐỊNH KỲ
 * Có thêm `startHealthCheck()` gọi HEAD tới backend mỗi 30s. Chỉ chạy khi tab
 * đang hiển thị (visibilitychange) — không tốn pin/CPU khi người dùng ở tab khác,
 * và tránh dồn hàng trăm request khi mở nhiều tab.
 */

import { reactive, computed } from 'vue'
import { API_BASE } from './config.js'

/** 'unknown' lúc chưa biết, 'online' khi gọi được backend, 'offline' khi không. */
export const net = reactive({
  status: 'unknown',
  lastErrorAt: 0,
  lastOkAt: 0,
})

/** Trình duyệt báo mất mạng (khác với server chết). */
export const browserOffline = typeof navigator !== 'undefined' && navigator.onLine === false

export const isOffline = computed(() => net.status === 'offline')
export const isOnline = computed(() => net.status === 'online')

/* ---------------------------------------------------------------------------
 * Ghi nhận kết quả request
 * ------------------------------------------------------------------------- */

/**
 * Request thành công -> đánh dấu online nếu trước đó đang offline.
 * Trả về true nếu trạng thái VỪA đổi (để nơi gọi biết có cần thông báo).
 */
export function markOk() {
  net.lastOkAt = Date.now()
  if (net.status === 'online') return false
  net.status = 'online'
  return true
}

/**
 * Request thất bại. Chỉ coi là mất kết nối khi lỗi thuộc loại "không tới được
 * server" — KHÔNG phải mọi lỗi:
 *
 *   - `!error.response` : axios không nhận được response (mất mạng, server chết,
 *     sai cổng, DNS lỗi). Đây đúng là "không thể kết nối".
 *   - timeout (ECONNABORTED) : server có thể đang chạy nhưng chậm; vẫn tính là
 *     lỗi kết nối vì với người dùng thì kết quả giống nhau.
 *   - 502 / 503 / 504 : có proxy đứng giữa (NGINX) nhưng backend chết. Với người
 *     dùng cũng là "không kết nối được" — và đây chính là trường hợp sẽ gặp khi
 *     chạy sau NGINX mà container Spring chưa lên.
 *
 * KHÔNG tính là mất kết nối: 400/401/403/404/409/422/500 (server trả lời được,
 * chỉ là nghiệp vụ sai hoặc chưa đăng nhập). Nếu gộp những lỗi này vào thì
 * người dùng nhập sai mật khẩu cũng thấy "Không thể kết nối mạng" — sai bản chất.
 */
export function markError(error) {
  if (!isConnectionError(error)) return false
  net.lastErrorAt = Date.now()
  if (net.status === 'offline') return false
  net.status = 'offline'
  return true
}

export function isConnectionError(error) {
  // Không có response = không tới được server.
  if (!error || !error.response) {
    // Axios đặt error.code = 'ECONNABORTED' khi quá timeout,
    // 'ERR_NETWORK' khi mạng lỗi, 'ERR_CANCELED' khi bị huỷ (không phải lỗi).
    if (error?.code === 'ERR_CANCELED') return false
    return true
  }
  // Có response nhưng là lỗi của hạ tầng, không phải của nghiệp vụ.
  const s = error.response.status
  return s === 502 || s === 503 || s === 504
}

/* ---------------------------------------------------------------------------
 * Trạng thái mạng của trình duyệt
 * ------------------------------------------------------------------------- */

let browserListenersBound = false

export function watchBrowserNetwork() {
  if (browserListenersBound || typeof window === 'undefined') return
  browserListenersBound = true

  window.addEventListener('offline', () => {
    net.status = 'offline'
    net.lastErrorAt = Date.now()
  })
  window.addEventListener('online', () => {
    // Có mạng trở lại KHÔNG có nghĩa là backend đã sống lại.
    // Đặt về 'unknown' để lần request kế tiếp tự xác định.
    net.status = 'unknown'
  })
}

/* ---------------------------------------------------------------------------
 * Kiểm tra định kỳ
 * ------------------------------------------------------------------------- */

let timer = null
let running = false

/**
 * Gọi HEAD tới backend để biết nó có sống không.
 *
 * Dùng `no-cors` + `cache: 'no-store'`: ở chế độ no-cors ta KHÔNG đọc được mã
 * trạng thái, nhưng biết được promise có resolve hay không — mà đó chính là
 * điều cần biết. Dùng no-cors còn tránh ghi log CORS rác vào console.
 *
 * `cache: 'no-store'` để trình duyệt không trả kết quả cũ từ cache — nếu không,
 * server vừa chết mà vẫn "ping thấy" do cache.
 */
export async function probe() {
  if (typeof fetch !== 'function') return

  // Ping vào GỐC của server, không phải vào đường dẫn API có prefix.
  // API_BASE có dạng "http://localhost:8080/api/web" hoặc "/api/web".
  // Với URL tuyệt đối thì lấy origin; với đường dẫn tương đối (chạy sau
  // NGINX) thì "/" cũng chính là server đang phục vụ trang — ping "/" là đủ.
  let target = '/'
  try {
    if (/^https?:\/\//i.test(API_BASE)) target = new URL(API_BASE).origin
  } catch {
    // API_BASE không phải URL hợp lệ -> dùng "/", không để việc này làm hỏng.
  }

  // AbortSignal.timeout chỉ có từ một số phiên bản trình duyệt; nếu thiếu thì
  // dùng AbortController để vẫn có giới hạn thời gian (nếu không, fetch có thể
  // treo rất lâu khi cổng bị firewall nuốt gói tin).
  let signal
  let abortId
  if (typeof AbortSignal !== 'undefined' && typeof AbortSignal.timeout === 'function') {
    signal = AbortSignal.timeout(4000)
  } else if (typeof AbortController !== 'undefined') {
    const ctrl = new AbortController()
    abortId = setTimeout(() => ctrl.abort(), 4000)
    signal = ctrl.signal
  }

  try {
    await fetch(target, { method: 'HEAD', mode: 'no-cors', cache: 'no-store', signal })
    markOk()
  } catch {
    net.status = 'offline'
    net.lastErrorAt = Date.now()
  } finally {
    if (abortId) clearTimeout(abortId)
  }
}

/**
 * Bật kiểm tra định kỳ. Mặc định 30s.
 * Chỉ chạy khi tab đang hiển thị; quay lại tab thì kiểm tra ngay một lần.
 * Trả về hàm huỷ — gọi trong onBeforeUnmount.
 */
export function startHealthCheck(intervalMs = 30000) {
  if (running || typeof window === 'undefined') return () => {}
  running = true

  const tick = () => {
    if (document.visibilityState !== 'visible') return
    probe()
  }

  timer = window.setInterval(tick, intervalMs)
  const onVisible = () => { if (document.visibilityState === 'visible') probe() }
  document.addEventListener('visibilitychange', onVisible)
  // Kiểm tra ngay sau khi trang tải để biết sớm, không phải đợi 30s đầu.
  window.setTimeout(tick, 1500)

  return () => {
    running = false
    if (timer) window.clearInterval(timer)
    timer = null
    document.removeEventListener('visibilitychange', onVisible)
  }
}
