package com.example.server_ai_worker.enums;

/**
 * Mã lỗi ghi vào ntt_ai_tasks.error_code (varchar(50)).
 * CHECK của ER v4: task failed bắt buộc có error_code.
 */
public enum AiErrorCode {

    LLM_UNREACHABLE(true),
    LLM_TIMEOUT(true),
    LLM_UNAUTHORIZED(true),
    LLM_HTTP_5XX(true),
    LLM_TRUNCATED(true),
    LLM_BAD_JSON(true),
    LLM_SCHEMA_MISMATCH(true),
    LLM_BAD_REQUEST(false),
    BAD_INPUT(false),
    INTERNAL_ERROR(false),
    QUEUE_TIMEOUT(false),
    MAX_ATTEMPTS(false);

    private final boolean retryable;

    AiErrorCode(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
