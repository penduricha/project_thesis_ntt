package com.example.be_web_admin.enums.ai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Ánh xạ 1-1 với ENUM của cột tương ứng trong CSDL v4 (giá trị lưu DB nằm ở {@link #dbValue()}).
 *
 * <p>Không dùng @Enumerated(STRING) trực tiếp: hằng số Java viết HOA còn DB viết thường, nên phải đi
 * qua converter.TaskTypeDConverter (xem docs/review-csdl-v4-chot-vs-code.md mục C5).</p>
 */
public enum TaskTypeD {

    QUESTION_CHECK("question_check"),
    NL_SEARCH("nl_search");

    /** Dùng cho columnDefinition để kiểu cột và danh sách giá trị không bao giờ lệch nhau. */
    public static final String SQL_TYPE = "enum('question_check','nl_search')";

    private final String dbValue;

    TaskTypeD(String dbValue) {
        this.dbValue = dbValue;
    }

    @JsonValue
    public String dbValue() {
        return dbValue;
    }

    @JsonCreator
    public static TaskTypeD fromDbValue(String value) {
        for (TaskTypeD candidate : values()) {
            if (candidate.dbValue.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Giá trị không hợp lệ cho TaskTypeD trong CSDL: " + value);
    }
}
