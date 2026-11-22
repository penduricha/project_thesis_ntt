package com.example.be_web_admin.converter;

import com.example.be_web_admin.enums.eval.EvalStageD;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Chuyển EvalStageD sang/ra giá trị ENUM thực tế trong CSDL (không phụ thuộc tên hằng số Java). */
@Converter
public class EvalStageDConverter implements AttributeConverter<EvalStageD, String> {

    @Override
    public String convertToDatabaseColumn(EvalStageD attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public EvalStageD convertToEntityAttribute(String dbData) {
        return dbData == null ? null : EvalStageD.fromDbValue(dbData);
    }
}
