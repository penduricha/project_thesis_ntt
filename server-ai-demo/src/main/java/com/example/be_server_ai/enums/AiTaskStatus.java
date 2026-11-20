package com.example.be_server_ai.enums;

/**
 * Trạng thái tác vụ AI: cột ntt_ai_tasks.status.
 */
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

    /** Giá trị chữ thường đúng như lưu trong cột ENUM của MariaDB. */
    @Override
    public String getValue() {
        return value;
    }

    /** Đã kết thúc: theo CHECK của ER, mọi trạng thái này phải có finished_at. */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }
}
