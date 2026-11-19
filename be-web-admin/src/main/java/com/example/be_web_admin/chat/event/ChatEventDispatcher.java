package com.example.be_web_admin.chat.event;

import com.example.be_web_admin.chat.ChatProperties;
import com.example.be_web_admin.chat.ws.ChatSessionRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gửi sự kiện chat ra WebSocket SAU KHI giao dịch commit (AFTER_COMMIT).
 *
 * Việc gửi chạy trên một executor riêng (không dùng luồng Tomcat) để một máy chậm không giữ request HTTP.
 * Mặc định 1 luồng để các sự kiện giữ đúng thứ tự commit (ví dụ message.new rồi message.deleted của cùng một tin).
 * Lỗi khi gửi chỉ ghi log: dữ liệu đã lưu, client tự sửa bằng kéo bù khi nối lại. Nếu hàng đợi đầy thì bỏ sự kiện
 * (cũng chỉ ghi log) thay vì chặn luồng ghi.
 *
 * Executor nằm trong chính lớp này (không khai báo bean Executor) để Spring Boot vẫn tự tạo applicationTaskExecutor như cũ.
 */
@Slf4j
@Component
public class ChatEventDispatcher {

    private final ChatSessionRegistry registry;
    private final ObjectMapper objectMapper;
    private final ThreadPoolExecutor executor;

    public ChatEventDispatcher(ChatSessionRegistry registry, ObjectMapper objectMapper, ChatProperties properties) {
        this.registry = registry;
        this.objectMapper = objectMapper;
        int threads = Math.max(1, properties.getEvent().getThreads());
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "chat-event-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(Math.max(1, properties.getEvent().getQueueCapacity())), factory);
    }

    // fallbackExecution = true: nếu ai đó phát sự kiện ngoài giao dịch thì vẫn gửi ngay thay vì âm thầm bỏ.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEvent(ChatDomainEvent event) {
        try {
            executor.execute(() -> dispatch(event));
        } catch (RejectedExecutionException e) {
            log.warn("Chat: hàng đợi sự kiện đầy, bỏ sự kiện {}", event.type());
        }
    }

    private void dispatch(ChatDomainEvent event) {
        try {
            String json = objectMapper.writeValueAsString(Map.of("type", event.type(), "data", event.data()));
            registry.sendToAdmins(event.recipients(), json);
        } catch (JsonProcessingException e) {
            log.error("Chat: không dựng được JSON cho sự kiện {}", event.type(), e);
        } catch (RuntimeException e) {
            log.warn("Chat: lỗi khi phát sự kiện {}: {}", event.type(), e.toString());
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
