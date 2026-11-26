package com.example.server_ai_worker.enums;

public enum AiTaskStatus implements ValueEnum {

    PENDING("pending"),
    RUNNING("running"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    CANCELLED("cancelled");

    private final String value;

    AiTaskStatus(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }
}
