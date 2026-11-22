package com.example.be_web_admin.dto.qbank;

import java.time.Instant;

public record QuestionDetail(
        Long questionId,
        Long subjectId,
        String subjectName,
        Long topicId,
        String topicName,
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
        String status,
        String validationStatus,
        Instant lastValidatedAt,
        String source,
        String contentHash,
        Integer version,
        Instant createdAt,
        Instant updatedAt,
        int revisionNo,
        long pendingSuggestionCount) {
}
