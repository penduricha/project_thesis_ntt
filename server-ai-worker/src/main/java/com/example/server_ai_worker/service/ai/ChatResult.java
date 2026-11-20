package com.example.server_ai_worker.service.ai;

public record ChatResult(
        String content,
        String rawBody,
        String modelName,
        String modelDigest,
        String runtimeVersion,
        int evalCount,
        long evalDurationNs,
        int promptEvalCount,
        long totalDurationNs,
        String doneReason,
        long wallMs) {
}
