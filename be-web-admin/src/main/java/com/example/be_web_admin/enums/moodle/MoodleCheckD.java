package com.example.be_web_admin.enums.moodle;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Ánh xạ 1-1 với ENUM của cột tương ứng trong CSDL v4 (giá trị lưu DB nằm ở {@link #dbValue()}).
 *
 * <p>Không dùng @Enumerated(STRING) trực tiếp: hằng số Java viết HOA còn DB viết thường, nên phải đi
 * qua converter.MoodleCheckDConverter (xem docs/review-csdl-v4-chot-vs-code.md mục C5).</p>
 */
public enum MoodleCheckD {

    UNKNOWN("unknown"),
    OK("ok"),
    FAILED("failed");

    /** Dùng cho columnDefinition để kiểu cột và danh sách giá trị không bao giờ lệch nhau. */
    public static final String SQL_TYPE = "enum('unknown','ok','failed')";

    private final String dbValue;

    MoodleCheckD(String dbValue) {
        this.dbValue = dbValue;
    }

    @JsonValue
    public String dbValue() {
        return dbValue;
    }

    @JsonCreator
    public static MoodleCheckD fromDbValue(String value) {
        for (MoodleCheckD candidate : values()) {
            if (candidate.dbValue.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Giá trị không hợp lệ cho MoodleCheckD trong CSDL: " + value);
    }
}
