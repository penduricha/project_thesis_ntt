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

  /** Đã init xong (main.js chạy trước khi bất kỳ trang nào gọi API) -> mới dùng được refreshNow / ensureFresh. */
  isReady() {
    return Boolean(this.tokenLogin && this.adminService)
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
   * @param options.notifyExpired false = refresh token bị từ chối thì chỉ trả false, KHÔNG tự chuyển về
   *                   trang đăng nhập (dùng lúc khởi động app, nơi main.js tự chọn route theo kết quả).
   * @returns {Promise<boolean>} true = đã có access token mới dùng được
   */
  refreshNow(staleToken = null, { notifyExpired = true, throwOnUnavailable = false } = {}) {
    if (!this.inflight) {
      this.inflight = this.runExclusive(() => this.doRefresh(staleToken, notifyExpired))
          .finally(() => { this.inflight = null })
    }
    return this.inflight.catch(error => {
      if (throwOnUnavailable) throw error
      return false
    })
  }

  /**
   * Gọi TRƯỚC mỗi request cần đăng nhập: access token sắp hết hạn (hoặc đã hết) thì làm mới ngầm rồi
   * mới cho request đi, để người dùng không bao giờ thấy "Phiên đăng nhập đã hết hạn" chỉ vì đứng yên vài phút.
   * Chưa init / chưa có token / token còn dài hạn thì trả true ngay, không tốn gì.
   */
  async ensureFresh() {
    if (!this.isReady() || !this.isDue()) return true
    return this.refreshNow(null, { throwOnUnavailable: true })
  }

  /** Web Locks: chỉ 1 tab được gọi refresh tại một thời điểm (refresh token có thể bị xoay vòng). */
  runExclusive(fn) {
    return navigator.locks ? navigator.locks.request(LOCK_NAME, fn) : fn()
  }

  async doRefresh(staleToken, notifyExpired = true) {
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
      this.expire(notifyExpired)
      return false
    }

    try {
      // Gọi THẲNG endpoint refresh-token và lưu cặp token mới (xoay vòng).
      // Bản cũ gọi hàm auto login bằng access token: khi access token còn hạn server trả 200 mà KHÔNG cấp
      // token mới, nên "làm mới chủ động" thực ra không làm mới gì, chỉ đến khi token hết hạn mới đổi được.
      const ok = Boolean(await this.adminService.getDataToRenewTokens(this.tokenLogin))
      if (!ok) {
        this.expire(notifyExpired) // server từ chối refresh token (hết hạn / bị thu hồi) -> phải đăng nhập lại
        return false
      }
      this.afterSuccess()
      return true
    } catch (error) {
      // Lỗi mạng / server tạm thời: giữ phiên, thử lại sau
      if (error?.code !== 'AUTH_SESSION_CHANGED') {
        console.warn('Silent refresh failed, will retry:', error)
        if (this.started) {
          const delay = RETRY_DELAYS_MS[Math.min(this.retryCount++, RETRY_DELAYS_MS.length - 1)]
          clearTimeout(this.retryTimer)
          this.retryTimer = setTimeout(() => this.refreshNow(), delay)
        }
      }
      throw error
    }
  }

  afterSuccess() {
    this.retryCount = 0
    clearTimeout(this.retryTimer)
    this.schedule()
  }

  expire(notify = true) {
    this.stop()
    if (notify) this.onSessionExpired?.()
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

  const refreshed = await silentRefresher.refreshNow(staleToken, { throwOnUnavailable: true })
  if (!refreshed) return response // phiên đã hết hoặc đang lỗi mạng: trả 401 gốc cho nơi gọi xử lý

  response = await sendRequest()
  return response
}
