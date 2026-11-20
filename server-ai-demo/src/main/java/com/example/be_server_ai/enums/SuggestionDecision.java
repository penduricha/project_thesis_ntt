package com.example.be_server_ai.enums;

/**
 * Quyết định của người duyệt: cột ntt_ai_suggestions.decision.
 */
public enum SuggestionDecision implements ValueEnum {

    PENDING("pending"),
    ACCEPTED("accepted"),
    REJECTED("rejected"),
    STALE("stale");

    private final String value;

    SuggestionDecision(String value) {
        this.value = value;
    }

    /** Giá trị chữ thường đúng như lưu trong cột ENUM của MariaDB. */
    @Override
    public String getValue() {
        return value;
    }
}
