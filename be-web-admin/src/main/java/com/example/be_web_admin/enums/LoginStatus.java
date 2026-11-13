package com.example.be_web_admin.enums;

public enum LoginStatus {
    OK,
    BAD_CREDENTIALS,
    TOTP_REQUIRED,
    TOTP_INVALID,
    LOCKED
}