package com.example.server_ai_worker.service.redis.impl;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.example.server_ai_worker.model.redis.AiDoneMessage;
import com.example.server_ai_worker.model.redis.AiRedisKeys;
import com.example.server_ai_worker.service.redis.AiQueueService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AiQueueServiceImpl implements AiQueueService {

    private static final Logger log = LoggerFactory.getLogger(AiQueueServiceImpl.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public AiQueueServiceImpl(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    @Override
    public void push(String taskId) {
        redis.opsForList().rightPush(AiRedisKeys.QUEUE, taskId);
    }

    @Override
    public String pop(Duration block) {
        return redis.opsForList().leftPop(AiRedisKeys.QUEUE, block.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public long length() {
        Long size = redis.opsForList().size(AiRedisKeys.QUEUE);
        return size == null ? 0L : size;
    }

    @Override
    public void publishDone(String taskId, String status) {
        try {
            String payload = json.writeValueAsString(new AiDoneMessage(taskId, status));
            redis.convertAndSend(AiRedisKeys.DONE_CHANNEL, payload);
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Không thể phát thông báo xong cho task {}: {}", taskId, e.getMessage());
        }
    }
}
