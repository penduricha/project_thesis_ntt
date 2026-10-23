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

// Plugins & Local Storage / Routers
import CanvasJSChart from '@canvasjs/vue-charts'
import AdminLocalStorage from "@/local_storage/AdminLocalStorage.js"
import RouterManagement from "@/routers/RouterManagement.js"
import routersBeforeLogin from "@/routers/routers_before_login.js"
import routersAfterLogin from "@/routers/routers_after_login.js"
import {AdminService} from "@/services/spring/AdminService.js";
import {TokenLogin} from "@/model/TokenLogin.js";

const adminService = new AdminService();

const tokenLogin = new TokenLogin();

const app = createApp(App)
app.use(createVuetify())

function initApp(routes, targetPath) {
    const router = createRouter({
        history: createWebHistory(),
        routes,
    })

    app.use(router)

    router.replace(targetPath).catch((error) => {
        console.error('Error navigating:', error)
    })

    app.mount('#app')
}

async function execute() {
    //const adminLocalStorage = new AdminLocalStorage()
    // const isLoggedIn = Boolean(
    //     routerManagement.getPath_From_LocalStorage() &&
    //     adminLocalStorage.getAdminId_From_LocalStorage() &&
    //     tokenLogin.getRefreshTokenFromStorage()
    // )

    const routerManagement = new RouterManagement();

    const currentPath = window.location.pathname;

    // Flag login auto
    let statusLoginAuto = false;

    let accessToken = tokenLogin.getAccessTokenFromStorage();
    let refreshToken = tokenLogin.getRefreshTokenFromStorage();
    tokenLogin.setAccessToken(accessToken);
    tokenLogin.setRefreshToken(refreshToken);

    if(refreshToken) {
        statusLoginAuto = await adminService.getDataToAutoLoginAccessToken(tokenLogin);
    }

    console.log("Status login auto:", statusLoginAuto);

    //neu 2 token ko co thi remove het
    if(!accessToken || !refreshToken) {
        routerManagement.removePathFromLocalStorage();
    }

    const activeRouters = statusLoginAuto ? routersAfterLogin : routersBeforeLogin;

    const defaultPath = statusLoginAuto ? routerManagement.getPathFromLocalStorage() : '/login';

    // Tìm route khớp với currentPath
    const matchedRoute = activeRouters.find(route => route.path === currentPath)

    let targetPath = currentPath;

    // 1. Kiểm tra nếu chưa đăng nhập và route có cờ allow === false (hoặc route không hợp lệ)
    // 2. Nếu đã đăng nhập nhưng route không tồn tại trong danh sách
    // 3. Nếu đang ở root '/'
    // 4. Kiểm tra SessionStorage
    if (!statusLoginAuto && (matchedRoute?.allow === false || !matchedRoute)) {
        targetPath = '/login'
    } else if (!matchedRoute) {
        targetPath = '/404-not-found'
    } else if (currentPath === '/') {
        targetPath = defaultPath
    } else {
        const pathSession = routerManagement.getPathFromSessionStorage();
        if (pathSession && currentPath !== pathSession) {
            targetPath = pathSession;
        }
    }

    // Init path and routers
    initApp(activeRouters, targetPath)
}

await execute();