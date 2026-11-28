package com.example.be_web_admin.service.redis.impl;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
import java.util.function.BooleanSupplier;

/** Local-only, best-effort notifications; consumers must reload authoritative state. */
@Service
public class TwoFaEventServiceImpl {
    public static final String PENDING = "PENDING";
    public static final String ENABLED = "ENABLED";
    public static final String NOT_ENABLED = "NOT_ENABLED";
    private static final String EVENT_NAME = "2fa-status";
    private static final long STREAM_TIMEOUT_MS = Duration.ofMinutes(30).toMillis();
    private static final int MAX_STREAMS_PER_ADMIN = 5;
    private static final int HEARTBEAT_SECONDS = 25;

    // Validator is scoped to the original access token/session. Never log it or its closure.
    private record Stream(SseEmitter emitter, BooleanSupplier authorized) {}
    private final Map<Long, List<Stream>> streams = new ConcurrentHashMap<>();
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
    void stopHeartbeat() { heartbeat.shutdownNow(); }

    /** Authenticated at the controller; also revalidate before every emitted event. */
    public SseEmitter subscribe(Long adminId, BooleanSupplier authorized) {
        if (adminId == null || authorized == null) throw new IllegalArgumentException("Stream identity required");
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        Stream stream = new Stream(emitter, authorized);
        emitter.onCompletion(() -> remove(adminId, stream));
        emitter.onTimeout(() -> close(adminId, stream));
        emitter.onError(e -> remove(adminId, stream));

        // Complete an evicted stream outside the map's compute callback: its completion
        // callback itself mutates the map and must not cause a recursive update.
        java.util.ArrayList<Stream> evicted = new java.util.ArrayList<>();
        streams.compute(adminId, (id, list) -> {
            List<Stream> current = list == null ? new CopyOnWriteArrayList<>() : list;
            while (current.size() >= MAX_STREAMS_PER_ADMIN) evicted.add(current.remove(0));
            current.add(stream);
            return current;
        });
        evicted.forEach(old -> close(adminId, old));
        if (!isAuthorized(stream)) {
            close(adminId, stream);
            return emitter;
        }
        try {
            emitter.send(SseEmitter.event().name("ready").data("ok").reconnectTime(3000));
        } catch (IOException | IllegalStateException e) {
            close(adminId, stream);
        }
        return emitter;
    }

    /** Publish only after the business transaction commits; delivery is best-effort. */
    public void publish(Long adminId, String status) {
        List<Stream> list = streams.get(adminId);
        if (list == null) return;
        for (Stream stream : list) {
            if (!isAuthorized(stream)) { close(adminId, stream); continue; }
            try {
                stream.emitter().send(SseEmitter.event().name(EVENT_NAME)
                        .data(Map.of("status", status), MediaType.APPLICATION_JSON));
            } catch (Exception e) { close(adminId, stream); }
        }
    }

    // Package-private for deterministic unit tests; scheduled in production.
    void sendHeartbeat() {
        streams.forEach((adminId, list) -> {
            for (Stream stream : list) {
                if (!isAuthorized(stream)) { close(adminId, stream); continue; }
                try { stream.emitter().send(SseEmitter.event().comment("hb")); }
                catch (Exception e) { close(adminId, stream); }
            }
        });
    }
    private boolean isAuthorized(Stream stream) {
        try { return stream.authorized().getAsBoolean(); }
        catch (RuntimeException e) { return false; } // DB/Redis outage: close, never continue emitting
    }
    private void close(Long adminId, Stream stream) {
        remove(adminId, stream);
        try { stream.emitter().complete(); } catch (RuntimeException ignored) { }
    }
    private void remove(Long adminId, Stream stream) {
        streams.computeIfPresent(adminId, (id, list) -> {
            list.remove(stream);
            return list.isEmpty() ? null : list;
        });
    }
}
