package com.example.be_server_ai.dto;

import com.example.be_server_ai.enums.AiNodeStatus;

/** Trạng thái một node gửi về trình duyệt. KHÔNG có token. */
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
        String lastError) {
}
