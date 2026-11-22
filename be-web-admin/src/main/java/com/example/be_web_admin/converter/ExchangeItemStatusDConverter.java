package com.example.be_web_admin.converter;

import com.example.be_web_admin.enums.moodle.ExchangeItemStatusD;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Chuyển ExchangeItemStatusD sang/ra giá trị ENUM thực tế trong CSDL (không phụ thuộc tên hằng số Java). */
@Converter
public class ExchangeItemStatusDConverter implements AttributeConverter<ExchangeItemStatusD, String> {

    @Override
    public String convertToDatabaseColumn(ExchangeItemStatusD attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public ExchangeItemStatusD convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ExchangeItemStatusD.fromDbValue(dbData);
    }
}
