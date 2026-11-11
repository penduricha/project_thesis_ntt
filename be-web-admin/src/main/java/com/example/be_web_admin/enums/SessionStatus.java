package com.example.be_web_admin.enums;

public enum SessionStatus {
    ACTIVE,      // phiên đang hoạt động
    LOGGED_OUT,  // người dùng chủ động đăng xuất
    EXPIRED,     // hết hạn refresh token
    REVOKED      // bị thu hồi (đăng xuất mọi nơi, phát hiện dùng lại refresh token...)
}
