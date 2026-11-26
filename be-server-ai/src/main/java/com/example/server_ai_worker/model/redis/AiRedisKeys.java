package com.example.server_ai_worker.model.redis;

/**
 * Tên khóa, kênh và TTL Redis của tính năng AI. Mọi khóa bắt đầu bằng "ai:".
 */
public final class AiRedisKeys {

    private AiRedisKeys() {
    }

    public static final String PREFIX = "ai:";
    public static final String QUEUE = "ai:queue";
    public static final String DONE_CHANNEL = "ai:done";

    public static String taskStatusKey(String taskId) {
        return "ai:task:" + taskId;
    }

    public static String nlCacheKey(String queryHash) {
        return "ai:cache:nl:" + queryHash;
    }

    public static String nodeStateKey(String nodeName) {
        return "ai:node:" + nodeName;
    }

    public static final long TASK_TTL_SECONDS = 3600L;
    public static final long NL_CACHE_TTL_SECONDS = 86400L;
    public static final long NODE_TTL_SECONDS = 300L;
}
