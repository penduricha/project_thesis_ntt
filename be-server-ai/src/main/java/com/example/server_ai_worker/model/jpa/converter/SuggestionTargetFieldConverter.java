package com.example.server_ai_worker.model.jpa.converter;

import com.example.server_ai_worker.enums.SuggestionTargetField;
import jakarta.persistence.Converter;

@Converter
public class SuggestionTargetFieldConverter extends ValueEnumConverter<SuggestionTargetField> {
    public SuggestionTargetFieldConverter() {
        super(SuggestionTargetField.class);
    }
}
