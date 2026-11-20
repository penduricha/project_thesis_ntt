package com.example.be_server_ai.service.redis;

import java.util.Optional;

import com.example.be_server_ai.enums.AiTaskStatus;
import com.example.be_server_ai.enums.AiTaskType;
import com.example.be_server_ai.model.redis.AiTaskStatusCache;

/** Cache trạng thái task (ai:task:{id}) và bộ lọc tìm kiếm tự nhiên (ai:cache:nl:{sha256}). Chỉ là bộ nhớ đệm, lỗi Redis không làm hỏng luồng. */
public interface AiCacheService {

    void putStatus(String taskId, AiTaskType type, AiTaskStatus status, Integer attemptCount, String errorCode);

    Optional<AiTaskStatusCache> getStatus(String taskId);

    Optional<String> getNlFilter(String queryHash);

    void putNlFilter(String queryHash, String filterJson, String promptVersion);
}
