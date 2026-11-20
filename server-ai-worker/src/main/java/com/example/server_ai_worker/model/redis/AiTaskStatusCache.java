package com.example.server_ai_worker.model.redis;

public record AiTaskStatusCache(
        String taskId,
        String taskType,
        String status,
        Integer attemptCount,
        String errorCode,
        Long updatedAtMs) {
}
