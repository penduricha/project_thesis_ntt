package com.example.server_ai_worker.model.redis;

public record AiDoneMessage(String taskId, String status) {
}
