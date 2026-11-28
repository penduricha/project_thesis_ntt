package com.example.be_web_admin.exception.qbank;

import org.springframework.http.HttpStatus;

/**
 * Lỗi nghiệp vụ của module ngân hàng câu hỏi. Mang theo HTTP status + mã lỗi ổn định để
 * Vue phân nhánh (vd: 409 VERSION_CONFLICT -> hiện hộp thoại "dữ liệu đã đổi"), thay vì
 * đoán qua chuỗi message.
 */
public class QBankException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public QBankException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static QBankException notFound(String code, String message) {
        return new QBankException(HttpStatus.NOT_FOUND, code, message);
    }

    public static QBankException badRequest(String code, String message) {
        return new QBankException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static QBankException conflict(String code, String message) {
        return new QBankException(HttpStatus.CONFLICT, code, message);
    }

    public static QBankException tooManyRequests(String code, String message) {
        return new QBankException(HttpStatus.TOO_MANY_REQUESTS, code, message);
    }

    public static QBankException serviceUnavailable(String code, String message) {
        return new QBankException(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }
}
