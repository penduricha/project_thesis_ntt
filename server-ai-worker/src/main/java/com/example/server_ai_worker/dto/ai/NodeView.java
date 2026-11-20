package com.example.server_ai_worker.dto.ai;

import com.example.server_ai_worker.enums.AiNodeStatus;

/** Trạng thái một node gửi về trình duyệt. KHÔNG bao giờ chứa token. */
public record NodeView(
        String name,
        String url,
        boolean enabled,
        boolean hasToken,
        AiNodeStatus status,
        int inFlight,
        int maxConcurrency,
        int failCount,
        long cooldownUntilMs,
        long lastLatencyMs,
        double tokensPerSec,
        String modelDigest,
        String runtimeVersion,
        String lastError) {
}
