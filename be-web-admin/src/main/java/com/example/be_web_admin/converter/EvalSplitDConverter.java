package com.example.be_web_admin.converter;

import com.example.be_web_admin.enums.eval.EvalSplitD;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Chuyển EvalSplitD sang/ra giá trị ENUM thực tế trong CSDL (không phụ thuộc tên hằng số Java). */
@Converter
public class EvalSplitDConverter implements AttributeConverter<EvalSplitD, String> {

    @Override
    public String convertToDatabaseColumn(EvalSplitD attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public EvalSplitD convertToEntityAttribute(String dbData) {
        return dbData == null ? null : EvalSplitD.fromDbValue(dbData);
    }
}
