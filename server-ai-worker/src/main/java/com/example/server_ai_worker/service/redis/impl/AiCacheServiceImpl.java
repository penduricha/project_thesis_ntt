package com.example.server_ai_worker.service.redis.impl;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.example.server_ai_worker.enums.AiTaskStatus;
import com.example.server_ai_worker.enums.AiTaskType;
import com.example.server_ai_worker.model.redis.AiRedisKeys;
import com.example.server_ai_worker.model.redis.AiTaskStatusCache;
import com.example.server_ai_worker.service.redis.AiCacheService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Lưu trạng thái task và cache lọc tìm kiếm trực tiếp bằng StringRedisTemplate (TTL tự dọn,
 * không dùng @RedisHash để tránh rác keyspace Set trong Redis).
 */
@Service
public class AiCacheServiceImpl implements AiCacheService {

    private static final Logger log = LoggerFactory.getLogger(AiCacheServiceImpl.class);
    private static final Duration TASK_TTL = Duration.ofSeconds(AiRedisKeys.TASK_TTL_SECONDS);
    private static final Duration NL_CACHE_TTL = Duration.ofSeconds(AiRedisKeys.NL_CACHE_TTL_SECONDS);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public AiCacheServiceImpl(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    @Override
    public void putStatus(String taskId, AiTaskType type, AiTaskStatus status, Integer attemptCount,
                          String errorCode) {
        AiTaskStatusCache payload = new AiTaskStatusCache(
                taskId,
                type.getValue(),
                status.getValue(),
                attemptCount,
                errorCode,
                System.currentTimeMillis());
        try {
            redis.opsForValue().set(
                    AiRedisKeys.taskStatusKey(taskId),
                    json.writeValueAsString(payload),
                    TASK_TTL);
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Không ghi được cache trạng thái task {}: {}", taskId, e.getMessage());
        }
    }

    @Override
    public Optional<AiTaskStatusCache> getStatus(String taskId) {
        try {
            String raw = redis.opsForValue().get(AiRedisKeys.taskStatusKey(taskId));
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(json.readValue(raw, AiTaskStatusCache.class));
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Không đọc được cache trạng thái task {}: {}", taskId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> getNlFilter(String queryHash) {
        try {
            String raw = redis.opsForValue().get(AiRedisKeys.nlCacheKey(queryHash));
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            JsonNode root = json.readTree(raw);
            JsonNode filterNode = root.get("filterJson");
            return filterNode != null && filterNode.isTextual()
                    ? Optional.of(filterNode.asText())
                    : Optional.empty();
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Không đọc được cache nl_search {}: {}", queryHash, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void putNlFilter(String queryHash, String filterJson, String promptVersion) {
        try {
            String payload = json.writeValueAsString(new NlFilterEntry(
                    queryHash, filterJson, promptVersion, System.currentTimeMillis()));
            redis.opsForValue().set(AiRedisKeys.nlCacheKey(queryHash), payload, NL_CACHE_TTL);
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Không ghi được cache nl_search {}: {}", queryHash, e.getMessage());
        }
    }

    private record NlFilterEntry(String queryHash, String filterJson, String promptVersion, long createdAtMs) {
    }
}
