package com.example.be_web_admin.exception.qbank;

import org.springframework.http.HttpStatus;

/**
 * Lỗi nghiệp vụ của module ngân hàng câu hỏi. Mang theo HTTP status + mã lỗi ổn định để
 * Vue phân nhánh (vd: 409 VERSION_CONFLICT -> hiện hộp thoại "dữ liệu đã đổi"), thay vì
 * đoán qua chuỗi message.
 */
public class QbankException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public QbankException(HttpStatus status, String code, String message) {
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

    public static QbankException notFound(String code, String message) {
        return new QbankException(HttpStatus.NOT_FOUND, code, message);
    }

    public static QbankException badRequest(String code, String message) {
        return new QbankException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static QbankException conflict(String code, String message) {
        return new QbankException(HttpStatus.CONFLICT, code, message);
    }

    public static QbankException tooManyRequests(String code, String message) {
        return new QbankException(HttpStatus.TOO_MANY_REQUESTS, code, message);
    }

    public static QbankException serviceUnavailable(String code, String message) {
        return new QbankException(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }
}
