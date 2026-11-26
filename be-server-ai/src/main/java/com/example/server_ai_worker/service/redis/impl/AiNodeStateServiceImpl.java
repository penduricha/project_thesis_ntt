package com.example.server_ai_worker.service.redis.impl;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.example.server_ai_worker.model.redis.AiNodeState;
import com.example.server_ai_worker.model.redis.AiRedisKeys;
import com.example.server_ai_worker.service.redis.AiNodeStateService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Lưu trạng thái node Colab vào Redis bằng StringRedisTemplate (TTL 300s, không tạo Set rác).
 */
@Service
public class AiNodeStateServiceImpl implements AiNodeStateService {

    private static final Logger log = LoggerFactory.getLogger(AiNodeStateServiceImpl.class);
    private static final Duration NODE_TTL = Duration.ofSeconds(AiRedisKeys.NODE_TTL_SECONDS);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public AiNodeStateServiceImpl(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    @Override
    public void save(AiNodeState state) {
        if (state == null || state.getName() == null) {
            return;
        }
        state.setUpdatedAtMs(System.currentTimeMillis());
        try {
            redis.opsForValue().set(
                    AiRedisKeys.nodeStateKey(state.getName()),
                    json.writeValueAsString(state),
                    NODE_TTL);
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Không ghi được trạng thái node {}: {}", state.getName(), e.getMessage());
        }
    }
}
