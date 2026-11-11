package com.example.be_web_admin.model.redis;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.io.Serial;
import java.io.Serializable;

/**
 * Phiên ghép thiết bị qua QR, dùng một lần. Key: enroll:{id}, TTL 5 phút.
 * Sau khi app gửi đúng mã TOTP đầu tiên thì ghi vào ntt_totp_credentials rồi xoá key này.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@RedisHash(value = "enroll_session", timeToLive = 300)
public class EnrollSession implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    private Long adminId;

    // Secret đã mã hoá AES-256-GCM (cùng định dạng với TotpCredential).
    private byte[] secretCipher;

    private byte[] iv;

    private byte[] kdfSalt;
}
