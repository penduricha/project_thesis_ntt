import './assets/main.css'
import { createApp } from 'vue'
import App from './App.vue'

// Bootstrap
import 'bootstrap/dist/css/bootstrap.css'
import 'bootstrap/dist/js/bootstrap.js'

// Vue Router & Vuetify
import { createRouter, createWebHistory } from 'vue-router';
import { createVuetify } from 'vuetify';
import 'vuetify/styles';
import 'vuetify/dist/vuetify-labs.min.css';


// Routers / Services / Models
import RouterMgmt from '@/router/RouterMgmt.js'
import routersBeforeLogin from '@/router/routers_before_login.js'
import routersAfterLogin from '@/router/routers_after_login.js'
import { AdminService } from '@/services/spring/AdminService.js'
import { TokenLogin } from '@/model/TokenLogin.js'
import { onRemoteLogout } from '@/assets/auth-sync.js'
import { silentRefresher } from '@/services/auth/SilentTokenRefresher.js'

const LOGIN_PATH = '/login'
const NOT_FOUND_PATH = '/404-not-found'

const adminService = new AdminService()
const tokenLogin = new TokenLogin()
const routerManagement = new RouterMgmt()

/**
 * Dọn trạng thái cục bộ rồi tải lại trang /login.
 * Dùng chung cho 2 trường hợp: tab khác đã đăng xuất, và refresh token bị server từ chối.
 *
 * Dùng window.location.replace thay vì router.replace vì app dùng 2 bộ route
 * (routersBeforeLogin / routersAfterLogin): bộ "sau đăng nhập" có thể không có
 * '/login'. Tải lại sẽ chạy lại bootstrap() và nạp đúng bộ route "trước đăng nhập".
 * Cách này cũng không kích hoạt beforeRouteLeave nên không gọi API đăng xuất lần 2.
 */
function clearSessionAndGoToLogin() {
    silentRefresher.stop() // dừng làm mới ngầm trước khi rời trang

    if (window.location.pathname === LOGIN_PATH) return

    try {
        routerManagement.removePathFromLocalStorage()
        // sessionStorage là riêng từng tab → phải tự xoá ở tab này
        sessionStorage.clear()
    } catch (error) {
        console.error('Error clearing session:', error)
    }

    window.location.replace(LOGIN_PATH)
}

/**
 * Cấu hình bộ làm mới access token ngầm (SilentTokenRefresher).
 * Chỉ init ở đây; việc start() chỉ chạy khi đã đăng nhập (xem bootstrap).
 * Hết hạn refresh token (server từ chối) → về /login. Lỗi mạng thì KHÔNG đăng xuất, tự thử lại.
 */
function setupSilentRefresh() {
    silentRefresher.init({
        tokenLogin,
        adminService,
        onSessionExpired: clearSessionAndGoToLogin,
    })
}

/**
 * Nạp token từ storage và thử auto-login bằng refresh token.
 * Không bao giờ throw: lỗi mạng/server được coi là "chưa đăng nhập"
 * để app vẫn mount được (tránh trang trắng).
 */
async function autoLogin() {
    const accessToken = tokenLogin.getAccessTokenFromStorage()
    const refreshToken = tokenLogin.getRefreshTokenFromStorage()

    tokenLogin.setAccessToken(accessToken)
    tokenLogin.setRefreshToken(refreshToken)

    let isLoggedIn = false

    if (refreshToken) {
        try {
            isLoggedIn = Boolean(await adminService.getDataToAutoLoginAccessToken(tokenLogin))
        } catch (error) {
            console.error('Auto login failed:', error)
        }
    }

    return { isLoggedIn, hasTokens: Boolean(accessToken && refreshToken) }
}

/**
 * Xác định path sẽ điều hướng tới khi khởi động app.
 */
function resolveTargetPath(currentPath, routes, isLoggedIn) {
    const matchedRoute = routes.find((route) => route.path === currentPath)

    if (!isLoggedIn) {
        // Chưa đăng nhập: route không tồn tại hoặc không được phép → về login
        if (!matchedRoute || matchedRoute.allow === false) return LOGIN_PATH
    } else if (!matchedRoute) {
        // Đã đăng nhập nhưng route không tồn tại
        return NOT_FOUND_PATH
    }

    // Đang ở root '/'
    if (currentPath === '/') {
        if (!isLoggedIn) return LOGIN_PATH
        return (
            routerManagement.getPathFromLocalStorage() ||
            routes[0]?.path || // fallback: đổi thành trang chủ của bạn
            NOT_FOUND_PATH
        )
    }

    // Khôi phục path trong SessionStorage (chỉ khi path đó hợp lệ với bộ route hiện tại)
    const pathSession = routerManagement.getPathFromSessionStorage()
    const canRestoreSession =
        pathSession &&
        pathSession !== currentPath &&
        routes.some((route) => route.path === pathSession)

    return canRestoreSession ? pathSession : currentPath
}

/**
 * Đồng bộ đăng xuất giữa các tab.
 * Tab khác đã đăng xuất thành công (server huỷ phiên, token trong localStorage
 * đã bị xoá) → tab này chỉ cần dọn trạng thái cục bộ rồi tải lại trang /login.
 */
function setupCrossTabLogout() {
    onRemoteLogout(clearSessionAndGoToLogin)
}

async function mountApp(routes, targetPath) {
    const app = createApp(App)
    const router = createRouter({
        history: createWebHistory(),
        routes,
    })

    app.use(createVuetify())
    app.use(router)

    try {
        await router.replace(targetPath)
    } catch (error) {
        console.error('Error navigating:', error)
    }

    // Mount sau khi điều hướng xong → không bị nháy sang trang sai
    app.mount('#app')
}

async function bootstrap() {
    // Đăng ký lắng nghe sớm, một lần duy nhất cho cả vòng đời tab
    setupCrossTabLogout()
    setupSilentRefresh()

    const { isLoggedIn, hasTokens } = await autoLogin()

    if (import.meta.env.DEV) {
        console.log('Status login auto:', isLoggedIn)
    }

    // Không đăng nhập được và cũng không có đủ token → xoá path đã lưu
    if (!isLoggedIn && !hasTokens) {
        routerManagement.removePathFromLocalStorage()
    }

    const routes = isLoggedIn ? routersAfterLogin : routersBeforeLogin
    const targetPath = resolveTargetPath(window.location.pathname, routes, isLoggedIn)

    // Đã đăng nhập → chạy ngầm làm mới access token trước khi hết TTL.
    // (Nếu đăng nhập ở trang Login mà không reload, gọi silentRefresher.start() sau khi login thành công.)
    if (isLoggedIn) silentRefresher.start()

    await mountApp(routes, targetPath)
}

await bootstrap()