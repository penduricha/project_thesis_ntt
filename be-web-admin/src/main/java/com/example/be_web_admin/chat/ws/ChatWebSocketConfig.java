package com.example.be_web_admin.chat.ws;

import com.example.be_web_admin.chat.ChatProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * Đăng ký WebSocket tại /mobile/chat/ws (URL đầy đủ là /api/mobile/chat/ws vì context-path là /api).
 * Origin được phép lấy từ chat.ws.allowed-origin-patterns; client không gửi Origin (ứng dụng mobile gốc) thì được qua,
 * lớp bảo vệ chính là vé một lần ở ChatHandshakeInterceptor.
 */
@Configuration
@EnableWebSocket
public class ChatWebSocketConfig implements WebSocketConfigurer {

    public static final String PATH = "/mobile/chat/ws";

    private final ChatWebSocketHandler handler;
    private final ChatHandshakeInterceptor interceptor;
    private final ChatProperties properties;

    public ChatWebSocketConfig(ChatWebSocketHandler handler, ChatHandshakeInterceptor interceptor, ChatProperties properties) {
        this.handler = handler;
        this.interceptor = interceptor;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, PATH)
                .addInterceptors(interceptor)
                .setAllowedOriginPatterns(properties.getWs().getAllowedOriginPatterns().toArray(new String[0]));
    }

    /** Giới hạn bộ đệm khung của container: khung client chỉ là ping vài chục byte, không cần 8 KB mặc định trở lên. */
    @Bean
    public ServletServerContainerFactoryBean chatWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(properties.getWs().getContainerTextBufferBytes());
        container.setMaxBinaryMessageBufferSize(1_024);
        return container;
    }
}
