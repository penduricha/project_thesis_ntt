package com.example.be_web_admin.chat.ws;

import com.example.be_web_admin.chat.ChatProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;

/**
 * WebSocket chỉ chạy một chiều: server đẩy sự kiện, client chỉ gửi "ping" (JSON ở cấp ứng dụng vì trình duyệt không
 * gửi được ping giao thức). Mọi thao tác ghi dữ liệu đều đi qua REST.
 */
@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatSessionRegistry registry;
    private final ObjectMapper objectMapper;
    private final ChatProperties properties;

    public ChatWebSocketHandler(ChatSessionRegistry registry, ObjectMapper objectMapper, ChatProperties properties) {
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long adminId = (Long) session.getAttributes().get(ChatHandshakeInterceptor.ATTR_ADMIN_ID);
        if (adminId == null) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        ChatProperties.Ws ws = properties.getWs();
        // Bọc để nhiều luồng (dispatcher sự kiện, phản hồi ping) gửi cùng lúc an toàn; vượt thời gian hoặc bộ đệm thì
        // Spring tự đóng phiên (mã 1008 SESSION_NOT_RELIABLE) và ném SessionLimitExceededException.
        WebSocketSession decorated = new ConcurrentWebSocketSessionDecorator(
                session, ws.getSendTimeLimitMs(), ws.getSendBufferBytes());
        ChatConnection connection = new ChatConnection(adminId, decorated);
        session.getAttributes().put(ChatConnection.ATTR, connection);

        if (!registry.register(connection)) {
            connection.close(new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(), "too many connections"));
            return;
        }
        ObjectNode data = objectMapper.createObjectNode()
                .put("adminId", adminId)
                .put("serverTime", Instant.now().toString())
                .put("pingIntervalSec", ws.getPingIntervalSeconds());
        registry.sendTo(connection, frame("ready", data));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        ChatConnection connection = (ChatConnection) session.getAttributes().get(ChatConnection.ATTR);
        if (connection == null) {
            return;
        }
        connection.touch();
        if (message.getPayloadLength() > properties.getWs().getMaxClientFrameBytes()) {
            connection.close(ChatSessionRegistry.BAD_FRAME);
            return;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            connection.close(ChatSessionRegistry.BAD_FRAME);
            return;
        }
        if (root == null || !root.isObject()) {
            connection.close(ChatSessionRegistry.BAD_FRAME);
            return;
        }
        String type = root.path("type").asText("");
        if ("ping".equals(type)) {
            ObjectNode data = objectMapper.createObjectNode();
            JsonNode t = root.path("data").path("t");
            if (t.isMissingNode() || t.isNull()) {
                data.putNull("t");
            } else {
                data.set("t", t);
            }
            registry.sendTo(connection, frame("pong", data));
        } else {
            ObjectNode data = objectMapper.createObjectNode()
                    .put("code", "UNKNOWN_TYPE")
                    .put("message", "Chỉ hỗ trợ khung ping.");
            registry.sendTo(connection, frame("error", data));
        }
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        ChatConnection connection = (ChatConnection) session.getAttributes().get(ChatConnection.ATTR);
        if (connection != null) {
            connection.touch();
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("Chat: lỗi truyền tải WebSocket ({}): {}", session.getId(), exception.toString());
        ChatConnection connection = (ChatConnection) session.getAttributes().get(ChatConnection.ATTR);
        if (connection != null) {
            registry.unregister(connection);
        }
        closeQuietly(session, CloseStatus.SERVER_ERROR);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        ChatConnection connection = (ChatConnection) session.getAttributes().get(ChatConnection.ATTR);
        if (connection != null) {
            registry.unregister(connection);
        }
    }

    private String frame(String type, ObjectNode data) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", type);
        root.set("data", data);
        return root.toString();
    }

    private static void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception ignored) {
            // Phiên đã đóng.
        }
    }
}
