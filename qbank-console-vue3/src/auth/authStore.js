// Store xác thực dùng chung cho QBank Console (ghép từ fe_web_admin main.js + AdminRepository).
// Dùng reactive() giống store.js của qbank (không thêm Pinia để giữ nguyên kiến trúc).
import { reactive } from 'vue'
import { DEMO_MODE } from '../config.js'
import { TokenLogin } from './TokenLogin.js'
import AdminStorage from './AdminStorage.js'
import AdminRepository, { REQUEST_STATUS } from './AdminRepository.js'
import { silentRefresher } from './SilentTokenRefresher.js'
import { broadcastLogout, onRemoteLogout } from './auth-sync.js'
import { ME } from '../data/account.js'

const DEMO_KEY = 'qbank.demoSession'

export const auth = reactive({
  isAuthenticated: false,
  isDemo: false,
  adminId: null,
  adminName: '',
  // 'admin' | 'gv' — map từ Role ADMIN/LECTURER của backend (Admin.role).
  // Backend /information/basic hiện KHÔNG trả role, nên mặc định 'admin' và
  // giảng viên dùng "Chế độ xem theo vai trò" ở Cài đặt để xem thử (chỉ đổi UI).
  role: 'admin',
  basic: null,   // GET /information/basic -> { adminId, adminName, gender, dateOfBirthAdmin }
  detail: null,  // GET /information/detail -> { address, idCard, faculty, industry, ... }
  booted: false, // đã chạy auto-login lúc khởi động chưa
})

const tokenLogin = new TokenLogin()
const adminStorage = new AdminStorage()
const adminRepository = new AdminRepository()

function applyDemoSession() {
  auth.isDemo = true
  auth.isAuthenticated = true
  auth.adminId = ME.adminId
  auth.adminName = ME.name
  auth.role = 'admin'
  auth.basic = { adminId: ME.adminId, adminName: ME.name, gender: ME.gender, dateOfBirthAdmin: ME.dob }
  auth.detail = {
    address: ME.address, idCard: ME.idCard,
    faculty: ME.course.faculty, industry: ME.course.industry, major: ME.course.major,
    trainingSystem: ME.course.trainingSystem, typeOfTraining: ME.course.typeOfTraining,
    branch: ME.course.branch,
  }
}

function clearLocalSession() {
  silentRefresher.stop()
  try {
    tokenLogin.removeAccessTokenAndRefreshTokenFromStorage()
    adminStorage.removeAdminIdFromLocalStorage()
    localStorage.removeItem(DEMO_KEY)
    localStorage.removeItem('routerPathLocalStorage')
    sessionStorage.clear()
  } catch { /* trình duyệt chặn storage: bỏ qua */ }
  auth.isAuthenticated = false
  auth.isDemo = false
  auth.adminId = null
  auth.adminName = ''
  auth.basic = null
  auth.detail = null
}

/** Đăng nhập: adminId + password + totpCode (tùy chọn). Trả về LoginResult (xem AdminRepository). */
export async function login(adminId, password, totpCode = null) {
  if (DEMO_MODE) {
    if (!adminId || !password) return { status: REQUEST_STATUS.FAILED }
    localStorage.setItem(DEMO_KEY, '1')
    applyDemoSession()
    return { status: REQUEST_STATUS.OK }
  }
  const result = await adminRepository.getLoginResult(Number(adminId), String(password), totpCode || null)
  if (result.getStatus() === REQUEST_STATUS.OK) {
    auth.isAuthenticated = true
    auth.isDemo = false
    auth.adminId = Number(adminId)
    silentRefresher.start()
    await loadMe()
  }
  return result
}

/** Đăng xuất thật: gọi server hủy token, dừng làm mới ngầm, báo các tab khác. */
export async function logout() {
  if (auth.isDemo) {
    clearLocalSession()
    return REQUEST_STATUS.OK
  }
  const status = await adminRepository.getResponseFromLogoutRequest()
  if (status === REQUEST_STATUS.OK) {
    broadcastLogout()
    clearLocalSession()
  }
  return status
}

/** Tải thông tin cơ bản + chi tiết của admin đang đăng nhập. Lỗi mạng -> giữ null, không đăng xuất. */
export async function loadMe() {
  if (auth.isDemo) return
  try {
    const basic = await adminRepository.getAdminInformationBasicByAdminId()
    if (basic) {
      auth.basic = basic
      auth.adminId = basic.adminId ?? auth.adminId
      auth.adminName = basic.adminName ?? ''
    }
  } catch (e) {
    console.warn('[auth] Không tải được thông tin cơ bản:', e?.message)
  }
  try {
    auth.detail = await adminRepository.getAdminInformationDetailByAdminId()
  } catch (e) {
    console.warn('[auth] Không tải được thông tin chi tiết:', e?.message)
  }
}

/**
 * Auto-login lúc khởi động (giống fe_web_admin main.js bootstrap):
 * có refresh token thì thử đổi access token mới. Không bao giờ throw.
 */
export async function bootstrapAuth() {
  if (DEMO_MODE && localStorage.getItem(DEMO_KEY)) {
    applyDemoSession()
    auth.booted = true
    return true
  }
  // Cấu hình bộ làm mới ngầm (chỉ init 1 lần; start() khi đã đăng nhập)
  silentRefresher.init({
    tokenLogin,
    adminService: adminRepository.adminService,
    onSessionExpired: () => {
      clearLocalSession()
      if (location.hash !== '#/login') location.hash = '#/login'
    },
  })
  onRemoteLogout(() => {
    clearLocalSession()
    if (location.hash !== '#/login') location.hash = '#/login'
  })

  const accessToken = tokenLogin.getAccessTokenFromStorage()
  const refreshToken = tokenLogin.getRefreshTokenFromStorage()
  tokenLogin.setAccessToken(accessToken)
  tokenLogin.setRefreshToken(refreshToken)

  let isLoggedIn = false
  if (refreshToken) {
    try {
      isLoggedIn = Boolean(await adminRepository.loginService.getStatusAutoLogin())
    } catch (e) {
      console.warn('[auth] Auto login failed:', e?.message)
    }
  }
  if (isLoggedIn) {
    auth.isAuthenticated = true
    auth.isDemo = false
    auth.adminId = adminStorage.getAdminIdToLocalStorage()
    silentRefresher.start()
    await loadMe()
  }
  auth.booted = true
  return isLoggedIn
}
