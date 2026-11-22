package com.example.be_web_admin.enums.moodle;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Ánh xạ 1-1 với ENUM của cột tương ứng trong CSDL v4 (giá trị lưu DB nằm ở {@link #dbValue()}).
 *
 * <p>Không dùng @Enumerated(STRING) trực tiếp: hằng số Java viết HOA còn DB viết thường, nên phải đi
 * qua converter.ExchangeStatusDConverter (xem docs/review-csdl-v4-chot-vs-code.md mục C5).</p>
 */
public enum ExchangeStatusD {

    PENDING("pending"),
    SUCCEEDED("succeeded"),
    PARTIAL("partial"),
    FAILED("failed");

    /** Dùng cho columnDefinition để kiểu cột và danh sách giá trị không bao giờ lệch nhau. */
    public static final String SQL_TYPE = "enum('pending','succeeded','partial','failed')";

    private final String dbValue;

    ExchangeStatusD(String dbValue) {
        this.dbValue = dbValue;
    }

    @JsonValue
    public String dbValue() {
        return dbValue;
    }

    @JsonCreator
    public static ExchangeStatusD fromDbValue(String value) {
        for (ExchangeStatusD candidate : values()) {
            if (candidate.dbValue.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Giá trị không hợp lệ cho ExchangeStatusD trong CSDL: " + value);
    }
}
