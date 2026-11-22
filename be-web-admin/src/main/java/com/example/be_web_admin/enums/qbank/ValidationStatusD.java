package com.example.be_web_admin.enums.qbank;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Ánh xạ 1-1 với ENUM của cột tương ứng trong CSDL v4 (giá trị lưu DB nằm ở {@link #dbValue()}).
 *
 * <p>Không dùng @Enumerated(STRING) trực tiếp: hằng số Java viết HOA còn DB viết thường, nên phải đi
 * qua converter.ValidationStatusDConverter (xem docs/review-csdl-v4-chot-vs-code.md mục C5).</p>
 */
public enum ValidationStatusD {

    NOT_CHECKED("not_checked"),
    CLEAN("clean"),
    HAS_ISSUES("has_issues"),
    FAILED("failed"),
    STALE("stale");

    /** Dùng cho columnDefinition để kiểu cột và danh sách giá trị không bao giờ lệch nhau. */
    public static final String SQL_TYPE = "enum('not_checked','clean','has_issues','failed','stale')";

    private final String dbValue;

    ValidationStatusD(String dbValue) {
        this.dbValue = dbValue;
    }

    @JsonValue
    public String dbValue() {
        return dbValue;
    }

    @JsonCreator
    public static ValidationStatusD fromDbValue(String value) {
        for (ValidationStatusD candidate : values()) {
            if (candidate.dbValue.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Giá trị không hợp lệ cho ValidationStatusD trong CSDL: " + value);
    }
}
