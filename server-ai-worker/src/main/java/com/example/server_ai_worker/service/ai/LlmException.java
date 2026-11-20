package com.example.server_ai_worker.service.ai;

import com.example.server_ai_worker.enums.AiErrorCode;

public class LlmException extends RuntimeException {

    private final AiErrorCode code;
    private final String rawResponse;
    private final boolean jsonValid;

    public LlmException(AiErrorCode code, String message) {
        this(code, message, null, false);
    }

    public LlmException(AiErrorCode code, String message, String rawResponse, boolean jsonValid) {
        super(message);
        this.code = code;
        this.rawResponse = rawResponse;
        this.jsonValid = jsonValid;
    }

    public AiErrorCode getCode() {
        return code;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public boolean isJsonValid() {
        return jsonValid;
    }
}
