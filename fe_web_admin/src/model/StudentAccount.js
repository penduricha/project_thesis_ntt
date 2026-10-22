export class StudentAccount {
    /** @type {number} */
    #studentId;

    /** @type {string} */
    #password;

    /**
     * @returns {number}
     */
    getStudentId(){
        return this.#studentId;
    }

    /**
     * @returns {void}
     */
    setStudentId(studentId) {
        this.#studentId = Number(studentId);
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
     * @param {number} [studentId] - Mã access token.
     * @param {string} [password] - Mã refresh token.
     */
    constructor(studentId, password) {
        this.setStudentId(studentId);
        this.setPassword(password);
    }

    toString() {
        return `Student account:[studentId: ${this.#studentId}, password: ${this.#password}]`;
    }
}