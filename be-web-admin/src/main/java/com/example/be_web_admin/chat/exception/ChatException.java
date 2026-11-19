package com.example.be_web_admin.chat.exception;

import org.springframework.http.HttpStatus;

/** Lỗi nghiệp vụ của chat: có mã HTTP và code ổn định cho client. Dựng bằng các hàm tĩnh bên dưới. */
public class ChatException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    /** Chỉ dùng với 429: số giây client nên chờ, trả về qua header Retry-After. */
    private final Long retryAfterSeconds;

    public ChatException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public ChatException(HttpStatus status, String code, String message, Long retryAfterSeconds) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    // Không cần dấu vết ngăn xếp cho lỗi nghiệp vụ: bỏ đi cho nhẹ.
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }

    public static ChatException badRequest(String code, String message) {
        return new ChatException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ChatException validation(String message) {
        return new ChatException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }

    public static ChatException forbidden(String code, String message) {
        return new ChatException(HttpStatus.FORBIDDEN, code, message);
    }

    /** 404 dùng chung cho "không tồn tại" và "không phải thành viên", để không lộ cuộc trò chuyện nào có thật. */
    public static ChatException notFound() {
        return new ChatException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy.");
    }

    public static ChatException notFound(String code, String message) {
        return new ChatException(HttpStatus.NOT_FOUND, code, message);
    }

    public static ChatException conflict(String code, String message) {
        return new ChatException(HttpStatus.CONFLICT, code, message);
    }

    public static ChatException tooManyRequests(String message, long retryAfterSeconds) {
        return new ChatException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", message, retryAfterSeconds);
    }

    public static ChatException unavailable(String code, String message) {
        return new ChatException(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }
}
