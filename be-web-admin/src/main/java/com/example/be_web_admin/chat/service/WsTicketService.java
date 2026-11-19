package com.example.be_web_admin.chat.service;

import com.example.be_web_admin.chat.ChatProperties;
import com.example.be_web_admin.chat.dto.WsTicketDto;
import com.example.be_web_admin.chat.exception.ChatException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Vé một lần để mở WebSocket. Flutter web không đặt được header Authorization khi mở WebSocket, và đưa JWT lên URL
 * sẽ lọt vào log Nginx, nên client xin vé qua REST (có Bearer) rồi mở ws://...?ticket=VÉ.
 * Vé là 32 byte ngẫu nhiên (SecureRandom), lưu Redis 30 giây, lấy ra bằng GETDEL nên chỉ dùng được một lần.
 */
@Slf4j
@Service
public class WsTicketService {

    private static final String KEY_PREFIX = "chat:ws-ticket:";
    private static final int TICKET_BYTES = 32;

    private final StringRedisTemplate redis;
    private final ChatProperties properties;
    private final ChatRateLimiter rateLimiter;
    private final SecureRandom random = new SecureRandom();

    public WsTicketService(StringRedisTemplate redis, ChatProperties properties, ChatRateLimiter rateLimiter) {
        this.redis = redis;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
    }

    public WsTicketDto issue(Long adminId) {
        rateLimiter.check("ticket", adminId, properties.getRate().getTicketsPerMinute(), 60);

        byte[] bytes = new byte[TICKET_BYTES];
        random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        int ttl = properties.getWs().getTicketTtlSeconds();
        try {
            redis.opsForValue().set(KEY_PREFIX + ticket, String.valueOf(adminId), Duration.ofSeconds(ttl));
        } catch (RuntimeException e) {
            log.warn("Chat: Redis lỗi khi lưu vé WebSocket: {}", e.toString());
            throw ChatException.unavailable("REALTIME_UNAVAILABLE", "Kênh thời gian thực tạm thời không dùng được.");
        }
        return new WsTicketDto(ticket, ttl);
    }

    /**
     * Lấy vé và xóa trong một lệnh (GETDEL). Trả rỗng nếu vé sai, đã dùng hoặc hết hạn.
     * Redis lỗi thì ném ChatException 503 để bước bắt tay trả đúng mã.
     */
    public Optional<Long> consume(String ticket) {
        if (ticket == null || ticket.isBlank() || ticket.length() > 128) {
            return Optional.empty();
        }
        try {
            String value = redis.opsForValue().getAndDelete(KEY_PREFIX + ticket);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("Chat: Redis lỗi khi đọc vé WebSocket: {}", e.toString());
            throw ChatException.unavailable("REALTIME_UNAVAILABLE", "Kênh thời gian thực tạm thời không dùng được.");
        }
    }
}
