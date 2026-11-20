package com.example.be_server_ai.model.jpa.converter;

import com.example.be_server_ai.enums.SuggestionSeverity;
import jakarta.persistence.Converter;

/** Gắn tường minh bằng @Convert trên thuộc tính, không autoApply. */
@Converter
public class SuggestionSeverityConverter extends ValueEnumConverter<SuggestionSeverity> {

    public SuggestionSeverityConverter() {
        super(SuggestionSeverity.class);
    }
}
