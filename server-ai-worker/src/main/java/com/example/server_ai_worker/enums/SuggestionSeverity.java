package com.example.server_ai_worker.enums;

public enum SuggestionSeverity implements ValueEnum {

    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");

    private final String value;

    SuggestionSeverity(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    public static SuggestionSeverity fromValue(String raw) {
        if (raw == null) {
            return null;
        }
        for (SuggestionSeverity s : values()) {
            if (s.value.equals(raw)) {
                return s;
            }
        }
        return null;
    }
}
