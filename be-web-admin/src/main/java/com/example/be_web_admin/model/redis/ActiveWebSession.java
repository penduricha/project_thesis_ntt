package com.example.be_web_admin.model.redis;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Phiên Web đang hoạt động. Key: wsess:{sid}, TTL 30 ngày (= refresh token).
 * Tương ứng 1-1 với model.jpa.WebSession (cùng sid = sessionId).
 * Đặt tên ActiveWebSession để không trùng tên với entity JPA.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@RedisHash(value = "active_web_sessions", timeToLive = 2_592_000)
public class ActiveWebSession implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String sid;

    // Có index để "đăng xuất mọi nơi": findByAdminId(...) rồi xoá hết.
    @Indexed
    private Long adminId;

    private Long deviceId;

    // SHA-256 của refresh token.
    private String refreshHash;

    private String ip;

    private String userAgent;

    private Instant createdAt;
}
