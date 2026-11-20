package com.example.be_server_ai.model.redis;

/**
 * Tên khóa, kênh và TTL Redis của tính năng AI. Mọi khóa bắt đầu bằng "ai:" để không đụng
 * các khóa xác thực hiện có (OTP, blacklist token, giới hạn đăng nhập).
 *
 * <p>ai:queue (List) và ai:done (Pub/Sub) không phải hash nên không có model @RedisHash:
 * dùng StringRedisTemplate với hai hằng bên dưới. Các hash còn lại có model riêng cùng package.
 */
public final class AiRedisKeys {

    private AiRedisKeys() {
    }

    public static final String PREFIX = "ai:";

    /** List: Server 1 LPUSH, Server 2 BRPOP. Chỉ chứa task_id (UUID), không chứa nội dung câu hỏi. */
    public static final String QUEUE = "ai:queue";

    /** Pub/Sub: Server 2 PUBLISH {@link AiDoneMessage} dạng JSON sau khi ghi DB xong. */
    public static final String DONE_CHANNEL = "ai:done";

    /** Keyspace của {@link AiTaskStatusCache}: khóa thật là ai:task:{task_id}. */
    public static final String TASK_KEYSPACE = "ai:task";

    /** Keyspace của {@link NlFilterCache}: khóa thật là ai:cache:nl:{sha256}. */
    public static final String NL_CACHE_KEYSPACE = "ai:cache:nl";

    /** Keyspace của {@link AiNodeState}: khóa thật là ai:node:{name}. */
    public static final String NODE_KEYSPACE = "ai:node";

    public static final long TASK_TTL_SECONDS = 3600L;        // khoảng 1 giờ
    public static final long NL_CACHE_TTL_SECONDS = 86400L;   // 24 giờ
    public static final long NODE_TTL_SECONDS = 300L;         // ngắn: URL/token đổi mỗi lần Colab chạy lại
}
