package com.example.server_ai_worker.dto.ai;

public record NodeCheckResponse(
        boolean reachable,
        boolean authorized,
        Boolean modelPresent,
        Long latencyMs,
        String modelDigest,
        String runtimeVersion,
        String error) {
}
