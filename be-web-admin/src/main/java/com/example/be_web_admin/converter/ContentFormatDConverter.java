package com.example.be_web_admin.converter;

import com.example.be_web_admin.enums.qbank.ContentFormatD;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Chuyển ContentFormatD sang/ra giá trị ENUM thực tế trong CSDL (không phụ thuộc tên hằng số Java). */
@Converter
public class ContentFormatDConverter implements AttributeConverter<ContentFormatD, String> {

    @Override
    public String convertToDatabaseColumn(ContentFormatD attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public ContentFormatD convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ContentFormatD.fromDbValue(dbData);
    }
}
