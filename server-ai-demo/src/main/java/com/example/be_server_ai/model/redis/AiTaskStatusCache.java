package com.example.be_server_ai.model.redis;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

/**
 * Cache trạng thái task cho polling (UC09), khóa Hash ai:task:{task_id}, TTL khoảng 1 giờ. Tùy chọn.
 * Chỉ là bản sao để giảm đọc DB: nguồn sự thật vẫn là ntt_ai_tasks.
 *
 * <p>Chỉ dùng findById / save / deleteById. Không dùng findAll / count: Spring Data Redis giữ một Set id
 * của keyspace và Set này không tự bớt khi hash hết hạn (listener hết hạn mặc định đang tắt).
 */
@RedisHash(value = AiRedisKeys.TASK_KEYSPACE, timeToLive = AiRedisKeys.TASK_TTL_SECONDS)
public class AiTaskStatusCache {

    @Id
    private String taskId;

    // Giá trị chữ thường của AiTaskType (question_check, nl_search).
    private String taskType;

    // Giá trị chữ thường của AiTaskStatus (pending, running, ...).
    private String status;

    private Integer attemptCount;

    private String errorCode;

    // Epoch mili giây (UTC). Dùng Long cho đơn giản và chắc chắn khi lưu hash.
    private Long updatedAtMs;

    public AiTaskStatusCache() {
    }

    public AiTaskStatusCache(String taskId, String taskType, String status, Integer attemptCount,
                             String errorCode, Long updatedAtMs) {
        this.taskId = taskId;
        this.taskType = taskType;
        this.status = status;
        this.attemptCount = attemptCount;
        this.errorCode = errorCode;
        this.updatedAtMs = updatedAtMs;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public Long getUpdatedAtMs() {
        return updatedAtMs;
    }

    public void setUpdatedAtMs(Long updatedAtMs) {
        this.updatedAtMs = updatedAtMs;
    }
}
