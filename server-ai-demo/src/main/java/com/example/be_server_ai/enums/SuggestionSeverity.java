package com.example.be_server_ai.enums;

/**
 * Mức nghiêm trọng của đề xuất: cột ntt_ai_suggestions.severity.
 */
public enum SuggestionSeverity implements ValueEnum {

    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");

    private final String value;

    SuggestionSeverity(String value) {
        this.value = value;
    }

    /** Giá trị chữ thường đúng như lưu trong cột ENUM của MariaDB. */
    @Override
    public String getValue() {
        return value;
    }
}
