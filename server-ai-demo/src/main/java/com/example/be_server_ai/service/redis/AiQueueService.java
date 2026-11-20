package com.example.be_server_ai.service.redis;

import java.time.Duration;

/** Hàng đợi ai:queue (List) và kênh ai:done (Pub/Sub). Id task là UUID; dữ liệu thật nằm ở MariaDB. */
public interface AiQueueService {

    /** LPUSH ai:queue. Ném lỗi nếu Redis không nhận (người gọi quyết định xử lý). */
    void push(String taskId);

    /** BRPOP ai:queue, chặn tối đa block; trả null nếu hết thời gian. block phải nhỏ hơn spring.data.redis.timeout. */
    String pop(Duration block);

    long length();

    /** PUBLISH ai:done {taskId, status}. Lỗi chỉ ghi log (kênh Pub/Sub là thông báo, không phải nguồn sự thật). */
    void publishDone(String taskId, String status);
}
