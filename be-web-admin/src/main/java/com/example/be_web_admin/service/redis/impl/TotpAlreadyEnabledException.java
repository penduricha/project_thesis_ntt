package com.example.be_web_admin.service.redis.impl;

/** Admin đã bật 2FA xong rồi: không cho sinh khóa / kích hoạt lại (controller trả 409). */
public class TotpAlreadyEnabledException extends RuntimeException {
    public TotpAlreadyEnabledException() {
        super("2FA is already enabled.");
    }
}
