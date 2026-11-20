package com.example.be_server_ai.enums;

/**
 * Loại tác vụ AI: cột ntt_ai_tasks.task_type.
 */
public enum AiTaskType implements ValueEnum {

    QUESTION_CHECK("question_check"),
    NL_SEARCH("nl_search");

    private final String value;

    AiTaskType(String value) {
        this.value = value;
    }

    /** Giá trị chữ thường đúng như lưu trong cột ENUM của MariaDB. */
    @Override
    public String getValue() {
        return value;
    }
}
