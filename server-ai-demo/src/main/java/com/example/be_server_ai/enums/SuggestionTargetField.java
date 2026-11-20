package com.example.be_server_ai.enums;

/**
 * Trường mà đề xuất nhắm tới: cột ntt_ai_suggestions.target_field. Không có correct_label: AI không được đổi đáp án đúng.
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

    /** Giá trị chữ thường đúng như lưu trong cột ENUM của MariaDB. */
    @Override
    public String getValue() {
        return value;
    }
}
