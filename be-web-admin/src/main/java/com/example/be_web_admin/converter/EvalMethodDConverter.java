package com.example.be_web_admin.converter;

import com.example.be_web_admin.enums.eval.EvalMethodD;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Chuyển EvalMethodD sang/ra giá trị ENUM thực tế trong CSDL (không phụ thuộc tên hằng số Java). */
@Converter
public class EvalMethodDConverter implements AttributeConverter<EvalMethodD, String> {

    @Override
    public String convertToDatabaseColumn(EvalMethodD attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public EvalMethodD convertToEntityAttribute(String dbData) {
        return dbData == null ? null : EvalMethodD.fromDbValue(dbData);
    }
}
