package com.example.be_web_admin.chat.ws;

import com.example.be_web_admin.security.AuthSessionStore;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

/**
 * Một kết nối WebSocket đang mở của một admin. Gói phiên đã bọc ConcurrentWebSocketSessionDecorator (cho phép nhiều
 * luồng gửi cùng lúc, có hạn thời gian và bộ đệm) cùng mốc "lần cuối thấy khung nào" để dọn kết nối im lặng.
 */
public final class ChatConnection {

    /** Khóa thuộc tính phiên: chứa ChatConnection để handler tra lại từ phiên gốc. */
    public static final String ATTR = "chat.connection";

    private final AuthSessionStore.Identity identity;
    private final WebSocketSession session;
    private volatile long lastSeenNanos = System.nanoTime();

    public ChatConnection(AuthSessionStore.Identity identity, WebSocketSession decoratedSession) {
        this.identity = identity;
        this.session = decoratedSession;
    }

    public Long adminId() {
        return identity.adminId();
    }

    public AuthSessionStore.Identity identity() { return identity; }

    public String sessionId() {
        return session.getId();
    }

    public boolean isOpen() {
        return session.isOpen();
    }

    /** Ghi nhận vừa thấy một khung từ client (ping, pong giao thức, hay bất kỳ khung nào). */
    public void touch() {
        lastSeenNanos = System.nanoTime();
    }

    public long idleMillis() {
        return (System.nanoTime() - lastSeenNanos) / 1_000_000L;
    }

    public void send(String json) throws IOException {
        session.sendMessage(new TextMessage(json));
    }

    public void close(CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException | RuntimeException ignored) {
            // Đóng một kết nối đã hỏng: không còn gì để làm.
        }
    }
}
