package com.example.be_web_admin.converter;

import com.example.be_web_admin.enums.ai.TaskTypeD;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Chuyển TaskTypeD sang/ra giá trị ENUM thực tế trong CSDL (không phụ thuộc tên hằng số Java). */
@Converter
public class TaskTypeDConverter implements AttributeConverter<TaskTypeD, String> {

    @Override
    public String convertToDatabaseColumn(TaskTypeD attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public TaskTypeD convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TaskTypeD.fromDbValue(dbData);
    }
}
