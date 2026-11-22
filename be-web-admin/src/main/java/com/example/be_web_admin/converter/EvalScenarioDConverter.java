package com.example.be_web_admin.converter;

import com.example.be_web_admin.enums.eval.EvalScenarioD;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Chuyển EvalScenarioD sang/ra giá trị ENUM thực tế trong CSDL (không phụ thuộc tên hằng số Java). */
@Converter
public class EvalScenarioDConverter implements AttributeConverter<EvalScenarioD, String> {

    @Override
    public String convertToDatabaseColumn(EvalScenarioD attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public EvalScenarioD convertToEntityAttribute(String dbData) {
        return dbData == null ? null : EvalScenarioD.fromDbValue(dbData);
    }
}
