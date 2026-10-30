import AdminLocalStorage from "@/local_storage/AdminLocalStorage.js";
import RouterMgmt from "@/routers/RouterMgmt.js";
import {AdminAccount} from "@/model/AdminAccount.js";
import {TokenLogin} from "@/model/TokenLogin.js";
import {AdminService} from "@/services/spring/AdminService.js";
import {LoginService} from "@/services/spring/LoginService.js";

// Giữ nguyên giá trị chuỗi cũ vì nơi gọi có thể đang so sánh với chúng
export const REQUEST_STATUS = Object.freeze({
    OK: "OK",
    FAILED: "Failed",
    EXCEPTION: "Exception system:",
});

const HTTP_OK = 200;

export default class AdminRepository {
    constructor(
        adminService = new AdminService(),
        loginService = new LoginService(),
        adminLocalStorage = new AdminLocalStorage(),
    ) {
        this.adminService = adminService;
        this.loginService = loginService;
        this.adminLocalStorage = adminLocalStorage;
    }

    // ---------- Public API ----------

    async getResponseFromLoginRequest(adminId, password) {
        const adminAccount = new AdminAccount(adminId, password);

        try {
            const response = await this.adminService.postDataToLoginRequest(adminAccount);
            const {success, data} = response.data;

            if (!success) {
                return REQUEST_STATUS.FAILED;
            }

            this.#saveLoginSession(adminAccount, data);
            return REQUEST_STATUS.OK;
        } catch (error) {
            console.error(REQUEST_STATUS.EXCEPTION, error.message);
            return REQUEST_STATUS.EXCEPTION;
        }
    }

    async getResponseFromLogoutRequest() {
        try {
            const tokenLogin = new TokenLogin();
            const response = await this.adminService.postDataToLogoutRequest(tokenLogin);

            if (response.data.status !== HTTP_OK) {
                return REQUEST_STATUS.FAILED;
            }

            this.#clearLoginSession(tokenLogin);
            return REQUEST_STATUS.OK;
        } catch (error) {
            console.error(REQUEST_STATUS.EXCEPTION, error.message);
            return REQUEST_STATUS.EXCEPTION;
        }
    }

    /**
     * @returns {Promise<Object|null>} thông tin admin, hoặc null nếu chưa đăng nhập / không lấy được
     */
    async getAdminInformationByAdminId() {
        // Auto login PHẢI chạy trước: nó có thể ghi adminId mới vào localStorage
        const isAutoLoggedIn = await this.loginService.getStatusAutoLogin();
        const adminId = this.adminLocalStorage.getAdminIdToLocalStorage();

        if (!isAutoLoggedIn || !adminId) {
            return null;
        }

        const response = await this.adminService.getAdminInformationRequestByAdminId(adminId);
        return response?.data?.data ?? null;
    }

    // ---------- Private helpers ----------

    #saveLoginSession(adminAccount, {accessToken, refreshToken}) {
        const tokenLogin = new TokenLogin(String(accessToken), String(refreshToken));
        tokenLogin.setAccessTokenToStorage();
        tokenLogin.setRefreshTokenToStorage();

        this.adminLocalStorage.setAdminIdToLocalStorage(adminAccount.getAdminId());
    }

    #clearLoginSession(tokenLogin) {
        this.adminLocalStorage.removeAdminIdFromLocalStorage();
        tokenLogin.removeAccessTokenAndRefreshTokenFromStorage();

        // Tạo lazy như bản cũ, vì constructor của RouterMgmt có thể phụ thuộc trạng thái router
        const routerManagement = new RouterMgmt();
        routerManagement.removePathFromLocalStorage();
        routerManagement.removePathFromSessionStorage();
    }
}