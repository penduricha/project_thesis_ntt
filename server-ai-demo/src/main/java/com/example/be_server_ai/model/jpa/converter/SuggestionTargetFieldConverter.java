package com.example.be_server_ai.model.jpa.converter;

import com.example.be_server_ai.enums.SuggestionTargetField;
import jakarta.persistence.Converter;

/** Gắn tường minh bằng @Convert trên thuộc tính, không autoApply. */
@Converter
public class SuggestionTargetFieldConverter extends ValueEnumConverter<SuggestionTargetField> {

    public SuggestionTargetFieldConverter() {
        super(SuggestionTargetField.class);
    }
}
