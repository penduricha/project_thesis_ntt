package com.example.server_ai_worker.security;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.example.server_ai_worker.config.AiProperties;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Bảo mật fail-closed cho Server AI Worker:
 * - Các trang công cụ tĩnh (/node-colab.html, /demo-eval.html) mở tải HTML;
 * - Mọi API yêu cầu credential nội bộ; không chấp nhận JWT người dùng.
 * - CORS tập trung theo danh sách ai.security.cors-allowed-origins (không dùng "*").
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AiProperties props) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/",
                                "/index.html",
                                "/node-colab.html",
                                "/demo-eval.html",
                                "/favicon.ico").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint((request, response, ex) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.getWriter().write("{\"message\":\"Unauthorized\"}");
                }))
                .addFilterBefore(new AiAuthFilter(props), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AiProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = props.getSecurity().getCorsAllowedOrigins().stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !"*".equals(s))
                .toList();
        config.setAllowedOriginPatterns(origins.isEmpty()
                ? List.of("http://localhost:5173", "http://127.0.0.1:5173",
                "http://localhost:8082", "http://127.0.0.1:8082")
                : origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "X-Internal-Token", "X-Requested-With", "Accept", "Origin"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
