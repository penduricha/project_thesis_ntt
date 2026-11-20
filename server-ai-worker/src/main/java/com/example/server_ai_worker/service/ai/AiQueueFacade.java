package com.example.server_ai_worker.service.ai;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.example.server_ai_worker.service.redis.AiQueueService;

/**
 * Lớp bọc mỏng quanh AiQueueService giúp dễ test và tách biệt giao tiếp Redis khỏi logic xử lý task.
 */
@Component
public class AiQueueFacade {

    private final AiQueueService queue;

    public AiQueueFacade(AiQueueService queue) {
        this.queue = queue;
    }

    public void push(String taskId) {
        queue.push(taskId);
    }

    public String pop(Duration block) {
        return queue.pop(block);
    }

    public long length() {
        return queue.length();
    }

    public void publishDone(String taskId, String status) {
        queue.publishDone(taskId, status);
    }
}
