package com.example.be_web_admin.enums.moodle;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Ánh xạ 1-1 với ENUM của cột tương ứng trong CSDL v4 (giá trị lưu DB nằm ở {@link #dbValue()}).
 *
 * <p>Không dùng @Enumerated(STRING) trực tiếp: hằng số Java viết HOA còn DB viết thường, nên phải đi
 * qua converter.AnswerNumberingDConverter (xem docs/review-csdl-v4-chot-vs-code.md mục C5).</p>
 */
public enum AnswerNumberingD {

    NONE("none"),
    ABC("abc"),
    ABCD("ABCD"),
    NUMERIC("123"),
    /*
     * Moodle còn 2 kiểu đánh số bằng số La Mã (xem questiontype.php:324 —
     * array('abc','ABCD','123','iii','IIII','none')). Thiếu 2 giá trị này thì profile
     * đọc từ Moodle có answer_numbering = 'iii' | 'IIII' sẽ ném IllegalArgumentException
     * trong fromDbValue() và trả 500 cho người dùng.
     */
    LOWER_ROMAN("iii"),
    UPPER_ROMAN("IIII");

    /** Dùng cho columnDefinition để kiểu cột và danh sách giá trị không bao giờ lệch nhau. */
    public static final String SQL_TYPE = "enum('none','abc','ABCD','123','iii','IIII')";

    private final String dbValue;

    AnswerNumberingD(String dbValue) {
        this.dbValue = dbValue;
    }

    @JsonValue
    public String dbValue() {
        return dbValue;
    }

    @JsonCreator
    public static AnswerNumberingD fromDbValue(String value) {
        for (AnswerNumberingD candidate : values()) {
            if (candidate.dbValue.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Giá trị không hợp lệ cho AnswerNumberingD trong CSDL: " + value);
    }
}
