package com.example.be_web_admin.enums;

public enum DeviceStatus {
    PENDING,  // đã đăng ký thiết bị, chưa xác nhận mã TOTP đầu tiên
    ACTIVE,   // đang dùng để sinh mã TOTP
    REVOKED   // đã bị thu hồi
}
