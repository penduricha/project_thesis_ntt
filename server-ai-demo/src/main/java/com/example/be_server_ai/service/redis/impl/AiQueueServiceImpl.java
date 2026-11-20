package com.example.be_server_ai.service.redis.impl;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.example.be_server_ai.model.redis.AiDoneMessage;
import com.example.be_server_ai.model.redis.AiRedisKeys;
import com.example.be_server_ai.service.redis.AiQueueService;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AiQueueServiceImpl implements AiQueueService {

    private static final Logger log = LoggerFactory.getLogger(AiQueueServiceImpl.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper om;

    public AiQueueServiceImpl(StringRedisTemplate redis, ObjectMapper om) {
        this.redis = redis;
        this.om = om;
    }

    @Override
    public void push(String taskId) {
        // Producer LPUSH, consumer BRPOP: task cũ nhất ra trước (FIFO).
        redis.opsForList().leftPush(AiRedisKeys.QUEUE, taskId);
    }

    @Override
    public String pop(Duration block) {
        // Spring Data Redis dùng kết nối riêng cho lệnh chặn nên không làm nghẽn các lệnh khác.
        return redis.opsForList().rightPop(AiRedisKeys.QUEUE, block);
    }

    @Override
    public long length() {
        Long n = redis.opsForList().size(AiRedisKeys.QUEUE);
        return n == null ? 0 : n;
    }

    @Override
    public void publishDone(String taskId, String status) {
        try {
            redis.convertAndSend(AiRedisKeys.DONE_CHANNEL, om.writeValueAsString(new AiDoneMessage(taskId, status)));
        } catch (Exception e) {
            log.warn("Không PUBLISH được ai:done cho task {}: {}", taskId, e.toString());
        }
    }
}
