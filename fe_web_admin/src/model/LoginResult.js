/**
 * Kết quả một lần gọi đăng nhập, để component không phải đọc JSON thô.
 * status là một giá trị của REQUEST_STATUS (AdminRepository.js).
 */
export class LoginResult {
    constructor(status, retryAfterSeconds = 0) {
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    getStatus() {
        return this.status;
    }

    getRetryAfterSeconds() {
        return this.retryAfterSeconds;
    }
}