package com.example.server_ai_worker.dto.ai;

public record NodeUpsertRequest(String url, String token, Boolean enabled, Integer maxConcurrency) {
}
