package com.example.be_web_admin.converter;

import com.example.be_web_admin.enums.ai.TaskStatusD;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Chuyển TaskStatusD sang/ra giá trị ENUM thực tế trong CSDL (không phụ thuộc tên hằng số Java). */
@Converter
public class TaskStatusDConverter implements AttributeConverter<TaskStatusD, String> {

    @Override
    public String convertToDatabaseColumn(TaskStatusD attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public TaskStatusD convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TaskStatusD.fromDbValue(dbData);
    }
}
