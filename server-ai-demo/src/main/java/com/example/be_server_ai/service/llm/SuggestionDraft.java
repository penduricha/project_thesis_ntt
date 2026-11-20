package com.example.be_server_ai.service.llm;

import com.example.be_server_ai.enums.SuggestionSeverity;
import com.example.be_server_ai.enums.SuggestionTargetField;

/** Một đề xuất sửa sắp ghi vào ntt_ai_suggestions (seq_no do service đánh số). */
public record SuggestionDraft(
        String issueType,
        SuggestionSeverity severity,
        SuggestionTargetField targetField,
        String originalText,
        String proposedText,
        String explanation) {
}
