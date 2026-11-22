package com.example.be_web_admin.dto.qbank;

import java.time.Instant;

/**
 * Một dòng "Hoạt động gần đây" ở Dashboard. Lưu trong Redis list {@code ui:activity:{adminId}}
 * (LPUSH + LTRIM 20 + TTL 7 ngày) vì không cần bảng riêng cho 1 tính năng hiển thị.
 */
public record ActivityItem(
        String type,
        String message,
        String refType,
        Long refId,
        Instant at) {

    public static ActivityItem of(String type, String message, String refType, Long refId) {
        return new ActivityItem(type, message, refType, refId, Instant.now());
    }
}
