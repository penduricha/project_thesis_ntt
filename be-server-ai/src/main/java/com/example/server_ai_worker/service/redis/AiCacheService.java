package com.example.server_ai_worker.service.redis;

import java.util.Optional;

import com.example.server_ai_worker.enums.AiTaskStatus;
import com.example.server_ai_worker.enums.AiTaskType;
import com.example.server_ai_worker.model.redis.AiTaskStatusCache;

public interface AiCacheService {

    void putStatus(String taskId, AiTaskType type, AiTaskStatus status, Integer attemptCount, String errorCode);

    Optional<AiTaskStatusCache> getStatus(String taskId);

    Optional<String> getNlFilter(String queryHash);

    void putNlFilter(String queryHash, String filterJson, String promptVersion);
}
