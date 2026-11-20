package com.example.be_server_ai.model.redis;

import com.example.be_server_ai.enums.AiNodeStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

/**
 * Trạng thái một node Colab, khóa Hash ai:node:{name}, TTL ngắn. Chỉ Server 2 ghi và đọc.
 * Cần khi chạy nhiều bản Server 2 dùng chung 5 node; với một bản, giữ trong bộ nhớ là đủ.
 *
 * <p>Không chứa URL và token: cặp này lấy từ cấu hình hoặc biến môi trường và đổi mỗi lần Colab khởi động lại.
 * Hash hết hạn nghĩa là node chưa có tín hiệu gần đây, Server 2 khởi tạo lại từ cấu hình.
 */
@RedisHash(value = AiRedisKeys.NODE_KEYSPACE, timeToLive = AiRedisKeys.NODE_TTL_SECONDS)
public class AiNodeState {

    @Id
    // Tên node trong cấu hình (ví dụ colab-1). KHÔNG lưu URL hay token ở đây.
    private String name;

    private AiNodeStatus status;

    // Số request đang chạy trên node.
    private Integer inFlight;

    // Thường 3 (OLLAMA_NUM_PARALLEL).
    private Integer maxConcurrency;

    // Số lỗi liên tiếp; đạt ngưỡng (khởi đầu 3) thì chuyển COOLING.
    private Integer failCount;

    // Epoch mili giây; trước mốc này không giao việc cho node.
    private Long cooldownUntilMs;

    private Long lastSuccessMs;

    private Long lastFailureMs;

    // Tốc độ gần nhất (khoảng 12-14 token/s trên T4), để chọn node nhanh hơn.
    private Double tokensPerSec;

    private Long updatedAtMs;

    public AiNodeState() {
    }

    public AiNodeState(String name, AiNodeStatus status, Integer maxConcurrency) {
        this.name = name;
        this.status = status;
        this.inFlight = 0;
        this.maxConcurrency = maxConcurrency;
        this.failCount = 0;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AiNodeStatus getStatus() {
        return status;
    }

    public void setStatus(AiNodeStatus status) {
        this.status = status;
    }

    public Integer getInFlight() {
        return inFlight;
    }

    public void setInFlight(Integer inFlight) {
        this.inFlight = inFlight;
    }

    public Integer getMaxConcurrency() {
        return maxConcurrency;
    }

    public void setMaxConcurrency(Integer maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    public Integer getFailCount() {
        return failCount;
    }

    public void setFailCount(Integer failCount) {
        this.failCount = failCount;
    }

    public Long getCooldownUntilMs() {
        return cooldownUntilMs;
    }

    public void setCooldownUntilMs(Long cooldownUntilMs) {
        this.cooldownUntilMs = cooldownUntilMs;
    }

    public Long getLastSuccessMs() {
        return lastSuccessMs;
    }

    public void setLastSuccessMs(Long lastSuccessMs) {
        this.lastSuccessMs = lastSuccessMs;
    }

    public Long getLastFailureMs() {
        return lastFailureMs;
    }

    public void setLastFailureMs(Long lastFailureMs) {
        this.lastFailureMs = lastFailureMs;
    }

    public Double getTokensPerSec() {
        return tokensPerSec;
    }

    public void setTokensPerSec(Double tokensPerSec) {
        this.tokensPerSec = tokensPerSec;
    }

    public Long getUpdatedAtMs() {
        return updatedAtMs;
    }

    public void setUpdatedAtMs(Long updatedAtMs) {
        this.updatedAtMs = updatedAtMs;
    }
}
