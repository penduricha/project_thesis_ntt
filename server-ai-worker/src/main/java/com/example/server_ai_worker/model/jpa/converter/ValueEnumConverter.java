package com.example.server_ai_worker.model.jpa.converter;

import com.example.server_ai_worker.enums.ValueEnum;
import jakarta.persistence.AttributeConverter;

public abstract class ValueEnumConverter<E extends Enum<E> & ValueEnum> implements AttributeConverter<E, String> {

    private final Class<E> type;

    protected ValueEnumConverter(Class<E> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public E convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        for (E constant : type.getEnumConstants()) {
            if (constant.getValue().equals(dbData)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("Giá trị không hợp lệ cho " + type.getSimpleName() + ": " + dbData);
    }
}
