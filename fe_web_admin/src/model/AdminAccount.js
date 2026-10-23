export class AdminAccount {
    /** @type {number} */
    #adminId;

    /** @type {string} */
    #password;

    /**
     * @returns {number}
     */
    getAdminId(){
        return this.#adminId;
    }

    /**
     * @returns {void}
     */
    setAdminId(adminId) {
        this.#adminId = Number(adminId);
    }

    /**
     * @returns {string}
     */
    getPassword() {
        return this.#password;
    }

    /**
     * @returns {void}
     */
    setPassword(password) {
        this.#password = String(password);
    }

    /**
     * Khởi tạo đối tượng TokenLogin.
     * @param {number} [adminId] - Mã access token.
     * @param {string} [password] - Mã refresh token.
     */
    constructor(adminId, password) {
        this.setAdminId(adminId);
        this.setPassword(password);
    }

    toString() {
        return `Admin account:[adminId: ${this.#adminId}, password: ${this.#password}]`;
    }
}