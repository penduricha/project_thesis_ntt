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
    constructor(accessToken = "", refreshToken = "") {
        this.#accessToken = TokenLogin.#normalize(accessToken);
        this.#refreshToken = TokenLogin.#normalize(refreshToken);
    }

    static #normalize(value) {
        if (value === null || value === undefined) {
            return "";
        }
        return String(value).trim();
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
        this.#accessToken = TokenLogin.#normalize(value);
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
        this.#refreshToken = TokenLogin.#normalize(value);
    }

    // ================= LOCALSTORAGE METHODS =================

    /**
     * @returns {void}
     */
    setAccessTokenToStorage() {
        if (this.#accessToken) {
            localStorage.setItem("accessToken", this.#accessToken);
        } else {
            localStorage.removeItem("accessToken");
        }
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
     * @returns {void}
     */
    setRefreshTokenToStorage() {
        if (this.#refreshToken) {
            localStorage.setItem("refreshToken", this.#refreshToken);
        } else {
            localStorage.removeItem("refreshToken");
        }
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
     * @returns {void}
     */
    removeAccessTokenAndRefreshTokenFromStorage() {
        localStorage.removeItem("accessToken");
        localStorage.removeItem("refreshToken");
    }

    // ================= TO STRING =================

    /**
     * @returns {string}
     */
    toString() {
        return "TokenLogin [configured=" + Boolean(this.#accessToken || this.#refreshToken) + "]";
    }
}
