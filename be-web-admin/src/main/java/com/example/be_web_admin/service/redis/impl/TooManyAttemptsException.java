package com.example.be_web_admin.service.redis.impl;

/** Nhập sai mã quá số lần cho phép (controller trả 429). */
public class TooManyAttemptsException extends RuntimeException {
    public TooManyAttemptsException() {
        super("Too many failed attempts.");
    }
}
