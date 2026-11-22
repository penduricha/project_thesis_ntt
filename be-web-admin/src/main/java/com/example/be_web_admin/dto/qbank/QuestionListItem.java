package com.example.be_web_admin.dto.qbank;

import java.time.Instant;

/** Một dòng của bảng "Ngân hàng câu hỏi" (màn 4 trong UI_Accept_v9). */
public record QuestionListItem(
        Long questionId,
        Long subjectId,
        String subjectName,
        Long topicId,
        String topicName,
        String title,
        String stemPreview,
        String validationStatus,
        String status,
        Integer difficulty,
        String source,
        Instant updatedAt,
        Integer version) {
}
