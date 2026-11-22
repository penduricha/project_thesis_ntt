package com.example.be_web_admin.converter;

import com.example.be_web_admin.enums.ai.TargetFieldD;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Chuyển TargetFieldD sang/ra giá trị ENUM thực tế trong CSDL (không phụ thuộc tên hằng số Java). */
@Converter
public class TargetFieldDConverter implements AttributeConverter<TargetFieldD, String> {

    @Override
    public String convertToDatabaseColumn(TargetFieldD attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public TargetFieldD convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TargetFieldD.fromDbValue(dbData);
    }
}
