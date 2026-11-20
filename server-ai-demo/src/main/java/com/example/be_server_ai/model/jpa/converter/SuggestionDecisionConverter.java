package com.example.be_server_ai.model.jpa.converter;

import com.example.be_server_ai.enums.SuggestionDecision;
import jakarta.persistence.Converter;

/** Gắn tường minh bằng @Convert trên thuộc tính, không autoApply. */
@Converter
public class SuggestionDecisionConverter extends ValueEnumConverter<SuggestionDecision> {

    public SuggestionDecisionConverter() {
        super(SuggestionDecision.class);
    }
}
