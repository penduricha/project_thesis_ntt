package com.example.server_ai_worker.model.jpa.converter;

import com.example.server_ai_worker.enums.AiTaskType;
import jakarta.persistence.Converter;

@Converter
public class AiTaskTypeConverter extends ValueEnumConverter<AiTaskType> {
    public AiTaskTypeConverter() {
        super(AiTaskType.class);
    }
}
