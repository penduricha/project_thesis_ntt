package com.example.be_web_admin.converter;

import com.example.be_web_admin.enums.ai.SuggestionDecisionD;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Chuyển SuggestionDecisionD sang/ra giá trị ENUM thực tế trong CSDL (không phụ thuộc tên hằng số Java). */
@Converter
public class SuggestionDecisionDConverter implements AttributeConverter<SuggestionDecisionD, String> {

    @Override
    public String convertToDatabaseColumn(SuggestionDecisionD attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public SuggestionDecisionD convertToEntityAttribute(String dbData) {
        return dbData == null ? null : SuggestionDecisionD.fromDbValue(dbData);
    }
}
