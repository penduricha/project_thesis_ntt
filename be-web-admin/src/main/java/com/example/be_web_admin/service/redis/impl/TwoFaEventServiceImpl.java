package com.example.be_web_admin.service.redis.impl;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Đẩy sự kiện đổi trạng thái 2FA xuống trình duyệt qua SSE (Server-Sent Events), một chiều server -> web.
 * Web nhận sự kiện thì tự gọi lại GET /2fa/status, nên server vẫn là nguồn sự thật duy nhất.
 *
 * Registry nằm trong bộ nhớ của MỘT instance Spring Boot. Nếu sau này chạy nhiều instance sau load balancer,
 * cần fan-out qua Redis Pub/Sub (instance nào cũng nhận sự kiện rồi tự đẩy cho các kết nối của mình).
 */
@Slf4j
@Service
public class TwoFaEventServiceImpl {

    /** Trạng thái đẩy xuống web. */
    public static final String PENDING = "PENDING";
    public static final String ENABLED = "ENABLED";
    public static final String NOT_ENABLED = "NOT_ENABLED";

    private static final String EVENT_NAME = "2fa-status";
    private static final long STREAM_TIMEOUT_MS = Duration.ofMinutes(30).toMillis(); // hết hạn thì web tự nối lại
    private static final int MAX_STREAMS_PER_ADMIN = 5; // nhiều tab cùng lúc; vượt quá thì đóng kết nối cũ nhất
    private static final int HEARTBEAT_SECONDS = 25;    // giữ kết nối qua proxy / nginx (thường cắt sau ~60s im lặng)

    private final Map<Long, List<SseEmitter>> streams = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "2fa-sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    @PostConstruct
    void startHeartbeat() {
        heartbeat.scheduleAtFixedRate(this::sendHeartbeat, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    void stopHeartbeat() {
        heartbeat.shutdownNow();
    }

    /** Mở một kết nối SSE cho admin (mỗi tab một kết nối). */
    public SseEmitter subscribe(Long adminId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        emitter.onCompletion(() -> remove(adminId, emitter));
        emitter.onTimeout(() -> {
            remove(adminId, emitter);
            emitter.complete();
        });
        emitter.onError(e -> remove(adminId, emitter));

        streams.compute(adminId, (id, list) -> {
            List<SseEmitter> current = list == null ? new CopyOnWriteArrayList<>() : list;
            while (current.size() >= MAX_STREAMS_PER_ADMIN) {
                SseEmitter oldest = current.remove(0);
                try {
                    oldest.complete();
                } catch (Exception ignored) {
                    // kết nối cũ đã đóng
                }
            }
            current.add(emitter);
            return current;
        });

        try {
            // retry: trình duyệt chờ 3s rồi mới nối lại nếu đứt
            emitter.send(SseEmitter.event().name("ready").data("ok").reconnectTime(3000));
        } catch (IOException | IllegalStateException e) {
            remove(adminId, emitter);
        }
        return emitter;
    }

    /**
     * Báo cho mọi tab của admin. GỌI SAU KHI TRANSACTION ĐÃ COMMIT.
     * Lỗi đẩy tin không bao giờ làm hỏng nghiệp vụ chính: web vẫn còn polling dự phòng.
     */
    public void publish(Long adminId, String status) {
        List<SseEmitter> list = streams.get(adminId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                        .name(EVENT_NAME)
                        .data(Map.of("status", status), MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                remove(adminId, emitter);
            }
        }
    }

    private void sendHeartbeat() {
        streams.forEach((adminId, list) -> {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().comment("hb"));
                } catch (Exception e) {
                    remove(adminId, emitter);
                }
            }
        });
    }

    private void remove(Long adminId, SseEmitter emitter) {
        streams.computeIfPresent(adminId, (id, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }
}
