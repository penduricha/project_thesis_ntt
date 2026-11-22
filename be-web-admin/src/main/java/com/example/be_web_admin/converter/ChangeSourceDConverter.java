package com.example.be_web_admin.converter;

import com.example.be_web_admin.enums.qbank.ChangeSourceD;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Chuyển ChangeSourceD sang/ra giá trị ENUM thực tế trong CSDL (không phụ thuộc tên hằng số Java). */
@Converter
public class ChangeSourceDConverter implements AttributeConverter<ChangeSourceD, String> {

    @Override
    public String convertToDatabaseColumn(ChangeSourceD attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public ChangeSourceD convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ChangeSourceD.fromDbValue(dbData);
    }
}
