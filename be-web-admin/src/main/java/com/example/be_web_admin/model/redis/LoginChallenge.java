package com.example.be_web_admin.model.redis;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/** Trạng thái "đã qua bước mật khẩu, chờ TOTP". Key: chal:{challengeId}, TTL 3 phút. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@RedisHash(value = "chal", timeToLive = 180)
public class LoginChallenge implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // UUID trả cho trình duyệt (chưa phải JWT).
    @Id
    private String challengeId;

    @Indexed
    private Long adminId;

    private String ip;

    private Integer attempts = 0;

    private Instant createdAt;
}
