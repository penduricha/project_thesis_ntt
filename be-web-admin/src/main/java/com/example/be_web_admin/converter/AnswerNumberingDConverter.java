package com.example.be_web_admin.converter;

import com.example.be_web_admin.enums.moodle.AnswerNumberingD;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Chuyển AnswerNumberingD sang/ra giá trị ENUM thực tế trong CSDL (không phụ thuộc tên hằng số Java). */
@Converter
public class AnswerNumberingDConverter implements AttributeConverter<AnswerNumberingD, String> {

    @Override
    public String convertToDatabaseColumn(AnswerNumberingD attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public AnswerNumberingD convertToEntityAttribute(String dbData) {
        return dbData == null ? null : AnswerNumberingD.fromDbValue(dbData);
    }
}
