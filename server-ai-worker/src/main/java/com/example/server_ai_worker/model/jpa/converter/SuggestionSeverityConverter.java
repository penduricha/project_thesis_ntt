package com.example.server_ai_worker.model.jpa.converter;

import com.example.server_ai_worker.enums.SuggestionSeverity;
import jakarta.persistence.Converter;

@Converter
public class SuggestionSeverityConverter extends ValueEnumConverter<SuggestionSeverity> {
    public SuggestionSeverityConverter() {
        super(SuggestionSeverity.class);
    }
}
