package com.example.be_web_admin.enums.moodle;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Ánh xạ 1-1 với ENUM của cột tương ứng trong CSDL v4 (giá trị lưu DB nằm ở {@link #dbValue()}).
 *
 * <p>Không dùng @Enumerated(STRING) trực tiếp: hằng số Java viết HOA còn DB viết thường, nên phải đi
 * qua converter.ExchangeItemStatusDConverter (xem docs/review-csdl-v4-chot-vs-code.md mục C5).</p>
 */
public enum ExchangeItemStatusD {

    OK("ok"),
    SKIPPED("skipped"),
    FAILED("failed");

    /** Dùng cho columnDefinition để kiểu cột và danh sách giá trị không bao giờ lệch nhau. */
    public static final String SQL_TYPE = "enum('ok','skipped','failed')";

    private final String dbValue;

    ExchangeItemStatusD(String dbValue) {
        this.dbValue = dbValue;
    }

    @JsonValue
    public String dbValue() {
        return dbValue;
    }

    @JsonCreator
    public static ExchangeItemStatusD fromDbValue(String value) {
        for (ExchangeItemStatusD candidate : values()) {
            if (candidate.dbValue.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Giá trị không hợp lệ cho ExchangeItemStatusD trong CSDL: " + value);
    }
}
