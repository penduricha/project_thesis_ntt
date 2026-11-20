package com.example.server_ai_worker.model.jpa.converter;

import com.example.server_ai_worker.enums.AiTaskStatus;
import jakarta.persistence.Converter;

@Converter
public class AiTaskStatusConverter extends ValueEnumConverter<AiTaskStatus> {
    public AiTaskStatusConverter() {
        super(AiTaskStatus.class);
    }
}
