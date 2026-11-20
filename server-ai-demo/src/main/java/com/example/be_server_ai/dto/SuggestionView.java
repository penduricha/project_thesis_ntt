package com.example.be_server_ai.dto;

import com.example.be_server_ai.model.jpa.AiSuggestion;

public record SuggestionView(
        int seqNo,
        String issueType,
        String severity,
        String targetField,
        String originalText,
        String proposedText,
        String explanation,
        String decision) {

    public static SuggestionView of(AiSuggestion s) {
        return new SuggestionView(
                s.getSeqNo() == null ? 0 : s.getSeqNo(),
                s.getIssueType(),
                s.getSeverity() == null ? null : s.getSeverity().getValue(),
                s.getTargetField() == null ? null : s.getTargetField().getValue(),
                s.getOriginalText(),
                s.getProposedText(),
                s.getExplanation(),
                s.getDecision() == null ? null : s.getDecision().getValue());
    }
}
