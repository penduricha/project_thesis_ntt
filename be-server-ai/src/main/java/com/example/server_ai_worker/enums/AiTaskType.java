package com.example.server_ai_worker.enums;

public enum AiTaskType implements ValueEnum {

    QUESTION_CHECK("question_check"),
    NL_SEARCH("nl_search");

    private final String value;

    AiTaskType(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }
}
