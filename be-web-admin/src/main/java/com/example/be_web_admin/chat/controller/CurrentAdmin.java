package com.example.be_web_admin.chat.controller;

import com.example.be_web_admin.chat.exception.ChatException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

/** Lấy adminId từ token đã được ChatJwtFilter xác thực. Không bao giờ tin adminId do client gửi lên. */
final class CurrentAdmin {

    private CurrentAdmin() {
    }

    static Long id(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Long adminId) {
            return adminId;
        }
        throw new ChatException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Thiếu hoặc sai access token.");
    }
}
