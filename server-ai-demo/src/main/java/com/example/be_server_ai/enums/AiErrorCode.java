package com.example.be_server_ai.enums;

/**
 * Mã lỗi ghi vào ntt_ai_tasks.error_code (varchar(50)); giá trị lưu là name() của hằng.
 * CHECK của ER: task failed bắt buộc có error_code.
 */
public enum AiErrorCode {

    /** Không kết nối được tới node (Colab tắt, tunnel đổi URL). */
    LLM_UNREACHABLE(true),
    /** Quá thời gian chờ; giữ mỗi request dưới khoảng 90-120 s. */
    LLM_TIMEOUT(true),
    /** 401 từ proxy Colab: token sai hoặc đã đổi. */
    LLM_UNAUTHORIZED(true),
    /** 5xx hoặc 524 từ Cloudflare/Ollama. */
    LLM_HTTP_5XX(true),
    /** Phản hồi không phải JSON hợp lệ. */
    LLM_BAD_JSON(true),
    /** JSON hợp lệ nhưng sai schema hoặc sai issue_type. */
    LLM_SCHEMA_MISMATCH(true),
    /** HTTP 4xx khác 401/403 từ Colab (ví dụ Ollama từ chối tham số): thử lại cũng vậy, nên không thử lại. */
    LLM_BAD_REQUEST(false),
    /** input_json thiếu hoặc sai dạng: lỗi dữ liệu đầu vào, không thử lại. */
    BAD_INPUT(false),
    /** Lỗi bất ngờ trong Server AI. */
    INTERNAL_ERROR(false),
    /** Chờ trong hàng đợi quá max-wait. Kết thúc, không thử lại. */
    QUEUE_TIMEOUT(false),
    /** Hết lượt attempt_count (tối đa 5). Kết thúc, không thử lại. */
    MAX_ATTEMPTS(false);

    private final boolean retryable;

    AiErrorCode(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
