package com.example.be_web_admin.dto.qbank;

import java.time.Instant;

/** Một đề xuất AI trong bảng duyệt (màn 5 - "Duyệt đề xuất AI"). */
public record AiSuggestionView(
        Long suggestionId,
        Integer seqNo,
        String issueType,
        String issueLabel,
        String severity,
        String targetField,
        String originalText,
        Integer spanStart,
        Integer spanEnd,
        String explanation,
        String proposedText,
        Integer suggestedDifficulty,
        String decision,
        Instant decidedAt,
        String rejectReason,
        String finalText) {
}
