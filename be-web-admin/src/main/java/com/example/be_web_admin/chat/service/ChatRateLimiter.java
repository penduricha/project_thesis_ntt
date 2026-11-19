package com.example.be_web_admin.chat.service;

import com.example.be_web_admin.chat.exception.ChatException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Giới hạn tốc độ kiểu cửa sổ cố định, đếm bằng Redis theo (hành động, adminId).
 * INCR rồi đặt hạn ở lần đếm đầu tiên; vượt mức thì ném 429 kèm số giây còn lại của cửa sổ (Retry-After).
 *
 * Redis lỗi thì cho qua (fail-open) và ghi cảnh báo: chat vẫn dùng được khi Redis ngừng (mục 14 của thiết kế).
 * Không gọi trong giao dịch CSDL, để một lần Redis chậm không giữ kết nối Hikari.
 */
@Slf4j
@Component
public class ChatRateLimiter {

    private static final String KEY_PREFIX = "chat:rate:";

    private final StringRedisTemplate redis;

    public ChatRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * @param action        tên hành động, ví dụ "send", "ticket", "search", "new"
     * @param adminId       người thực hiện
     * @param limit         số lần tối đa trong một cửa sổ
     * @param windowSeconds độ dài cửa sổ (giây)
     */
    public void check(String action, Long adminId, int limit, int windowSeconds) {
        String key = KEY_PREFIX + action + ":" + adminId;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count == null) {
                return;
            }
            if (count == 1L) {
                redis.expire(key, Duration.ofSeconds(windowSeconds));
            }
            if (count > limit) {
                Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
                if (ttl == null || ttl < 0) {
                    // Khóa mất hạn (lần expire đầu thất bại): đặt lại để không bị chặn vĩnh viễn.
                    redis.expire(key, Duration.ofSeconds(windowSeconds));
                    ttl = (long) windowSeconds;
                }
                throw ChatException.tooManyRequests("Bạn thao tác quá nhanh, vui lòng thử lại sau ít giây.", Math.max(1L, ttl));
            }
        } catch (ChatException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Chat: Redis lỗi khi kiểm tra giới hạn tốc độ '{}', cho qua: {}", action, e.toString());
        }
    }
}
