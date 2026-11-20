package com.example.be_server_ai.enums;

/**
 * Enum Java có giá trị lưu DB khác tên hằng (cột ENUM của MariaDB dùng chữ thường).
 * com.example.be_server_ai.model.jpa.converter.ValueEnumConverter đọc giá trị này.
 */
public interface ValueEnum {

    String getValue();
}
