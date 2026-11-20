package com.example.be_web_admin.chat.ws;

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

    private final WsTicketService ticketService;
    private final ChatSessionRegistry registry;
    private final ChatProperties properties;

    public ChatHandshakeInterceptor(WsTicketService ticketService, ChatSessionRegistry registry, ChatProperties properties) {
        this.ticketService = ticketService;
        this.registry = registry;
        this.properties = properties;
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

        Optional<Long> adminId;
        try {
            adminId = ticketService.consume(ticket);
        } catch (ChatException e) {
            log.warn("Chat WS: từ chối bắt tay 503 (Redis không đọc được vé): {}", e.getMessage());
            response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return false;
        }
        if (adminId.isEmpty()) {
            log.info("Chat WS: từ chối bắt tay 401 (vé sai, đã dùng hoặc hết hạn), Origin={}",
                    http.getHeader("Origin"));
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        if (registry.count(adminId.get()) >= properties.getWs().getMaxConnectionsPerAdmin()) {
            log.info("Chat WS: từ chối bắt tay 429 (admin {} đã đủ {} kết nối)", adminId.get(),
                    properties.getWs().getMaxConnectionsPerAdmin());
            response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return false;
        }
        attributes.put(ATTR_ADMIN_ID, adminId.get());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // Không cần làm gì.
    }
}
