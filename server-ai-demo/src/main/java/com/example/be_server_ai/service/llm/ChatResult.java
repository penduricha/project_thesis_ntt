package com.example.be_server_ai.service.llm;

/** Kết quả một lượt chat. Thời gian lấy từ số liệu Ollama (total_duration, eval_duration...) quy ra mili giây. */
public record ChatResult(
        String content,
        String model,
        long totalMs,
        long loadMs,
        int promptTokens,
        int evalTokens,
        double tokensPerSec,
        long wallMs,
        String rawBody) {
}
