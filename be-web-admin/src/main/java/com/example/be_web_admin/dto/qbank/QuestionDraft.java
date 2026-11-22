package com.example.be_web_admin.dto.qbank;

import java.time.Instant;

/**
 * Bản nháp form tạo/sửa câu hỏi, lưu Redis {@code ui:draft:{adminId}:{new|questionId}} TTL 24h.
 * Giúp giảng viên không mất nội dung khi trình duyệt đóng/tải lại — không ghi vào MariaDB vì
 * nháp chưa phải dữ liệu nghiệp vụ.
 */
public record QuestionDraft(
        Long subjectId,
        Long topicId,
        String title,
        String stem,
        String optionA,
        String optionB,
        String optionC,
        String optionD,
        String correctLabel,
        String contentFormat,
        String generalFeedback,
        Integer difficulty,
        Instant savedAt) {
}
