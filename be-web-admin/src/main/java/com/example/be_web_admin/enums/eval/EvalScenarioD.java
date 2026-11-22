package com.example.be_web_admin.enums.eval;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Ánh xạ 1-1 với ENUM của cột tương ứng trong CSDL v4 (giá trị lưu DB nằm ở {@link #dbValue()}).
 *
 * <p>Không dùng @Enumerated(STRING) trực tiếp: hằng số Java viết HOA còn DB viết thường, nên phải đi
 * qua converter.EvalScenarioDConverter (xem docs/review-csdl-v4-chot-vs-code.md mục C5).</p>
 */
public enum EvalScenarioD {

    E1("E1"),
    E2("E2"),
    E3("E3"),
    E4("E4"),
    E5("E5"),
    E6("E6"),
    E7("E7"),
    E8("E8");

    /** Dùng cho columnDefinition để kiểu cột và danh sách giá trị không bao giờ lệch nhau. */
    public static final String SQL_TYPE = "enum('E1','E2','E3','E4','E5','E6','E7','E8')";

    private final String dbValue;

    EvalScenarioD(String dbValue) {
        this.dbValue = dbValue;
    }

    @JsonValue
    public String dbValue() {
        return dbValue;
    }

    @JsonCreator
    public static EvalScenarioD fromDbValue(String value) {
        for (EvalScenarioD candidate : values()) {
            if (candidate.dbValue.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Giá trị không hợp lệ cho EvalScenarioD trong CSDL: " + value);
    }
}
