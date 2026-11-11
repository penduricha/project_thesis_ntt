package com.example.be_web_admin.model.redis;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.io.Serial;
import java.io.Serializable;

/**
 * Access token đã bị vô hiệu khi đăng xuất. Key: bl:{jti}.
 * TTL = thời gian còn lại của access token (tối đa 300 giây), nên đặt động bằng @TimeToLive.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@RedisHash("black_listed_tokens")
public class BlackListedToken implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // jti của access token.
    @Id
    private String jti;

    private String sid;

    // Giây còn lại = exp - now.
    @TimeToLive
    private Long ttl;
}
