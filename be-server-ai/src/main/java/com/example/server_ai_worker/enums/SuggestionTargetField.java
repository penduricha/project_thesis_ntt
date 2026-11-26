package com.example.server_ai_worker.enums;

/**
 * Cột ntt_ai_suggestions.target_field. Không có correct_label: AI không được tự đổi đáp án đúng.
 */
public enum SuggestionTargetField implements ValueEnum {

    QUESTION("question"),
    STEM("stem"),
    OPTION_A("option_a"),
    OPTION_B("option_b"),
    OPTION_C("option_c"),
    OPTION_D("option_d");

    private final String value;

    SuggestionTargetField(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    public static SuggestionTargetField fromValue(String raw) {
        if (raw == null) {
            return null;
        }
        for (SuggestionTargetField f : values()) {
            if (f.value.equals(raw)) {
                return f;
            }
        }
        return null;
    }
}
