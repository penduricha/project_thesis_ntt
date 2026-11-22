package com.example.be_web_admin.dto.qbank;

import java.time.Instant;

/** Trạng thái task AI cho modal "Đang kiểm định..." — đọc Redis trước, DB sau. */
public record AiJobView(
        String taskId,
        Long questionId,
        String taskType,
        String status,
        Integer attemptCount,
        int suggestionCount,
        String errorCode,
        Instant createdAt,
        Instant updatedAt,
        int pollAfterMs,
        boolean fromCache) {
}
