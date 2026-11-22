package com.example.be_web_admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    @NotNull(message = "Thiếu mã quản trị viên.")
    private Long adminId;

    @ToString.Exclude
    @NotNull(message = "Mật khẩu không được để trống.")
    @Size(max = 100, message = "Mật khẩu quá dài.")
    private String password;

    /**
     * Mã 6 số từ app Authenticator.
     * null ở lần POST đầu; chỉ có khi tài khoản đã bật 2FA và server đã yêu cầu (TOTP_REQUIRED).
     */
    @ToString.Exclude
    // Cho phép null/chuỗi rỗng (tài khoản chưa bật 2FA) nhưng nếu có thì phải đúng 6 chữ số.
    @Pattern(regexp = "^$|^\\d{6}$", message = "Mã xác thực phải gồm 6 chữ số.")
    private String totpCode;

    /** Giữ constructor 2 tham số cũ để code đang gọi new AdminLoginDTO(adminId, password) không bị lỗi. */
    public AdminLoginDTO(Long adminId, String password) {
        this.adminId = adminId;
        this.password = password;
    }
}
