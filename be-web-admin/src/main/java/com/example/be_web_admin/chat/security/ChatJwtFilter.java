package com.example.be_web_admin.chat.security;

import com.example.be_web_admin.security.jwt.user.AdminJwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Đọc "Authorization: Bearer ..." và đặt adminId làm người dùng hiện tại (principal là Long).
 * Cố ý KHÔNG khai báo là bean (không @Component): nếu là bean, Spring Boot sẽ tự đăng ký nó thành filter servlet
 * toàn cục và nó chạy với cả các API cũ. Nó chỉ được tạo trong ChatSecurityConfig và chỉ gắn vào chuỗi chat.
 * Token sai hoặc thiếu thì không đặt gì cả; lớp phân quyền phía sau trả 401.
 */
public class ChatJwtFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final AdminJwtTokenProvider tokenProvider;

    public ChatJwtFilter(AdminJwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            Long adminId = tokenProvider.parseAccessTokenAdminId(header.substring(BEARER.length()).trim());
            if (adminId != null) {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(adminId, null, AuthorityUtils.NO_AUTHORITIES));
            }
        }
        chain.doFilter(request, response);
    }
}
