package com.example.server_ai_worker.service.redis;

import java.time.Duration;

public interface AiQueueService {

    void push(String taskId);

    String pop(Duration block);

    long length();

    void publishDone(String taskId, String status);
}
