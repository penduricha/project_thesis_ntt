// Ghép từ fe_web_admin/services/spring/SpringConfigHeader.js.
// Tạo axios client trỏ vào Spring Boot (/api/web/admin/...) + tự làm mới token ngầm.
import axios from 'axios'
import { API_BASE, REQUEST_TIMEOUT_MS } from '../config.js'
import { silentRefresher } from './SilentTokenRefresher.js'
import { markOk, markError } from '../netStatus.js'

export default class SpringConfigHeader {
  #createClient(extraHeaders = {}) {
    const client = axios.create({
      baseURL: API_BASE,
      timeout: REQUEST_TIMEOUT_MS,
      headers: {
        'Content-Type': 'application/json',
        ...extraHeaders,
      },
    })

    // Theo dõi tình trạng kết nối ở tầng thấp nhất: MỌI client tạo ở đây đều đi qua đây,
    // nên không cần rải try/catch ở từng hàm gọi API trong repository.
    //  - markOk()    : có phản hồi HTTP (kể cả 4xx/5xx nghiệp vụ) => mạng vẫn thông.
    //  - markError() : chỉ bật cờ "mất mạng" với lỗi mạng thật hoặc 502/503/504 (xem netStatus.js).
    client.interceptors.response.use(
      (response) => {
        markOk()
        return response
      },
      (error) => {
        markError(error)
        return Promise.reject(error)
      },
    )

    return client
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
