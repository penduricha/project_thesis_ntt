package com.example.be_web_admin.enums;

public enum AuthEvent {
    REGISTER_OK,
    LOGIN_OK,
    LOGIN_FAIL_PASSWORD,
    LOGIN_FAIL_TOTP,
    LOGOUT,
    REFRESH_TOKEN,
    ACCOUNT_LOCKED,
    DEVICE_BOUND,
    DEVICE_REVOKED,
    PASSWORD_RESET,
    BACKUP_CODE_USED
}
