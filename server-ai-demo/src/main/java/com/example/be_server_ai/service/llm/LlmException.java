package com.example.be_server_ai.service.llm;

import com.example.be_server_ai.enums.AiErrorCode;

/** Lỗi khi gọi hoặc đọc kết quả LLM. Mang mã lỗi của ER (ntt_ai_tasks.error_code) và phản hồi thô nếu có. */
public class LlmException extends RuntimeException {

    private final AiErrorCode code;
    private final String raw;

    public LlmException(AiErrorCode code, String message) {
        this(code, message, null);
    }

    public LlmException(AiErrorCode code, String message, String raw) {
        super(message);
        this.code = code;
        this.raw = raw;
    }

    public AiErrorCode getCode() {
        return code;
    }

    /** Nội dung model trả về (nếu có) để lưu vào raw_response phục vụ gỡ lỗi. */
    public String getRaw() {
        return raw;
    }

    /** Lỗi thuộc về node (mạng, token, 5xx): tính vào sức khỏe node. Lỗi nội dung model thì không. */
    public boolean isNodeFault() {
        return code == AiErrorCode.LLM_UNREACHABLE || code == AiErrorCode.LLM_TIMEOUT
                || code == AiErrorCode.LLM_UNAUTHORIZED || code == AiErrorCode.LLM_HTTP_5XX;
    }
}
