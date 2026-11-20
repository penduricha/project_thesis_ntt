package com.example.be_server_ai.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * CHỈ DÙNG KHI DEMO gọi LLM: mở mọi request (không JWT), tắt CSRF, cho phép CORS theo @CrossOrigin của controller
 * để trang node-colab.html (mở từ file hoặc từ máy khác) gọi được mọi phương thức GET, PUT, POST, DELETE.
 *
 * <p>Khi tích hợp với Server 1: thay anyRequest().permitAll() bằng xác thực (JWT hoặc khóa nội bộ), vì các
 * endpoint /web/ai/nodes nhận token Colab. File này chỉ khai báo chuỗi lọc; nếu SecurityConfig cũ của bạn có
 * @Bean PasswordEncoder thì giữ lại bean đó.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }
}
