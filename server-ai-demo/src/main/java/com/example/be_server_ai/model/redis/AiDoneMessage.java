package com.example.be_server_ai.model.redis;

/**
 * Nội dung tin nhắn Pub/Sub trên kênh ai:done (Server 2 gửi, Server 1 nghe).
 * Pub/Sub không lưu tin: nếu lỡ thì MariaDB vẫn là nguồn sự thật, Server 1 đối chiếu khi Dashboard hỏi.
 *
 * @param taskId khóa ntt_ai_tasks.task_id
 * @param status giá trị chữ thường của {@link com.example.be_server_ai.enums.AiTaskStatus#getValue()}
 */
public record AiDoneMessage(String taskId, String status) {
}
