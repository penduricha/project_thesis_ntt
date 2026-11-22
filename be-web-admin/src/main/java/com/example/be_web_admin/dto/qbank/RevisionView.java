package com.example.be_web_admin.dto.qbank;

import java.time.Instant;

/** Một dòng lịch sử sửa câu hỏi (không kèm snapshot_json để danh sách nhẹ). */
public record RevisionView(
        Long revisionId,
        Integer revNo,
        String changeSource,
        String changeNote,
        Long changedBy,
        Instant changedAt) {
}
