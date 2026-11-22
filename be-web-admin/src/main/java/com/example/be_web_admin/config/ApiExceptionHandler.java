package com.example.be_web_admin.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Xử lý lỗi dùng chung cho các API web/mobile (chat đã có {@code ChatExceptionHandler} chạy trước).
 *
 * <p>Lý do tồn tại: khi một API ném lỗi mà không có bộ xử lý nào, Spring chuyển tiếp nội bộ sang
 * {@code /error}. Với cấu hình bảo mật hiện tại (mọi request phải xác thực), chính bước chuyển tiếp đó
 * lại bị chặn và trả 401 "Unauthorized", nên lỗi thật (400/404/405/409) bị che thành 401 và client
 * không biết vì sao hỏng. Bộ xử lý này trả về đúng mã trạng thái và một thân JSON thống nhất:
 * {@code {"success": false, "code": "...", "message": "...", "at": "<thời điểm ISO-8601>"}}.
 *
 * <p>Thứ tự ưu tiên đặt thấp nhất để không tranh chỗ với {@code ClientAbortExceptionHandler}
 * (IOException do client ngắt kết nối) và {@code ChatExceptionHandler} (định dạng lỗi riêng của chat).
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApiExceptionHandler {

    /** Thân lỗi thống nhất; trường {@code at} giúp đối chiếu với log khi người dùng báo lỗi. */
    public record ApiErrorBody(boolean success, String code, String message, String at) {
        static ApiErrorBody of(String code, String message) {
            return new ApiErrorBody(false, code, message, Instant.now().toString());
        }
    }

    /** Thiếu/sai trường trong JSON gửi lên (khi controller dùng @Valid). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorBody> handleInvalidBody(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(ApiErrorBody.of("VALIDATION_FAILED", detail.isEmpty() ? "Dữ liệu gửi lên không hợp lệ." : detail));
    }

    /** JSON hỏng, sai kiểu dữ liệu, hoặc thiếu body bắt buộc. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorBody> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ApiErrorBody.of("MALFORMED_BODY", "Thân request không đọc được (JSON sai định dạng hoặc sai kiểu dữ liệu)."));
    }

    /** Thiếu tham số truy vấn hoặc tham số sai kiểu. */
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorBody> handleBadParameter(Exception ex) {
        String message = (ex instanceof MissingServletRequestParameterException missing)
                ? "Thiếu tham số bắt buộc: " + missing.getParameterName()
                : "Tham số không đúng kiểu dữ liệu.";
        return ResponseEntity.badRequest().body(ApiErrorBody.of("INVALID_PARAMETER", message));
    }

    /** Gọi sai method (ví dụ GET một API chỉ có POST). */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorBody> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiErrorBody.of("METHOD_NOT_ALLOWED", "API không hỗ trợ method " + ex.getMethod() + "."));
    }

    /** Sai Content-Type. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorBody> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiErrorBody.of("UNSUPPORTED_MEDIA_TYPE", "Content-Type chưa được hỗ trợ."));
    }

    /** Không có API ở đường dẫn này (Spring 6.1+ ném lỗi thay vì trả 404 mặc định). */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorBody> handleNotFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorBody.of("ENDPOINT_NOT_FOUND", "Không có API tại đường dẫn này."));
    }

    /**
     * Vi phạm ràng buộc dữ liệu (trùng khóa chính, trùng id_card, khóa ngoại...).
     * Trả 409 thay vì 500 để client biết là do dữ liệu chứ không phải server hỏng.
     * KHÔNG trả nội dung lỗi của DB ra ngoài (có thể lộ tên bảng/cột).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorBody> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Vi phạm ràng buộc dữ liệu: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorBody.of("DB_CONSTRAINT", "Dữ liệu vi phạm ràng buộc (trùng hoặc thiếu tham chiếu)."));
    }
}
