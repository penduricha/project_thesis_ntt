package com.example.be_web_admin.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;
import java.util.Locale;

/**
 * Xử lý lỗi "java.io.IOException: Broken pipe" khi trình duyệt đã đóng kết nối trước khi server ghi xong.
 *
 * Hay gặp nhất ở luồng SSE (GET /web/admin/2fa/events): người dùng tải lại trang, đổi trang hoặc Vue hủy
 * fetch (AbortController) đúng lúc Spring đang đẩy header / sự kiện "ready" xuống. Đây KHÔNG phải lỗi
 * của server, nên chỉ ghi log mức DEBUG và không ghi gì vào response (kết nối đã chết, không còn ai đọc).
 *
 * Các IOException khác (không phải do client ngắt kết nối) vẫn được ghi log ERROR và trả 500 như thường.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ClientAbortExceptionHandler {

    /** Phần của thông báo lỗi cho biết phía client đã đóng kết nối (viết thường, so khớp theo "chứa"). */
    private static final String[] CLIENT_ABORT_MESSAGES = {
            "broken pipe",
            "connection reset",
            "connection abort",
            "forcibly closed",
            "established connection was aborted",
    };

    private static final int MAX_CAUSE_DEPTH = 10;

    @ExceptionHandler(IOException.class)
    public void handleIoException(IOException ex,
                                  HttpServletRequest request,
                                  HttpServletResponse response) throws IOException {
        if (isClientAbort(ex)) {
            log.debug("Client closed the connection while writing {} {}: {}",
                    request.getMethod(), request.getRequestURI(), ex.getMessage());
            return; // đã có tham số HttpServletResponse nên Spring coi là đã xử lý xong, không render view
        }

        log.error("I/O error while handling {} {}", request.getMethod(), request.getRequestURI(), ex);
        if (!response.isCommitted()) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    static boolean isClientAbort(Throwable ex) {
        Throwable current = ex;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof AsyncRequestNotUsableException
                    || "ClientAbortException".equals(current.getClass().getSimpleName())) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                for (String marker : CLIENT_ABORT_MESSAGES) {
                    if (lower.contains(marker)) {
                        return true;
                    }
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
