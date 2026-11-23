// Ghép từ fe_web_admin/services/spring/SpringConfigHeader.js.
// Tạo axios client trỏ vào Spring Boot (/api/web/admin/...) + tự làm mới token ngầm.
import axios from 'axios'
import { API_BASE, REQUEST_TIMEOUT_MS } from '../config.js'
import { silentRefresher } from './SilentTokenRefresher.js'

export default class SpringConfigHeader {
  #createClient(extraHeaders = {}) {
    return axios.create({
      baseURL: API_BASE,
      timeout: REQUEST_TIMEOUT_MS,
      headers: {
        'Content-Type': 'application/json',
        ...extraHeaders,
      },
    })
  }

  // Không có header xác thực (đăng nhập, API công khai)
  getAPIClientNoHeaders() {
    return this.#createClient()
  }

  // JWT access/refresh token: Authorization: Bearer <token>
  getAPIClientWithHeadersToken(token) {
    return this.#createClient({
      Authorization: `Bearer ${token}`,
    })
  }

  // Client cho API cần đăng nhập, TỰ làm mới phiên ngầm (giữ nguyên logic fe_web_admin):
  //  - Trước mỗi request: access token sắp hết hạn thì làm mới trước, gắn token mới nhất.
  //  - Server vẫn trả 401: làm mới ngầm rồi gửi lại ĐÚNG MỘT lần.
  // KHÔNG dùng cho chính API đăng nhập / làm mới token (tránh lặp vô hạn).
  getAuthorizedAPIClient(tokenLogin) {
    const client = this.#createClient()

    client.interceptors.request.use(async (config) => {
      await silentRefresher.ensureFresh()
      const token = tokenLogin.getAccessTokenFromStorage()
      if (token) {
        config.headers.set('Authorization', `Bearer ${token}`)
      }
      return config
    })

    client.interceptors.response.use(undefined, async (error) => {
      const config = error.config
      if (error.response?.status !== 401
        || !config
        || config.__authRetried
        || !silentRefresher.isReady()) {
        return Promise.reject(error)
      }
      config.__authRetried = true

      const sentToken = String(config.headers?.get?.('Authorization') ?? '')
        .replace(/^Bearer\s+/i, '') || null
      const refreshed = await silentRefresher.refreshNow(sentToken)
      if (!refreshed) {
        return Promise.reject(error)
      }
      return client.request(config)
    })

    return client
  }

  getAuthorization() {
    return {
      withCredentials: true,
    }
  }
}
