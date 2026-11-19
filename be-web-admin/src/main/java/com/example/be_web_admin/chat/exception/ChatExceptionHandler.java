package com.example.be_web_admin.chat.exception;

import com.example.be_web_admin.chat.controller.ChatRestController;
import com.example.be_web_admin.chat.controller.ChatWsTicketController;
import com.example.be_web_admin.chat.dto.ErrorBody;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Chỉ áp cho hai controller của chat (basePackageClasses) để không đổi định dạng lỗi của các API cũ.
 * Mọi lỗi đều có dạng { "code": "...", "message": "..." } (và details với lỗi kiểm tra dữ liệu).
 */
@Slf4j
@RestControllerAdvice(basePackageClasses = {ChatRestController.class, ChatWsTicketController.class})
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ChatExceptionHandler {

    @ExceptionHandler(ChatException.class)
    public ResponseEntity<ErrorBody> handleChat(ChatException ex) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(ex.getStatus());
        if (ex.getRetryAfterSeconds() != null) {
            builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
        }
        return builder.body(ErrorBody.of(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> handleInvalidBody(MethodArgumentNotValidException ex) {
        List<ErrorBody.FieldIssue> issues = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorBody.FieldIssue(error.getField(), String.valueOf(error.getDefaultMessage())))
                .toList();
        return ResponseEntity.badRequest()
                .body(new ErrorBody("VALIDATION_FAILED", "Dữ liệu gửi lên không hợp lệ.", issues));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorBody> handleConstraint(ConstraintViolationException ex) {
        List<ErrorBody.FieldIssue> issues = ex.getConstraintViolations().stream()
                .map(v -> new ErrorBody.FieldIssue(String.valueOf(v.getPropertyPath()), v.getMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(new ErrorBody("VALIDATION_FAILED", "Dữ liệu gửi lên không hợp lệ.", issues));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorBody> handleUnreadable(HttpMessageNotReadableException ex) {
        log.debug("Chat: body không đọc được: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ErrorBody.of("VALIDATION_FAILED", "Body không phải JSON hợp lệ."));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorBody> handleBadParameter(Exception ex) {
        return ResponseEntity.badRequest()
                .body(ErrorBody.of("VALIDATION_FAILED", "Tham số không hợp lệ hoặc bị thiếu."));
    }
}
