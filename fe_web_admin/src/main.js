// import './assets/main.css'
// import { createApp } from 'vue'
// import App from './App.vue'
//
// // Bootstrap
// import 'bootstrap/dist/css/bootstrap.css'
// import 'bootstrap/dist/js/bootstrap.js'
//
// // Vue Router & Vuetify
// import { createRouter, createWebHistory } from 'vue-router'
// import { createVuetify } from 'vuetify'
// import 'vuetify/styles'
// import 'vuetify/dist/vuetify-labs.min.css'
//
// // Plugins & Local Storage / Routers
// import CanvasJSChart from '@canvasjs/vue-charts'
// import AdminLocalStorage from "@/local_storage/AdminLocalStorage.js"
// import RouterManagement from "@/routers/RouterManagement.js"
// import routersBeforeLogin from "@/routers/routers_before_login.js"
// import routersAfterLogin from "@/routers/routers_after_login.js"
// import {AdminService} from "@/services/spring/AdminService.js";
// import {TokenLogin} from "@/model/TokenLogin.js";
//
// const adminService = new AdminService();
//
// const tokenLogin = new TokenLogin();
//
// const app = createApp(App)
// app.use(createVuetify())
//
// function initApp(routes, targetPath) {
//     const router = createRouter({
//         history: createWebHistory(),
//         routes,
//     })
//
//     app.use(router)
//
//     router.replace(targetPath).catch((error) => {
//         console.error('Error navigating:', error)
//     })
//
//     app.mount('#app')
// }
//
// async function execute() {
//     //const adminLocalStorage = new AdminLocalStorage()
//     // const isLoggedIn = Boolean(
//     //     routerManagement.getPath_From_LocalStorage() &&
//     //     adminLocalStorage.getAdminId_From_LocalStorage() &&
//     //     tokenLogin.getRefreshTokenFromStorage()
//     // )
//
//     const routerManagement = new RouterManagement();
//
//     const currentPath = window.location.pathname;
//
//     // Flag login auto
//     let statusLoginAuto = false;
//
//     let accessToken = tokenLogin.getAccessTokenFromStorage();
//     let refreshToken = tokenLogin.getRefreshTokenFromStorage();
//     tokenLogin.setAccessToken(accessToken);
//     tokenLogin.setRefreshToken(refreshToken);
//
//     if(refreshToken) {
//         statusLoginAuto = await adminService.getDataToAutoLoginAccessToken(tokenLogin);
//     }
//
//     console.log("Status login auto:", statusLoginAuto);
//
//     //neu 2 token ko co thi remove het
//     if(!accessToken || !refreshToken) {
//         routerManagement.removePathFromLocalStorage();
//     }
//
//     const activeRouters = statusLoginAuto ? routersAfterLogin : routersBeforeLogin;
//
//     const defaultPath = statusLoginAuto ? routerManagement.getPathFromLocalStorage() : '/login';
//
//     // Tìm route khớp với currentPath
//     const matchedRoute = activeRouters.find(route => route.path === currentPath)
//
//     let targetPath = currentPath;
//
//     // 1. Kiểm tra nếu chưa đăng nhập và route có cờ allow === false (hoặc route không hợp lệ)
//     // 2. Nếu đã đăng nhập nhưng route không tồn tại trong danh sách
//     // 3. Nếu đang ở root '/'
//     // 4. Kiểm tra SessionStorage
//     if (!statusLoginAuto && (matchedRoute?.allow === false || !matchedRoute)) {
//         targetPath = '/login'
//     } else if (!matchedRoute) {
//         targetPath = '/404-not-found'
//     } else if (currentPath === '/') {
//         targetPath = defaultPath
//     } else {
//         const pathSession = routerManagement.getPathFromSessionStorage();
//         if (pathSession && currentPath !== pathSession) {
//             targetPath = pathSession;
//         }
//     }
//
//     // Init path and routers
//     initApp(activeRouters, targetPath)
// }
//
// await execute();

import './assets/main.css'
import { createApp } from 'vue'
import App from './App.vue'

// Bootstrap
import 'bootstrap/dist/css/bootstrap.css'
import 'bootstrap/dist/js/bootstrap.js'

// Vue Router & Vuetify
import { createRouter, createWebHistory } from 'vue-router'
import { createVuetify } from 'vuetify'
import 'vuetify/styles'
import 'vuetify/dist/vuetify-labs.min.css'

// Routers / Services / Models
import RouterManagement from '@/routers/RouterManagement.js'
import routersBeforeLogin from '@/routers/routers_before_login.js'
import routersAfterLogin from '@/routers/routers_after_login.js'
import { AdminService } from '@/services/spring/AdminService.js'
import { TokenLogin } from '@/model/TokenLogin.js'

const LOGIN_PATH = '/login'
const NOT_FOUND_PATH = '/404-not-found'

const adminService = new AdminService()
const tokenLogin = new TokenLogin()
const routerManagement = new RouterManagement()

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

    await mountApp(routes, targetPath)
}

await bootstrap()