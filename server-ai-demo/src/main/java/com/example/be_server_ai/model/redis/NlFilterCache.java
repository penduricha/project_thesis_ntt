package com.example.be_server_ai.model.redis;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

/**
 * Cache bộ lọc tìm kiếm ngôn ngữ tự nhiên (UC06), khóa Hash ai:cache:nl:{sha256}, TTL 24 giờ.
 * Chỉ chứa bộ lọc do AI sinh ra, không chứa kết quả truy vấn, nên không làm lộ dữ liệu giữa người dùng:
 * Server 1 vẫn kiểm tra trường, toán tử, quyền rồi tự dựng truy vấn tham số hóa mỗi lần.
 * Khóa đã gồm prompt_version nên đổi prompt thì cache cũ tự không khớp.
 */
@RedisHash(value = AiRedisKeys.NL_CACHE_KEYSPACE, timeToLive = AiRedisKeys.NL_CACHE_TTL_SECONDS)
public class NlFilterCache {

    @Id
    // sha256 (hex) của câu hỏi đã chuẩn hóa + prompt_version.
    private String queryHash;

    // Chỉ lưu bộ lọc (trường, toán tử, giá trị), không lưu danh sách câu hỏi.
    private String filterJson;

    private String promptVersion;

    private Long createdAtMs;

    public NlFilterCache() {
    }

    public NlFilterCache(String queryHash, String filterJson, String promptVersion, Long createdAtMs) {
        this.queryHash = queryHash;
        this.filterJson = filterJson;
        this.promptVersion = promptVersion;
        this.createdAtMs = createdAtMs;
    }

    public String getQueryHash() {
        return queryHash;
    }

    public void setQueryHash(String queryHash) {
        this.queryHash = queryHash;
    }

    public String getFilterJson() {
        return filterJson;
    }

    public void setFilterJson(String filterJson) {
        this.filterJson = filterJson;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public Long getCreatedAtMs() {
        return createdAtMs;
    }

    public void setCreatedAtMs(Long createdAtMs) {
        this.createdAtMs = createdAtMs;
    }
}
