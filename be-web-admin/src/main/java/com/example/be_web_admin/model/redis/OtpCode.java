package com.example.be_web_admin.model.redis;

import com.example.be_web_admin.enums.OtpPurpose;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

import java.io.Serial;
import java.io.Serializable;

/** OTP 6 số (đăng ký / quên mật khẩu) gửi qua kênh chat. Key: otp:{purpose}:{adminId}, TTL 5 phút. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@RedisHash(value = "otp_codes", timeToLive = 300)
public class OtpCode implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // dạng "REGISTER:123456", mỗi (mục đích, adminId) chỉ có 1 OTP còn hiệu lực.
    @Id
    private String id;

    @Indexed
    private Long adminId;

    private OtpPurpose purpose;

    // SHA-256 của mã 6 số, không lưu mã gốc.
    private String codeHash;

    // Số lần nhập sai.
    private Integer attempts = 0;

    public static String buildId(OtpPurpose purpose, Long adminId) {
        return purpose.name() + ":" + adminId;
    }
}
