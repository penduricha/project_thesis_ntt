package com.example.server_ai_worker.model.redis;

import com.example.server_ai_worker.enums.AiNodeStatus;

public class AiNodeState {

    private String name;
    private AiNodeStatus status;
    private Integer inFlight;
    private Integer maxConcurrency;
    private Integer failCount;
    private Long cooldownUntilMs;
    private Long lastSuccessMs;
    private Long lastFailureMs;
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
