package com.example.be_server_ai.model.jpa.converter;

import com.example.be_server_ai.enums.AiTaskStatus;
import jakarta.persistence.Converter;

/** Gắn tường minh bằng @Convert trên thuộc tính, không autoApply. */
@Converter
public class AiTaskStatusConverter extends ValueEnumConverter<AiTaskStatus> {

    public AiTaskStatusConverter() {
        super(AiTaskStatus.class);
    }
}
