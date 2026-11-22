package com.example.be_web_admin.converter;

import com.example.be_web_admin.enums.moodle.ExchangeDirectionD;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Chuyển ExchangeDirectionD sang/ra giá trị ENUM thực tế trong CSDL (không phụ thuộc tên hằng số Java). */
@Converter
public class ExchangeDirectionDConverter implements AttributeConverter<ExchangeDirectionD, String> {

    @Override
    public String convertToDatabaseColumn(ExchangeDirectionD attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public ExchangeDirectionD convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ExchangeDirectionD.fromDbValue(dbData);
    }
}
