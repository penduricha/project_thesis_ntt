package com.example.be_web_admin.exception.qbank;

import com.example.be_web_admin.controller.web.AiJobController;
import com.example.be_web_admin.controller.web.DashboardController;
import com.example.be_web_admin.controller.web.QuestionBankController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dựng lỗi của module ngân hàng câu hỏi thành JSON thống nhất:
 * {@code { "success": false, "code": "...", "message": "...", "at": "..." }}.
 *
 * <p>Chỉ áp cho 3 controller của module (assignableTypes) để KHÔNG đổi hành vi lỗi của
 * auth/chat đang chạy.</p>
 */
@Slf4j
@RestControllerAdvice(assignableTypes = {
        QuestionBankController.class,
        DashboardController.class,
        AiJobController.class})
public class QbankExceptionHandler {

    @ExceptionHandler(QbankException.class)
    public ResponseEntity<Map<String, Object>> handleQbank(QbankException e) {
        return build(e.getStatus(), e.getCode(), e.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(OptimisticLockingFailureException e) {
        return build(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                "Câu hỏi vừa bị người khác sửa. Tải lại để lấy bản mới nhất.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrity(DataIntegrityViolationException e) {
        log.warn("Qbank: vi phạm ràng buộc CSDL: {}", e.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "DB_CONSTRAINT",
                "Dữ liệu vi phạm ràng buộc của CSDL (trùng mã/khóa ngoại/CHECK).");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Dữ liệu không hợp lệ.");
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", detail);
    }

    private static ResponseEntity<Map<String, Object>> build(HttpStatus status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("code", code);
        body.put("message", message);
        body.put("at", Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }
}
