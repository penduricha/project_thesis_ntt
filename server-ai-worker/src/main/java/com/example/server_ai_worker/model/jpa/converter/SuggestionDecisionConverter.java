package com.example.server_ai_worker.model.jpa.converter;

import com.example.server_ai_worker.enums.SuggestionDecision;
import jakarta.persistence.Converter;

@Converter
public class SuggestionDecisionConverter extends ValueEnumConverter<SuggestionDecision> {
    public SuggestionDecisionConverter() {
        super(SuggestionDecision.class);
    }
}
