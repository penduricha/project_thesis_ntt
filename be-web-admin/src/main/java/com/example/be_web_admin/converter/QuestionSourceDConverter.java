package com.example.be_web_admin.converter;

import com.example.be_web_admin.enums.qbank.QuestionSourceD;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Chuyển QuestionSourceD sang/ra giá trị ENUM thực tế trong CSDL (không phụ thuộc tên hằng số Java). */
@Converter
public class QuestionSourceDConverter implements AttributeConverter<QuestionSourceD, String> {

    @Override
    public String convertToDatabaseColumn(QuestionSourceD attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public QuestionSourceD convertToEntityAttribute(String dbData) {
        return dbData == null ? null : QuestionSourceD.fromDbValue(dbData);
    }
}
