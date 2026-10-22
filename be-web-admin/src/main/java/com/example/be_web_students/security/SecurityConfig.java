package com.example.be_web_students.security;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.filters.CorsFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /* ===================================================================================
     * TRƯỜNG HỢP 1: THOẢI MÁI (DEV / TEST NHANH)
     * - Không bắt buộc username/password hay token (Permit All mọi API)
     * - Cho phép MỌI Origin, Method, Header truy cập qua CORS
     * =================================================================================== */

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. Kích hoạt CORS hỗ trợ Spring Security
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // 2. BẮT BUỘC: Cho phép toàn bộ Request Preflight (OPTIONS) đi qua
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().permitAll()
                );

        return http.build();
    }

    // 3. Khai báo Bean nguồn cấu hình CORS
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Chấp nhận tất cả Origin từ Localhost (Bao gồm port 63342 của IDE)
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*"
        ));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setExposedHeaders(List.of("Authorization"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }



    /* ===================================================================================
     * TRƯỜNG HỢP 2: RÀNG BUỘC SECURITY (PRODUCTION / SECURED)
     * - Bắt buộc phải có Authorization Header (Basic Auth / JWT)
     * - Chỉ các API trong danh sách permitAll mới được truy cập công khai
     * - Siết chặt CORS: Chỉ Origin và Header được khai báo mới được phép gọi sang
     * (BỎ COMMENT TOÀN BỘ BLOCK DƯỚI ĐÂY VÀ COMMENT BLOCK TRƯỜNG HỢP 1 KHI DÙNG)
     * =================================================================================== */

    /*

    @Bean
    public SecurityFilterChain filterChainSecured(HttpSecurity http) throws RuntimeException {
        http
                .cors(cors -> cors.configurationSource(corsSecuredSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        //Cho phep free cac request nay
                        .requestMatchers("/student/auth/login/auto/refresh-token").permitAll()
                        .requestMatchers("/student/auth/login/auto/access-token").permitAll()
                        .anyRequest().authenticated()
                )
                .httpBasic(basic -> basic
                        // Bắt sự kiện 401 của Basic Auth và trả về JSON/Error thuần túy
                        // BỎ HEADER 'WWW-Authenticate' để Browser không chặn JS và không hiện Pop-up
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"status\": 401, \"message\": \"Unauthorized\"}");
                        })
                );

        return http.build();
    }

    private CorsConfigurationSource corsSecuredSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of(
                "http://localhost:63342",
                "http://localhost:5173",  // Origin của Vue/Vite
                "http://localhost:3000",
                "http://localhost:3001"
        ));

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));

        // Bổ sung "X-Client-Auth" hoặc dùng "*" để chấp nhận mọi header gửi từ Frontend
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Origin",
                "X-Client-Auth" // <--- THÊM HEADER NÀY ĐỂ FIX LỖI CORS PREFLIGHT
        ));

        // Hoặc cho phép tất cả các headers:
        // configuration.setAllowedHeaders(List.of("*"));

        configuration.setExposedHeaders(List.of("Authorization", "Link", "X-Total-Count"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
    */
}