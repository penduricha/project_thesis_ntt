package com.example.be_web_admin.chat.ws;

import com.example.be_web_admin.chat.ChatProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Định kỳ đóng kết nối không thấy khung nào quá chat.ws.idle-timeout-seconds (mặc định 50 giây, nhỏ hơn mốc 60 giây
 * của Nginx). Tự có một luồng định kỳ riêng, KHÔNG dùng @EnableScheduling, để không vô tình bật các tác vụ @Scheduled
 * khác của dự án.
 */
@Slf4j
@Component
public class ChatIdleSweeper {

    private final ChatSessionRegistry registry;
    private final ChatProperties properties;
    private ScheduledExecutorService scheduler;

    public ChatIdleSweeper(ChatSessionRegistry registry, ChatProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @PostConstruct
    void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "chat-idle-sweeper");
            thread.setDaemon(true);
            return thread;
        });
        long interval = Math.max(1_000L, properties.getWs().getSweepIntervalMs());
        scheduler.scheduleWithFixedDelay(this::sweep, interval, interval, TimeUnit.MILLISECONDS);
    }

    void sweep() {
        try {
            long limitMs = properties.getWs().getIdleTimeoutSeconds() * 1_000L;
            for (ChatConnection connection : registry.snapshot()) {
                if (!registry.enforceSession(connection)) continue;
                if (connection.idleMillis() > limitMs) {
                    log.debug("Chat: đóng kết nối im lặng của adminId={}", connection.adminId());
                    registry.unregister(connection);
                    connection.close(ChatSessionRegistry.IDLE_TIMEOUT);
                }
            }
        } catch (RuntimeException e) {
            // Luồng định kỳ mà ném lỗi ra ngoài sẽ ngừng chạy vĩnh viễn: luôn nuốt và ghi log.
            log.warn("Chat: lỗi khi dọn kết nối: {}", e.toString());
        }
    }

    @PreDestroy
    void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }
}
