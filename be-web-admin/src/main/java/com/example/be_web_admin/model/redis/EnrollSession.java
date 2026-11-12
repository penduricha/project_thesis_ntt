package com.example.be_web_admin.model.redis;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Phiên cài đặt 2FA đang chờ xác nhận (khóa Base32 vừa sinh, CHƯA ghi vào MariaDB).
 * Key: enroll_session:{adminId}, TTL 5 phút, Redis tự xóa khi hết hạn.
 *
 * Dùng adminId làm @Id nên mỗi admin chỉ có tối đa 1 phiên chờ: mở modal lần hai thì
 * khóa mới tự ghi đè khóa cũ, không cần dọn.
 *
 * Sau khi admin bấm "Kích hoạt" thì ghi sang ntt_totp_credentials rồi xóa key này.
 * Bộ đếm nhập sai mã xác nhận nằm ở key riêng enroll_confirm_attempts:{adminId} (đếm nguyên tử bằng INCR).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@RedisHash(value = "enroll_session", timeToLive = EnrollSession.TTL_SECONDS)
public class EnrollSession implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final int TTL_SECONDS = 300;

    @Id
    private Long adminId;

    // CryptoService.Sealed.toBase64(): 1 chuỗi Base64 gồm salt | iv | ciphertext+tag. KHÔNG lưu khóa gốc.
    private String secretCipher;

    // Phiên bản master key đã dùng để mã hóa. Giữa lúc mở modal và bấm "Kích hoạt" có thể xoay khóa.
    private Integer keyVersion;

    // Chu kỳ đổi mã (giây) admin chọn trong modal: 15 / 30 / 45 / 60.
    private Integer periodSeconds;

    private Instant createdAt;
}