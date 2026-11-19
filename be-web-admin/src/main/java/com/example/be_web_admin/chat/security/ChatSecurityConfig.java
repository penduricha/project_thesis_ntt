package com.example.be_web_admin.chat.security;

import com.example.be_web_admin.security.jwt.user.AdminJwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.nio.charset.StandardCharsets;

/**
 * Chuỗi bảo mật RIÊNG cho /mobile/chat/** (không tính context-path /api), ưu tiên cao hơn chuỗi có sẵn trong
 * SecurityConfig. Chuỗi cũ (anyRequest().permitAll()) giữ nguyên cho mọi đường dẫn khác, nên các API 2FA/TOTP
 * hiện có không đổi hành vi. Không dùng phiên HTTP, tắt CSRF cho nhánh này (xác thực bằng Bearer, không bằng cookie).
 *
 * Dùng AntPathRequestMatcher tường minh để không phụ thuộc cách Spring MVC phân giải mẫu đường dẫn.
 */
@Configuration
public class ChatSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain chatSecurityFilterChain(HttpSecurity http, AdminJwtTokenProvider tokenProvider) throws Exception {
        http
                .securityMatcher(new AntPathRequestMatcher("/mobile/chat/**"))
                // Dùng lại CorsConfigurationSource đã khai báo trong SecurityConfig.
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.OPTIONS, "/mobile/chat/**")).permitAll()
                        // WebSocket tự xác thực bằng vé một lần ở bước bắt tay (ChatHandshakeInterceptor).
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/mobile/chat/ws")).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint((request, response, ex) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Thiếu hoặc sai access token.\"}");
                }))
                .addFilterBefore(new ChatJwtFilter(tokenProvider), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
