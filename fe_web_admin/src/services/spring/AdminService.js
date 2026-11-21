import {AdminAccount} from "@/model/AdminAccount.js";
import {TokenLogin} from "@/model/TokenLogin.js";
import SpringConfigHeader from "@/services/spring/SpringConfigHeader.js";
import AdminStorage from "@/local_storage/AdminStorage.js";
import {silentRefresher} from "@/services/auth/SilentTokenRefresher.js";

const ENDPOINTS = Object.freeze({
    LOGIN: '/admin/auth/login',
    LOGIN_TOTP: '/admin/auth/login/totp',
    LOGOUT: '/admin/auth/logout',
    INFORMATION: '/admin/information/basic',
    INFORMATION_DETAIL: '/admin/information/detail',
    AUTO_LOGIN_ACCESS_TOKEN: '/admin/auth/login/auto/access-token',
    AUTO_LOGIN_REFRESH_TOKEN: '/admin/auth/login/auto/refresh-token',
});

const HTTP_OK = 200;
const HTTP_UNAUTHORIZED = 401;

function assertInstanceOf(value, expectedClass, label) {
    if (!(value instanceof expectedClass)) {
        throw new Error(`Invalid ${label} instance`);
    }
}

export class AdminService {
    constructor(
        springConfigHeader = new SpringConfigHeader(),
        adminLocalStorage = new AdminStorage(),
    ) {
        this.springConfigHeader = springConfigHeader;
        this.adminLocalStorage = adminLocalStorage;
    }

    // ---------- Public API ----------

    /** Bước 1: chỉ mã quản trị viên + mật khẩu. */
    async postDataToLoginRequest(adminAccount) {
        assertInstanceOf(adminAccount, AdminAccount, 'AdminAccount');

        return this.#postWithAuthorization(ENDPOINTS.LOGIN, {
            adminId: adminAccount.getAdminId(),
            password: adminAccount.getPassword(),
        });
    }

    /**
     * Bước 2 (khi bước 1 báo TOTP_REQUIRED): gửi lại kèm mã 6 số từ app Authenticator.
     * @param {AdminAccount} adminAccount
     * @param {string} totpCode
     */
    async postDataToLoginTotpRequest(adminAccount, totpCode) {
        assertInstanceOf(adminAccount, AdminAccount, 'AdminAccount');

        return this.#postWithAuthorization(ENDPOINTS.LOGIN_TOTP, {
            adminId: adminAccount.getAdminId(),
            password: adminAccount.getPassword(),
            totpCode: String(totpCode),
        });
    }

    async postDataToLogoutRequest(tokenLogin) {
        assertInstanceOf(tokenLogin, TokenLogin, 'TokenLogin');

        return this.#postWithAuthorization(ENDPOINTS.LOGOUT, {
            accessToken: tokenLogin.getAccessTokenFromStorage(),
            refreshToken: tokenLogin.getRefreshTokenFromStorage(),
        });
    }

    async getAdminInformationBasicRequestByAdminId(adminId, tokenLogin = new TokenLogin()) {
        assertInstanceOf(tokenLogin, TokenLogin, 'TokenLogin');
        const path = `${ENDPOINTS.INFORMATION}?adminId=${encodeURIComponent(adminId)}`;
        return this.springConfigHeader
            .getAuthorizedAPIClient(tokenLogin)
            .get(path);
    }

    async getAdminInformationDetailRequestByAdminId(adminId, tokenLogin = new TokenLogin()) {
        assertInstanceOf(tokenLogin, TokenLogin, 'TokenLogin');
        const path = `${ENDPOINTS.INFORMATION_DETAIL}?adminId=${encodeURIComponent(adminId)}`;
        return this.springConfigHeader
            .getAuthorizedAPIClient(tokenLogin)
            .get(path);
    }

    /**
     * Tự động đăng nhập bằng access token.
     * Nếu access token hết hạn (401) thì thử làm mới token rồi đăng nhập lại.
     * @returns {Promise<boolean>} true nếu đăng nhập thành công
     */
    async getDataToAutoLoginAccessToken(tokenLogin) {
        assertInstanceOf(tokenLogin, TokenLogin, 'TokenLogin');

        const staleAccessToken = tokenLogin.getAccessToken();
        try {
            return await this.#loginWithAccessToken(staleAccessToken);
        } catch (error) {
            if (error.response?.status !== HTTP_UNAUTHORIZED) {
                console.error('Auto login error:', error);
                // Không cấp quyền vào app, nhưng lỗi kết nối không chứng minh phiên đã hết hạn.
                return false;
            }
        }

        return this.#renewTokensAndLogin(tokenLogin, staleAccessToken);
    }

    /**
     * Đổi refresh token lấy cặp access + refresh token MỚI rồi lưu vào localStorage. Đúng MỘT lần gọi server.
     *
     * Server xoay vòng refresh token (token cũ bị xóa ngay), nên hai lần đổi song song bằng cùng một refresh
     * token thì lần sau chắc chắn bị từ chối. Vì vậy ngoài SilentTokenRefresher (đã có single-flight + Web Locks)
     * không nơi nào được gọi hàm này trực tiếp; cần làm mới thì gọi silentRefresher.refreshNow().
     *
     * @param {TokenLogin} tokenLogin refresh token lấy từ getRefreshToken() (đã đồng bộ với storage)
     * @returns {Promise<boolean>} true = đã lưu cặp token mới;
     *   false = server từ chối refresh token (hết hạn / bị thu hồi): phiên đã bị xóa, phải đăng nhập lại
     * @throws lỗi mạng / server 5xx: giữ nguyên phiên để nơi gọi thử lại sau
     */
    async getDataToRenewTokens(tokenLogin) {
        assertInstanceOf(tokenLogin, TokenLogin, 'TokenLogin');

        let response;
        try {
            response = await this.springConfigHeader
                .getAPIClientWithHeadersToken(tokenLogin.getRefreshToken())
                .get(ENDPOINTS.AUTO_LOGIN_REFRESH_TOKEN);
        } catch (error) {
            const status = error.response?.status;
            if (status === HTTP_UNAUTHORIZED) {
                this.#clearSession(tokenLogin); // 401: refresh token không còn dùng được
                return false;
            }
            throw error; // mất mạng / 5xx: không đăng xuất
        }

        const data = response?.data?.data;
        if (response?.status !== HTTP_OK || !data?.accessToken || !data?.refreshToken) {
            this.#clearSession(tokenLogin);
            return false;
        }

        this.#storeTokens(tokenLogin, data.accessToken, data.refreshToken);
        return true;
    }

    // ---------- Private helpers ----------

    #postWithAuthorization(path, data) {
        return this.springConfigHeader
            .getAPIClientNoHeaders()
            .post(path, data, this.springConfigHeader.getAuthorization());
    }

    async #loginWithAccessToken(accessToken) {
        const response = await this.springConfigHeader
            .getAPIClientWithHeadersToken(accessToken)
            .get(ENDPOINTS.AUTO_LOGIN_ACCESS_TOKEN);

        if (response.status !== HTTP_OK) {
            return false;
        }

        this.#saveAdminId(response.data.data.adminId);
        return true;
    }

    async #renewTokensAndLogin(tokenLogin, staleAccessToken) {
        console.warn('Access token is expired. Renewing tokens...');

        try {
            if (silentRefresher.isReady()) {
                // Đi qua bộ làm mới ngầm để mọi nơi dùng CHUNG một lần làm mới (single-flight + Web Locks,
                // tab khác đã làm mới thì dùng luôn). Nếu mỗi nơi tự đổi refresh token thì request thứ hai
                // chắc chắn bị server từ chối và người dùng bị đăng xuất oan.
                const renewed = await silentRefresher.refreshNow(staleAccessToken, {notifyExpired: false});
                if (!renewed) return false; // bị từ chối (phiên đã xóa) hoặc đang lỗi mạng (giữ phiên, sẽ tự thử lại)

                // Đồng bộ bản TokenLogin của nơi gọi với token vừa được lưu vào storage
                tokenLogin.getAccessTokenFromStorage();
                tokenLogin.getRefreshTokenFromStorage();
            } else if (!(await this.getDataToRenewTokens(tokenLogin))) {
                return false;
            }

            return await this.#loginWithAccessToken(tokenLogin.getAccessToken());
        } catch (error) {
            console.error('Refresh token failed:', error);
            return false;
        }
    }

    #storeTokens(tokenLogin, accessToken, refreshToken) {
        tokenLogin.setAccessToken(accessToken);
        tokenLogin.setRefreshToken(refreshToken);
        tokenLogin.setAccessTokenToStorage();
        tokenLogin.setRefreshTokenToStorage();
    }

    #saveAdminId(adminId) {
        this.adminLocalStorage.setAdminIdToLocalStorage(Number(adminId));
    }

    #clearSession(tokenLogin) {
        tokenLogin.removeAccessTokenAndRefreshTokenFromStorage();
        this.adminLocalStorage.removeAdminIdFromLocalStorage();
    }
}