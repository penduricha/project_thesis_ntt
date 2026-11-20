package com.example.be_server_ai.enums;

/** Trạng thái một node Colab trong hash Redis ai:node:{name}. Lưu dạng tên hằng (IDLE, FULL, ...). */
public enum AiNodeStatus {
    /** Còn chỗ trống (số request đang chạy nhỏ hơn max-concurrency). */
    IDLE,
    /** Đủ max-concurrency: chờ, không tính là lỗi. */
    FULL,
    /** Đang nghỉ sau chuỗi lỗi liên tiếp, đến cooldownUntilMs mới thử lại. */
    COOLING,
    /** Tắt thủ công hoặc sai token: không dùng cho đến khi nạp lại cấu hình. */
    DISABLED
}
