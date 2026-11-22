package com.example.be_web_admin.converter;

import com.example.be_web_admin.enums.qbank.ValidationStatusD;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Chuyển ValidationStatusD sang/ra giá trị ENUM thực tế trong CSDL (không phụ thuộc tên hằng số Java). */
@Converter
public class ValidationStatusDConverter implements AttributeConverter<ValidationStatusD, String> {

    @Override
    public String convertToDatabaseColumn(ValidationStatusD attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public ValidationStatusD convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ValidationStatusD.fromDbValue(dbData);
    }
}
