package com.example.server_ai_worker.dto.ai;

import com.example.server_ai_worker.model.jpa.AiSuggestion;

public record SuggestionView(
        int seqNo,
        String issueType,
        String severity,
        String targetField,
        String originalText,
        Integer spanStart,
        Integer spanEnd,
        String proposedText,
        String explanation,
        Integer suggestedDifficulty,
        String decision) {

    public static SuggestionView of(AiSuggestion s) {
        return new SuggestionView(
                s.getSeqNo() == null ? 0 : s.getSeqNo(),
                s.getIssueType(),
                s.getSeverity() == null ? null : s.getSeverity().getValue(),
                s.getTargetField() == null ? null : s.getTargetField().getValue(),
                s.getOriginalText(),
                s.getSpanStart(),
                s.getSpanEnd(),
                s.getProposedText(),
                s.getExplanation(),
                s.getSuggestedDifficulty(),
                s.getDecision() == null ? null : s.getDecision().getValue());
    }
}
