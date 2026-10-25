import {AdminAccount} from "@/model/AdminAccount.js";
import {TokenLogin} from "@/model/TokenLogin.js";
import SpringConfigHeader from "@/services/spring/SpringConfigHeader.js";
import AdminLocalStorage from "@/local_storage/AdminLocalStorage.js";

const ENDPOINTS = Object.freeze({
    LOGIN: '/admin/auth/login',
    LOGOUT: '/admin/auth/logout',
    INFORMATION: '/admin/information',
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
        adminLocalStorage = new AdminLocalStorage(),
    ) {
        this.springConfigHeader = springConfigHeader;
        this.adminLocalStorage = adminLocalStorage;
    }

    // ---------- Public API ----------

    async postDataToLoginRequest(adminAccount) {
        assertInstanceOf(adminAccount, AdminAccount, 'AdminAccount');

        return this.#postWithAuthorization(ENDPOINTS.LOGIN, {
            adminId: adminAccount.getAdminId(),
            password: adminAccount.getPassword(),
        });
    }

    async postDataToLogoutRequest(tokenLogin) {
        assertInstanceOf(tokenLogin, TokenLogin, 'TokenLogin');

        return this.#postWithAuthorization(ENDPOINTS.LOGOUT, {
            accessToken: tokenLogin.getAccessTokenFromStorage(),
            refreshToken: tokenLogin.getRefreshTokenFromStorage(),
        });
    }

    async getAdminInformationRequestByAdminId(adminId) {
        try {
            const path = `${ENDPOINTS.INFORMATION}?adminId=${encodeURIComponent(adminId)}`;
            return await this.springConfigHeader
                .getAPIClientNoHeaders()
                .get(path, this.springConfigHeader.getAuthorization());
        } catch (error) {
            console.error(error);
            return null;
        }
    }

    /**
     * Tự động đăng nhập bằng access token.
     * Nếu access token hết hạn (401) thì thử làm mới token rồi đăng nhập lại.
     * @returns {Promise<boolean>} true nếu đăng nhập thành công
     */
    async getDataToAutoLoginAccessToken(tokenLogin) {
        assertInstanceOf(tokenLogin, TokenLogin, 'TokenLogin');

        try {
            return await this.#loginWithAccessToken(tokenLogin.getAccessToken());
        } catch (error) {
            if (error.response?.status !== HTTP_UNAUTHORIZED) {
                console.error('Auto login error:', error);
                this.#clearSession(tokenLogin);
                return false;
            }
        }

        return this.#renewTokensAndLogin(tokenLogin);
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

    async #renewTokensAndLogin(tokenLogin) {
        console.warn('Access token is expired. Renewing tokens...');

        try {
            const response = await this.springConfigHeader
                .getAPIClientWithHeadersToken(tokenLogin.getRefreshToken())
                .get(ENDPOINTS.AUTO_LOGIN_REFRESH_TOKEN);

            if (response?.status !== HTTP_OK) {
                this.#clearSession(tokenLogin);
                return false;
            }

            const {accessToken, refreshToken} = response.data.data;
            this.#storeTokens(tokenLogin, accessToken, refreshToken);

            return await this.#loginWithAccessToken(accessToken);
        } catch (error) {
            console.error('Refresh token failed:', error);
            this.#clearSession(tokenLogin);
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