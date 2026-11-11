package com.example.be_web_admin.enums;

public enum AccountStatus {
    PENDING,   // mới đăng ký, chờ nhập OTP 6 số
    ACTIVE,    // đang hoạt động
    LOCKED,    // bị khoá tạm do nhập sai nhiều lần
    DISABLED   // bị vô hiệu hoá
}
