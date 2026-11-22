package com.example.be_web_admin.converter;

import com.example.be_web_admin.enums.moodle.MoodleCheckD;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Chuyển MoodleCheckD sang/ra giá trị ENUM thực tế trong CSDL (không phụ thuộc tên hằng số Java). */
@Converter
public class MoodleCheckDConverter implements AttributeConverter<MoodleCheckD, String> {

    @Override
    public String convertToDatabaseColumn(MoodleCheckD attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public MoodleCheckD convertToEntityAttribute(String dbData) {
        return dbData == null ? null : MoodleCheckD.fromDbValue(dbData);
    }
}
