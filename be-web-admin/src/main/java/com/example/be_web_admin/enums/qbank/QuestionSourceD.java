package com.example.be_web_admin.enums.qbank;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Ánh xạ 1-1 với ENUM của cột tương ứng trong CSDL v4 (giá trị lưu DB nằm ở {@link #dbValue()}).
 *
 * <p>Không dùng @Enumerated(STRING) trực tiếp: hằng số Java viết HOA còn DB viết thường, nên phải đi
 * qua converter.QuestionSourceDConverter (xem docs/review-csdl-v4-chot-vs-code.md mục C5).</p>
 */
public enum QuestionSourceD {

    MANUAL("manual"),
    MOODLE_IMPORT("moodle_import"),
    EVAL("eval");

    /** Dùng cho columnDefinition để kiểu cột và danh sách giá trị không bao giờ lệch nhau. */
    public static final String SQL_TYPE = "enum('manual','moodle_import','eval')";

    private final String dbValue;

    QuestionSourceD(String dbValue) {
        this.dbValue = dbValue;
    }

    @JsonValue
    public String dbValue() {
        return dbValue;
    }

    @JsonCreator
    public static QuestionSourceD fromDbValue(String value) {
        for (QuestionSourceD candidate : values()) {
            if (candidate.dbValue.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Giá trị không hợp lệ cho QuestionSourceD trong CSDL: " + value);
    }
}
