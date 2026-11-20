package com.example.be_server_ai.model.jpa.converter;

import com.example.be_server_ai.enums.ValueEnum;
import jakarta.persistence.AttributeConverter;

/**
 * Chuyển enum Java sang chuỗi chữ thường của cột ENUM và ngược lại.
 * Lớp con chỉ cần gọi super(TênEnum.class) và gắn @Converter.
 */
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
