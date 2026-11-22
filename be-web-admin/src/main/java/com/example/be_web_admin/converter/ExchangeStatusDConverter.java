package com.example.be_web_admin.converter;

import com.example.be_web_admin.enums.moodle.ExchangeStatusD;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Chuyển ExchangeStatusD sang/ra giá trị ENUM thực tế trong CSDL (không phụ thuộc tên hằng số Java). */
@Converter
public class ExchangeStatusDConverter implements AttributeConverter<ExchangeStatusD, String> {

    @Override
    public String convertToDatabaseColumn(ExchangeStatusD attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public ExchangeStatusD convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ExchangeStatusD.fromDbValue(dbData);
    }
}
