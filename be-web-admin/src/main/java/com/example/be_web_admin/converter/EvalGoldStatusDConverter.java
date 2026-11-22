package com.example.be_web_admin.converter;

import com.example.be_web_admin.enums.eval.EvalGoldStatusD;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Chuyển EvalGoldStatusD sang/ra giá trị ENUM thực tế trong CSDL (không phụ thuộc tên hằng số Java). */
@Converter
public class EvalGoldStatusDConverter implements AttributeConverter<EvalGoldStatusD, String> {

    @Override
    public String convertToDatabaseColumn(EvalGoldStatusD attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public EvalGoldStatusD convertToEntityAttribute(String dbData) {
        return dbData == null ? null : EvalGoldStatusD.fromDbValue(dbData);
    }
}
