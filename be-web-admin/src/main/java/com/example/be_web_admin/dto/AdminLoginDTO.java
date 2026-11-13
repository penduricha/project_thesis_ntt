package com.example.be_web_admin.dto;

import lombok.*;

import java.io.Serial;
import java.io.Serializable;

@Setter
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AdminLoginDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long adminId;

    @ToString.Exclude
    private String password;

    /**
     * Mã 6 số từ app Authenticator.
     * null ở lần POST đầu; chỉ có khi tài khoản đã bật 2FA và server đã yêu cầu (TOTP_REQUIRED).
     */
    @ToString.Exclude
    private String totpCode;

    /** Giữ constructor 2 tham số cũ để code đang gọi new AdminLoginDTO(adminId, password) không bị lỗi. */
    public AdminLoginDTO(Long adminId, String password) {
        this.adminId = adminId;
        this.password = password;
    }
}
