package com.example.be_web_admin.exception.qbank;

import com.example.be_web_admin.controller.web.AiJobController;
import com.example.be_web_admin.controller.web.DashboardController;
import com.example.be_web_admin.controller.web.QuestionBankController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
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

    /**
     * Tham số query sai kiểu, ví dụ {@code ?page=abc} hoặc {@code ?questionId=xyz}.
     *
     * <p>Không có handler này thì Spring trả 400 nhưng thân rỗng, nên Vue không có
     * {@code message} để hiển thị và rơi vào câu báo chung. Trả đúng tên tham số để người gọi
     * biết sửa chỗ nào — đây là lỗi phía client, không phải lỗi hệ thống.</p>
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String expected = e.getRequiredType() == null ? "đúng kiểu" : e.getRequiredType().getSimpleName();
        return build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "Tham số '" + e.getName() + "' phải là " + expected + ".");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException e) {
        return build(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                "Thiếu tham số bắt buộc '" + e.getParameterName() + "'.");
    }

    /**
     * Sai method (gọi PUT vào endpoint chỉ nhận POST...): 405 thay vì 500.
     *
     * <p><b>Lưu ý phạm vi:</b> advice này khai báo {@code assignableTypes} nên CHỈ áp cho
     * QuestionBank / Dashboard / AiJob. Với lỗi 405 hay 404 đường dẫn, Spring không gắn được lỗi
     * vào controller cụ thể nào nên hai handler dưới đây có thể không được gọi; fallback
     * {@code Exception} vẫn bắt khi lỗi phát sinh trong phạm vi 3 controller. Muốn áp toàn cục
     * thì phải bỏ {@code assignableTypes} — nhưng làm vậy sẽ đổi định dạng lỗi của auth/chat đang
     * chạy, nên cố ý không làm ở đây.</p>
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethod(HttpRequestMethodNotSupportedException e) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "Endpoint này không hỗ trợ phương thức " + e.getMethod() + ".");
    }

    /** Không có endpoint nào khớp đường dẫn: 404 JSON thay vì trang lỗi mặc định. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException e) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy API này.");
    }

    /**
     * Lưới an toàn cuối cùng: lỗi không lường trước vẫn trả ĐÚNG định dạng JSON
     * {@code {success:false, code, message, at}}.
     *
     * <p><b>Vì sao cần:</b> không có handler bắt {@code Exception} thì Spring trả trang lỗi
     * HTML/JSON mặc định, nên lớp gọi API của Vue nhận nội dung không parse được và hiển thị
     * câu báo chung chung ("Có lỗi xảy ra") hoặc thậm chí treo ở trạng thái đang tải. Trả một
     * mã lỗi có nghĩa là giao diện luôn có câu để hiển thị.</p>
     *
     * <p><b>Không lộ chi tiết nội bộ:</b> stack trace và câu lỗi gốc chỉ ghi log phía server; thân
     * phản hồi cho client là câu cố định, không chứa tên lớp, SQL hay đường dẫn tệp.</p>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e) {
        log.error("Qbank: lỗi ngoài dự kiến", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Máy chủ gặp lỗi khi xử lý yêu cầu. Vui lòng thử lại.");
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
