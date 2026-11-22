package com.example.be_web_admin.converter;

import com.example.be_web_admin.enums.qbank.OptionLabelD;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Chuyển OptionLabelD sang/ra giá trị ENUM thực tế trong CSDL (không phụ thuộc tên hằng số Java). */
@Converter
public class OptionLabelDConverter implements AttributeConverter<OptionLabelD, String> {

    @Override
    public String convertToDatabaseColumn(OptionLabelD attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public OptionLabelD convertToEntityAttribute(String dbData) {
        return dbData == null ? null : OptionLabelD.fromDbValue(dbData);
    }
}
