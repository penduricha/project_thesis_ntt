/**
 * Class quản lý thông tin Login Token.
 */
export class TokenLogin {
    /** @type {string} */
    #accessToken;

    /** @type {string} */
    #refreshToken;

    /**
     * Khởi tạo đối tượng TokenLogin.
     * @param {string} [accessToken] - Mã access token.
     * @param {string} [refreshToken] - Mã refresh token.
     */
    constructor(accessToken, refreshToken) {
        this.#accessToken = String(accessToken);
        this.#refreshToken = String(refreshToken);
    }

    // ================= GETTER & SETTER =================

    /**
     * @returns {string} Access Token.
     */
    getAccessToken() {
        return this.#accessToken;
    }

    /**
     * @returns {void}
     */
    setAccessToken(value) {
        this.#accessToken = String(value);
    }

    /**
     * @returns {string} Refresh Token.
     */
    getRefreshToken() {
        return this.#refreshToken;
    }

    /**
     * @returns {void}
     */
    setRefreshToken(value) {
        this.#refreshToken = String(value);
    }

    // ================= LOCALSTORAGE METHODS =================

    /**
     *
     * @returns {void}
     */
    setAccessTokenToStorage() {
        localStorage.setItem("accessToken", this.#accessToken);
    }

    /**
     * Lấy accessToken từ localStorage và cập nhật vào thuộc tính instance.
     * @returns {string|null} Access token từ localStorage.
     */
    getAccessTokenFromStorage() {
        const token = localStorage.getItem("accessToken");
        if (token !== null) {
            this.#accessToken = token;
        }
        return token;
    }

    /**
     *
     * @returns {void}
     */
    setRefreshTokenToStorage() {
        localStorage.setItem("refreshToken", this.#refreshToken);
    }

    /**
     * Lấy refreshToken từ localStorage và cập nhật vào thuộc tính instance.
     * @returns {string|null} Refresh token từ localStorage.
     */
    getRefreshTokenFromStorage() {
        const token = localStorage.getItem("refreshToken");
        if (token !== null) {
            this.#refreshToken = token;
        }
        return token;
    }

    /**
     *
     * @returns {void}
     */
    removeAccessTokenAndRefreshTokenFromStorage() {
        localStorage.removeItem("accessToken");
        localStorage.removeItem("refreshToken");
    }

    // ================= TO STRING =================

    /**
     *
     * @returns {string}
     */
    toString() {
        return `TokenLogin [accessToken=${this.#accessToken}, refreshToken=${this.#refreshToken}]`;
    }
}