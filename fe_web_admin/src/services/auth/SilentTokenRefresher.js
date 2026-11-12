// src/services/auth/SilentTokenRefresher.js
//
// Làm mới access token NGẦM bằng refresh token, người dùng không thấy gì.
// Ba lớp bảo vệ:
//   1. Chủ động : hẹn giờ, làm mới khi access token còn <= REFRESH_BEFORE_MS (đọc `exp` trong JWT).
//   2. Thức dậy : tab nền / máy ngủ làm setTimeout bị trễ -> kiểm tra lại khi tab hiện lại hoặc có mạng lại.
//   3. Bị động  : API trả 401 -> làm mới 1 lần rồi gửi lại request (hàm retryOn401).
// Chống lặp: nhiều request / nhiều tab cùng hết hạn thì chỉ MỘT lần gọi refresh (single-flight + Web Locks).

const REFRESH_BEFORE_MS = 60_000            // access token sống 5 phút -> làm mới khi còn <= 60 giây
const MIN_DELAY_MS = 5_000                  // chặn vòng lặp dày nếu server trả token có exp không đổi
const FALLBACK_INTERVAL_MS = 4 * 60_000     // token không phải JWT (không đọc được exp) -> làm mới định kỳ
const MAX_TIMEOUT_MS = 2 ** 31 - 1          // giới hạn của setTimeout
const RETRY_DELAYS_MS = [5_000, 15_000, 30_000] // lỗi mạng: thử lại, KHÔNG đăng xuất
const LOCK_NAME = 'auth-token-refresh'

/** Đọc `exp` (ms) từ JWT. Trả null nếu không phải JWT. */
function decodeExpMs(token) {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const { exp } = JSON.parse(atob(payload))
    return typeof exp === 'number' ? exp * 1000 : null
  } catch {
    return null
  }
}

class SilentTokenRefresher {
  constructor() {
    this.tokenLogin = null
    this.adminService = null
    this.onSessionExpired = null
    this.timer = null
    this.retryTimer = null
    this.retryCount = 0
    this.inflight = null
    this.started = false
  }

  /** Gọi 1 lần ở main.js. */
  init({ tokenLogin, adminService, onSessionExpired }) {
    this.tokenLogin = tokenLogin
    this.adminService = adminService
    this.onSessionExpired = onSessionExpired
  }

  /** Bắt đầu chạy ngầm. Idempotent: gọi nhiều lần vẫn chỉ đăng ký 1 bộ listener. */
  start() {
    if (this.started || !this.tokenLogin) return
    this.started = true
    document.addEventListener('visibilitychange', this.handleWake)
    window.addEventListener('online', this.handleWake)
    window.addEventListener('storage', this.handleStorage) // tab khác vừa làm mới -> hẹn lại giờ
    this.schedule()
  }

  /** Gọi khi đăng xuất. */
  stop() {
    if (!this.started) return
    this.started = false
    clearTimeout(this.timer)
    clearTimeout(this.retryTimer)
    this.retryCount = 0
    document.removeEventListener('visibilitychange', this.handleWake)
    window.removeEventListener('online', this.handleWake)
    window.removeEventListener('storage', this.handleStorage)
  }

  // ---------- Hẹn giờ ----------

  handleWake = () => {
    if (document.visibilityState === 'hidden') return
    this.tick()
  }

  handleStorage = () => this.schedule()

  getExpMs() {
    return decodeExpMs(this.tokenLogin.getAccessTokenFromStorage() || '')
  }

  /** Còn hạn nhưng đã tới lúc làm mới? */
  isDue() {
    const exp = this.getExpMs()
    return exp !== null && exp - Date.now() <= REFRESH_BEFORE_MS
  }

  tick() {
    if (this.isDue()) this.refreshNow()
    else this.schedule()
  }

  schedule() {
    clearTimeout(this.timer)
    if (!this.started) return

    const exp = this.getExpMs()
    const delay = exp === null
        ? FALLBACK_INTERVAL_MS
        : Math.max(exp - Date.now() - REFRESH_BEFORE_MS, MIN_DELAY_MS)

    this.timer = setTimeout(() => this.tick(), Math.min(delay, MAX_TIMEOUT_MS))
  }

  // ---------- Làm mới ----------

  /**
   * Làm mới ngay. Nhiều nơi gọi cùng lúc vẫn chỉ chạy 1 lần.
   * @param staleToken access token vừa bị server từ chối (401). Nếu storage đã có token KHÁC
   *                   (tab khác làm mới rồi) thì dùng luôn, không gọi server nữa.
   * @returns {Promise<boolean>} true = đã có access token mới dùng được
   */
  refreshNow(staleToken = null) {
    if (!this.inflight) {
      this.inflight = this.runExclusive(() => this.doRefresh(staleToken))
          .finally(() => { this.inflight = null })
    }
    return this.inflight
  }

  /** Web Locks: chỉ 1 tab được gọi refresh tại một thời điểm (refresh token có thể bị xoay vòng). */
  runExclusive(fn) {
    return navigator.locks ? navigator.locks.request(LOCK_NAME, fn) : fn()
  }

  async doRefresh(staleToken) {
    const storedAccess = this.tokenLogin.getAccessTokenFromStorage()
    const storedRefresh = this.tokenLogin.getRefreshTokenFromStorage()

    // Đồng bộ token trong bộ nhớ của tab này với storage (tab khác có thể đã đổi)
    this.tokenLogin.setAccessToken(storedAccess)
    this.tokenLogin.setRefreshToken(storedRefresh)

    // Tab khác đã làm mới xong trong lúc ta chờ khóa -> khỏi gọi server
    const alreadyRefreshed = staleToken ? storedAccess !== staleToken : !this.isDue() && this.getExpMs() !== null
    if (storedAccess && alreadyRefreshed) {
      this.afterSuccess()
      return true
    }

    if (!storedRefresh) {
      this.expire()
      return false
    }

    try {
      // Dùng lại đúng hàm auto login ở main.js: gửi refresh token, nhận cặp token mới
      const ok = Boolean(await this.adminService.getDataToAutoLoginAccessToken(this.tokenLogin))
      if (!ok) {
        this.expire() // server từ chối refresh token (hết hạn / bị thu hồi) -> phải đăng nhập lại
        return false
      }
      this.afterSuccess()
      return true
    } catch (error) {
      // Lỗi mạng / server tạm thời: giữ phiên, thử lại sau
      console.warn('Silent refresh failed, will retry:', error)
      const delay = RETRY_DELAYS_MS[Math.min(this.retryCount++, RETRY_DELAYS_MS.length - 1)]
      clearTimeout(this.retryTimer)
      this.retryTimer = setTimeout(() => this.refreshNow(), delay)
      return false
    }
  }

  afterSuccess() {
    this.retryCount = 0
    clearTimeout(this.retryTimer)
    this.schedule()
  }

  expire() {
    this.stop()
    this.onSessionExpired?.()
  }
}

export const silentRefresher = new SilentTokenRefresher()

/**
 * Bọc 1 request kiểu fetch: nếu 401 -> làm mới ngầm rồi gửi lại ĐÚNG 1 lần.
 * `sendRequest` phải đọc access token mới nhất mỗi lần được gọi, ví dụ:
 *
 *   const res = await retryOn401(() => fetch(url, {
 *     headers: { Authorization: `Bearer ${tokenLogin.getAccessTokenFromStorage()}` },
 *   }))
 *
 * KHÔNG bọc chính API login / refresh bằng hàm này (tránh lặp vô hạn).
 */
export async function retryOn401(sendRequest, tokenLogin) {
  const staleToken = tokenLogin.getAccessTokenFromStorage()
  let response = await sendRequest()
  if (response.status !== 401) return response

  const refreshed = await silentRefresher.refreshNow(staleToken)
  if (!refreshed) return response // phiên đã hết hoặc đang lỗi mạng: trả 401 gốc cho nơi gọi xử lý

  response = await sendRequest()
  return response
}
