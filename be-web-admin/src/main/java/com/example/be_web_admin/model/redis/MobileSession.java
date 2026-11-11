package com.example.be_web_admin.model.redis;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Phiên ngắn hạn của app Authenticator (sau khi app ký challenge bằng khoá thiết bị).
 * Key: msess:{jti}, TTL 1 giờ. lastSeenAt ghi liên tục ở đây, định kỳ ghi xuống ntt_mobile_devices.last_seen_at.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@RedisHash(value = "mobile_sessions", timeToLive = 3600)
public class MobileSession implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String jti;

    @Indexed
    private Long deviceId;

    private Long adminId;

    private Instant lastSeenAt;
}
