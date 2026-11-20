package com.example.be_server_ai.model.jpa.converter;

import com.example.be_server_ai.enums.AiTaskType;
import jakarta.persistence.Converter;

/** Gắn tường minh bằng @Convert trên thuộc tính, không autoApply. */
@Converter
public class AiTaskTypeConverter extends ValueEnumConverter<AiTaskType> {

    public AiTaskTypeConverter() {
        super(AiTaskType.class);
    }
}
