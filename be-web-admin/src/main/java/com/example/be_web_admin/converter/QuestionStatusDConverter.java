package com.example.be_web_admin.converter;

import com.example.be_web_admin.enums.qbank.QuestionStatusD;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Chuyển QuestionStatusD sang/ra giá trị ENUM thực tế trong CSDL (không phụ thuộc tên hằng số Java). */
@Converter
public class QuestionStatusDConverter implements AttributeConverter<QuestionStatusD, String> {

    @Override
    public String convertToDatabaseColumn(QuestionStatusD attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public QuestionStatusD convertToEntityAttribute(String dbData) {
        return dbData == null ? null : QuestionStatusD.fromDbValue(dbData);
    }
}
