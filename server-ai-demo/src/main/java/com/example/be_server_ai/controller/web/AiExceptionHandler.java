package com.example.be_server_ai.controller.web;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Đổi lỗi thành JSON {"message": "..."} mà trang HTML hiển thị được. Không đưa nội dung SQL, URL hay token vào
 * thông báo. Chỉ áp dụng cho các controller của Server AI.
 */
@RestControllerAdvice(basePackages = "com.example.be_server_ai.controller")
public class AiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException e) {
        return body(HttpStatus.BAD_REQUEST, "Thân yêu cầu không phải JSON hợp lệ.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> integrity(DataIntegrityViolationException e) {
        log.warn("Vi phạm ràng buộc CSDL khi tạo task: {}", e.getMostSpecificCause().getClass().getSimpleName());
        return body(HttpStatus.CONFLICT,
                "CSDL từ chối ghi task. Nếu bảng tạo từ script ER v4, kiểm tra: createdBy có trong ntt_admins "
                        + "(đổi ai.demo.created-by), questionId và questionRevisionId có thật, và câu hỏi đó chưa có "
                        + "task đang chạy.");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> dataAccess(DataAccessException e) {
        log.warn("Lỗi truy cập MariaDB hoặc Redis: {}", e.toString());
        return body(HttpStatus.SERVICE_UNAVAILABLE,
                "Server AI không truy cập được MariaDB hoặc Redis. Kiểm tra hai dịch vụ này và file .env.");
    }

    private static ResponseEntity<Map<String, String>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message == null ? status.getReasonPhrase() : message));
    }
}
