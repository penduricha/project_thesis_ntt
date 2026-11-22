package com.example.be_web_admin.converter;

import com.example.be_web_admin.enums.ai.SeverityD;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Chuyển SeverityD sang/ra giá trị ENUM thực tế trong CSDL (không phụ thuộc tên hằng số Java). */
@Converter
public class SeverityDConverter implements AttributeConverter<SeverityD, String> {

    @Override
    public String convertToDatabaseColumn(SeverityD attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public SeverityD convertToEntityAttribute(String dbData) {
        return dbData == null ? null : SeverityD.fromDbValue(dbData);
    }
}
