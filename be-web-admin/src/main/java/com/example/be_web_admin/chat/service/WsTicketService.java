package com.example.be_web_admin.chat.service;

import com.example.be_web_admin.security.AuthSessionStore;
import com.example.be_web_admin.chat.ChatProperties;
import com.example.be_web_admin.chat.dto.WsTicketDto;
import com.example.be_web_admin.chat.exception.ChatException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Vé một lần để mở WebSocket. Flutter web không đặt được header Authorization khi mở WebSocket, và đưa JWT lên URL
 * sẽ lọt vào log Nginx, nên client xin vé qua REST (có Bearer) rồi mở ws://...?ticket=VÉ.
 * Vé là 32 byte ngẫu nhiên (SecureRandom), lưu Redis 30 giây, lấy ra rồi xóa trong một bước nguyên tử nên chỉ dùng được một lần.
 */
@Slf4j
@Service
public class WsTicketService {

    private static final String KEY_PREFIX = "chat:ws-ticket:";
    private static final int TICKET_BYTES = 32;

    /**
     * Lấy giá trị và xóa khóa trong MỘT bước nguyên tử bằng Lua (chạy được trên mọi bản Redis từ 2.6). Cố ý không dùng
     * lệnh GETDEL: lệnh đó chỉ có từ Redis 6.2, trên bản cũ hơn (vd redis-server 6.0 của Ubuntu 22.04, hay Redis 5 trên
     * Windows) nó báo "unknown command" nên bước bắt tay WebSocket luôn bị 503 dù xin vé vẫn thành công.
     */
    private static final DefaultRedisScript<String> GET_AND_DELETE = new DefaultRedisScript<>(
            "local v = redis.call('GET', KEYS[1]) if v then redis.call('DEL', KEYS[1]) end return v",
            String.class);

    private final StringRedisTemplate redis;
    private final ChatProperties properties;
    private final ChatRateLimiter rateLimiter;
    private final SecureRandom random = new SecureRandom();

    public WsTicketService(StringRedisTemplate redis, ChatProperties properties, ChatRateLimiter rateLimiter) {
        this.redis = redis;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
    }

    public WsTicketDto issue(AuthSessionStore.Identity identity) {
        rateLimiter.check("ticket", identity.adminId(), properties.getRate().getTicketsPerMinute(), 60);

        byte[] bytes = new byte[TICKET_BYTES];
        random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        int ttl = properties.getWs().getTicketTtlSeconds();
        try {
            redis.opsForValue().set(KEY_PREFIX + ticket, identity.adminId() + "|" + identity.client().name() + "|" + identity.sessionId(), Duration.ofSeconds(ttl));
        } catch (RuntimeException e) {
            log.warn("Chat: Redis lỗi khi lưu vé WebSocket: {}", e.toString());
            throw ChatException.unavailable("REALTIME_UNAVAILABLE", "Kênh thời gian thực tạm thời không dùng được.");
        }
        return new WsTicketDto(ticket, ttl);
    }

    /**
     * Lấy vé và xóa trong một bước nguyên tử (script Lua). Trả rỗng nếu vé sai, đã dùng hoặc hết hạn.
     * Redis lỗi thì ném ChatException 503 để bước bắt tay trả đúng mã.
     */
    public Optional<AuthSessionStore.Identity> consume(String ticket) {
        if (ticket == null || ticket.isBlank() || ticket.length() > 128) {
            return Optional.empty();
        }
        try {
            String value = redis.execute(GET_AND_DELETE, List.of(KEY_PREFIX + ticket));
            if (value == null) {
                return Optional.empty();
            }
            String[] parts = value.split("\\|", -1);
            if (parts.length != 3 || !parts[2].matches("[0-9a-fA-F-]{36}")) return Optional.empty();
            return Optional.of(new AuthSessionStore.Identity(Long.parseLong(parts[0]), AuthSessionStore.Client.valueOf(parts[1]), parts[2]));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("Chat: Redis lỗi khi đọc vé WebSocket: {}", e.toString());
            throw ChatException.unavailable("REALTIME_UNAVAILABLE", "Kênh thời gian thực tạm thời không dùng được.");
        }
    }
}
