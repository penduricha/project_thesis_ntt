package com.example.be_web_admin.converter;

import com.example.be_web_admin.enums.eval.EvalOriginD;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Chuyển EvalOriginD sang/ra giá trị ENUM thực tế trong CSDL (không phụ thuộc tên hằng số Java). */
@Converter
public class EvalOriginDConverter implements AttributeConverter<EvalOriginD, String> {

    @Override
    public String convertToDatabaseColumn(EvalOriginD attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public EvalOriginD convertToEntityAttribute(String dbData) {
        return dbData == null ? null : EvalOriginD.fromDbValue(dbData);
    }
}
