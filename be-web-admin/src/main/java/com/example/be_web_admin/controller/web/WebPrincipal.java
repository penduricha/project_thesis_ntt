package com.example.be_web_admin.controller.web;

import com.example.be_web_admin.exception.qbank.QbankException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

/**
 * Lấy adminId từ access token đã được JwtAuthenticationFilter xác thực.
 * Không bao giờ tin adminId do client gửi trong body/query.
 */
public final class WebPrincipal {

    private WebPrincipal() {
    }

    public static Long adminId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Long id) {
            return id;
        }
        throw new QbankException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Thiếu hoặc sai access token.");
    }
}
