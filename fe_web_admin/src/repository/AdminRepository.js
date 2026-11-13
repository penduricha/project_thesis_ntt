import AdminStorage from "@/local_storage/AdminStorage.js";
import RouterMgmt from "@/router/RouterMgmt.js";
import {AdminAccount} from "@/model/AdminAccount.js";
import {TokenLogin} from "@/model/TokenLogin.js";
import {AdminService} from "@/services/spring/AdminService.js";
import {LoginService} from "@/services/spring/LoginService.js";
import {LoginResult} from "@/model/LoginResult.js";

// Giữ nguyên giá trị chuỗi cũ vì nơi gọi có thể đang so sánh với chúng
export const REQUEST_STATUS = Object.freeze({
    OK: "OK",
    FAILED: "Failed",
    EXCEPTION: "Exception system:",
    // Thêm cho 2FA (không ảnh hưởng nơi đang so sánh với 3 giá trị cũ)
    TOTP_REQUIRED: "TotpRequired",   // mật khẩu đúng + tài khoản đã bật 2FA -> cần mã 6 số
    TOTP_INVALID: "TotpInvalid",     // mã 6 số sai / hết hạn / đã dùng
    LOCKED: "Locked",                // nhập sai mã quá nhiều lần
});

// Mã nghiệp vụ backend đặt trong data.code khi success = false
const LOGIN_CODE = Object.freeze({
    TOTP_REQUIRED: "TOTP_REQUIRED",
    TOTP_INVALID: "TOTP_INVALID",
    LOCKED: "LOCKED",
});

const HTTP_OK = 200;

export default class AdminRepository {
    constructor(
        adminService = new AdminService(),
        loginService = new LoginService(),
        adminLocalStorage = new AdminStorage(),
    ) {
        this.adminService = adminService;
        this.loginService = loginService;
        this.adminLocalStorage = adminLocalStorage;
    }

    // ---------- Public API ----------

    /**
     * Giữ lại để các nơi cũ vẫn chạy: chỉ trả chuỗi trạng thái.
     */
    async getResponseFromLoginRequest(adminId, password, totpCode = null) {
        const result = await this.getLoginResult(adminId, password, totpCode);
        return result.getStatus();
    }

    /**
     * Đăng nhập 1 hoặc 2 bước:
     *  - Bước 1, POST /auth/login (totpCode = null): chưa bật 2FA -> OK; đã bật 2FA -> TOTP_REQUIRED.
     *  - Bước 2, POST /auth/login/totp (kèm totpCode): OK, TOTP_INVALID hoặc LOCKED.
     * @returns {Promise<LoginResult>}
     */
    async getLoginResult(adminId, password, totpCode = null) {
        const adminAccount = new AdminAccount(adminId, password);

        try {
            // Có mã 6 số thì gọi bước 2 (/auth/login/totp), chưa có thì gọi bước 1 (/auth/login)
            const response = totpCode
                ? await this.adminService.postDataToLoginTotpRequest(adminAccount, totpCode)
                : await this.adminService.postDataToLoginRequest(adminAccount);
            return this.#toLoginResult(adminAccount, response.data);
        } catch (error) {
            // Nếu backend trả 401/429 kèm body nghiệp vụ thì axios sẽ ném lỗi; vẫn đọc body đó.
            const body = error.response?.data;
            if (body && typeof body === "object" && body.success === false) {
                return this.#toLoginResult(adminAccount, body);
            }
            console.error(REQUEST_STATUS.EXCEPTION, error.message);
            return new LoginResult(REQUEST_STATUS.EXCEPTION);
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
    async getAdminInformationBasicByAdminId() {
        // Auto login PHẢI chạy trước: nó có thể ghi adminId mới vào localStorage
        const isAutoLoggedIn = await this.loginService.getStatusAutoLogin();
        const adminId = this.adminLocalStorage.getAdminIdToLocalStorage();

        if (!isAutoLoggedIn || !adminId) {
            return null;
        }

        const response = await this.adminService.getAdminInformationBasicRequestByAdminId(adminId);
        return response?.data?.data ?? null;
    }

    async getAdminInformationDetailByAdminId() {
        // Auto login PHẢI chạy trước: nó có thể ghi adminId mới vào localStorage
        const isAutoLoggedIn = await this.loginService.getStatusAutoLogin();
        const adminId = this.adminLocalStorage.getAdminIdToLocalStorage();

        if (!isAutoLoggedIn || !adminId) {
            return null;
        }

        const response = await this.adminService.getAdminInformationDetailRequestByAdminId(adminId);
        return response?.data?.data ?? null;
    }

    // ---------- Private helpers ----------

    #toLoginResult(adminAccount, body) {
        const {success, data} = body;

        if (success) {
            this.#saveLoginSession(adminAccount, data);
            return new LoginResult(REQUEST_STATUS.OK);
        }

        switch (data?.code) {
            case LOGIN_CODE.TOTP_REQUIRED:
                return new LoginResult(REQUEST_STATUS.TOTP_REQUIRED);
            case LOGIN_CODE.TOTP_INVALID:
                return new LoginResult(REQUEST_STATUS.TOTP_INVALID);
            case LOGIN_CODE.LOCKED:
                return new LoginResult(REQUEST_STATUS.LOCKED, Number(data.retryAfterSeconds) || 0);
            default:
                return new LoginResult(REQUEST_STATUS.FAILED); // sai mã quản trị viên / mật khẩu
        }
    }

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