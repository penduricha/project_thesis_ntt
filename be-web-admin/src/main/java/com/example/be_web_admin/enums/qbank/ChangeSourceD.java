package com.example.be_web_admin.enums.qbank;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Ánh xạ 1-1 với ENUM của cột tương ứng trong CSDL v4 (giá trị lưu DB nằm ở {@link #dbValue()}).
 *
 * <p>Không dùng @Enumerated(STRING) trực tiếp: hằng số Java viết HOA còn DB viết thường, nên phải đi
 * qua converter.ChangeSourceDConverter (xem docs/review-csdl-v4-chot-vs-code.md mục C5).</p>
 */
public enum ChangeSourceD {

    CREATE("create"),
    MANUAL("manual"),
    AI_SUGGESTION("ai_suggestion"),
    MOODLE_IMPORT("moodle_import"),
    ARCHIVE("archive"),
    RESTORE("restore");

    /** Dùng cho columnDefinition để kiểu cột và danh sách giá trị không bao giờ lệch nhau. */
    public static final String SQL_TYPE = "enum('create','manual','ai_suggestion','moodle_import','archive','restore')";

    private final String dbValue;

    ChangeSourceD(String dbValue) {
        this.dbValue = dbValue;
    }

    @JsonValue
    public String dbValue() {
        return dbValue;
    }

    @JsonCreator
    public static ChangeSourceD fromDbValue(String value) {
        for (ChangeSourceD candidate : values()) {
            if (candidate.dbValue.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Giá trị không hợp lệ cho ChangeSourceD trong CSDL: " + value);
    }
}
