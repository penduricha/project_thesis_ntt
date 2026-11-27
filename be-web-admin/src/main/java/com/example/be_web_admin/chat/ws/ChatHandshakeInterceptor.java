package com.example.be_web_admin.chat.ws;

import com.example.be_web_admin.security.AuthSessionStore;
import com.example.be_web_admin.chat.exception.ChatException;
import com.example.be_web_admin.chat.service.WsTicketService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.example.be_web_admin.chat.ChatProperties;

import java.util.Map;
import java.util.Optional;

/**
 * Chạy TRƯỚC khi nâng cấp kết nối: lấy vé bằng GETDEL (dùng một lần), kiểm tra số kết nối của admin, gắn adminId vào
 * thuộc tính phiên. Sai thì trả 401, 429 hoặc 503 và KHÔNG nâng cấp (không mở kết nối rồi mới đóng).
 * Kiểm tra Origin do chính đăng ký trong ChatWebSocketConfig.setAllowedOriginPatterns đảm nhiệm (trả 403).
 */
@Slf4j
@Component
public class ChatHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_ADMIN_ID = "chat.adminId";
    public static final String ATTR_IDENTITY = "chat.identity";

    private final WsTicketService ticketService;
    private final ChatSessionRegistry registry;
    private final ChatProperties properties;
    private final AuthSessionStore sessions;

    public ChatHandshakeInterceptor(WsTicketService ticketService, ChatSessionRegistry registry, ChatProperties properties, AuthSessionStore sessions) {
        this.ticketService = ticketService;
        this.registry = registry;
        this.properties = properties;
        this.sessions = sessions;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
        HttpServletRequest http = servletRequest.getServletRequest();
        String ticket = http.getParameter("ticket");

        Optional<AuthSessionStore.Identity> identity;
        try {
            identity = ticketService.consume(ticket);
        } catch (ChatException e) {
            log.warn("Chat WS: từ chối bắt tay 503 (Redis không đọc được vé): {}", e.getMessage());
            response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return false;
        }
        if (identity.isEmpty()) {
            log.info("Chat WS: từ chối bắt tay 401 (vé sai, đã dùng hoặc hết hạn), Origin={}",
                    http.getHeader("Origin"));
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        AuthSessionStore.Identity auth = identity.get();
        AuthSessionStore.State state = sessions.state(auth);
        if (state != AuthSessionStore.State.ACTIVE) {
            response.setStatusCode(state == AuthSessionStore.State.UNAVAILABLE ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.UNAUTHORIZED);
            return false;
        }
        if (registry.count(auth.adminId()) >= properties.getWs().getMaxConnectionsPerAdmin()) {
            log.info("Chat WS: từ chối bắt tay 429 (admin {} đã đủ {} kết nối)", auth.adminId(),
                    properties.getWs().getMaxConnectionsPerAdmin());
            response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return false;
        }
        attributes.put(ATTR_ADMIN_ID, auth.adminId());
        attributes.put(ATTR_IDENTITY, auth);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // Không cần làm gì.
    }
}
