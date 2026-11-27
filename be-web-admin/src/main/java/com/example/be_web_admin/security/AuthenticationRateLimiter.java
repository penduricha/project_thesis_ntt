package com.example.be_web_admin.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;

/** Reserves an attempt before password/TOTP verification; Redis failures deny authentication. */
@Component
public class AuthenticationRateLimiter {
    private static final DefaultRedisScript<Long> RESERVE = new DefaultRedisScript<>(
            "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[2]) end " +
            "if n>tonumber(ARGV[1]) then local ttl=redis.call('TTL',KEYS[1]); " +
            "if ttl<1 then redis.call('EXPIRE',KEYS[1],ARGV[2]); ttl=tonumber(ARGV[2]) end return ttl end return 0", Long.class);
    private final StringRedisTemplate redis;
    public AuthenticationRateLimiter(StringRedisTemplate redis) { this.redis = redis; }

    /** @return zero if admitted; otherwise seconds until retry. Counts successes too, preventing races on reset. */
    public long reserve(String scope, Long adminId, int limit, Duration window) {
        return reserve(scope, String.valueOf(adminId), limit, window);
    }

    public long reserve(String scope, String subject, int limit, Duration window) {
        try {
            Long ttl = redis.execute(RESERVE, List.of("auth:limit:" + scope + ":" + subject),
                    String.valueOf(limit), String.valueOf(window.toSeconds()));
            if (ttl == null) throw new IllegalStateException("Rate limiter returned no result");
            return ttl;
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Authentication temporarily unavailable", e);
        }
    }
}
