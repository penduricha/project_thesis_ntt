package com.example.server_ai_worker.enums;

public enum SuggestionDecision implements ValueEnum {

    PENDING("pending"),
    ACCEPTED("accepted"),
    REJECTED("rejected"),
    STALE("stale");

    private final String value;

    SuggestionDecision(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }
}
