package com.example.be_web_admin.dto;

import com.example.be_web_admin.enums.LoginStatus;

public record LoginOutcome(LoginStatus status, String accessToken, String refreshToken, long retryAfterSeconds) {

    public static LoginOutcome of(LoginStatus status) {
        return new LoginOutcome(status, null, null, 0);
    }

    public static LoginOutcome locked(long retryAfterSeconds) {
        return new LoginOutcome(LoginStatus.LOCKED, null, null, retryAfterSeconds);
    }

    public static LoginOutcome ok(String accessToken, String refreshToken) {
        return new LoginOutcome(LoginStatus.OK, accessToken, refreshToken, 0);
    }
}
