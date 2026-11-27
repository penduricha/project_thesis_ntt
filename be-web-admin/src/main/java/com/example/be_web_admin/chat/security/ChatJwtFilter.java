package com.example.be_web_admin.chat.security;

import com.example.be_web_admin.security.AuthSessionStore;
import com.example.be_web_admin.security.jwt.user.AdminJwtTokenProvider;
import com.example.be_web_admin.service.redis.AdminTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Đọc "Authorization: Bearer ..." và xác thực ACCESS JWT + đối chiếu Redis AdminAccessToken
 * để đặt adminId làm người dùng hiện tại (principal là Long).
 * Cố ý KHÔNG khai báo là bean (không @Component) để chỉ chạy trong ChatSecurityConfig.
 */
@Slf4j
public class ChatJwtFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final AdminJwtTokenProvider tokenProvider;
    private final AdminTokenService adminTokenService;

    public ChatJwtFilter(AdminJwtTokenProvider tokenProvider, AdminTokenService adminTokenService) {
        this.tokenProvider = tokenProvider;
        this.adminTokenService = adminTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            String token = header.substring(BEARER.length()).trim();
            AuthSessionStore.Identity identity = tokenProvider.parseIdentity(token, "ACCESS");
            if (identity != null && identity.client() == AuthSessionStore.Client.MOBILE) {
                Long adminId = identity.adminId();
                try {
                    if (adminTokenService.findAdminAccessTokenByJwtToken(token)) {
                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(adminId, null, AuthorityUtils.NO_AUTHORITIES);
                        authentication.setDetails(identity);
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } catch (Exception e) {
                    log.warn("Chat: không kiểm tra được access token trên Redis: {}", e.getMessage());
                }
            }
        }
        chain.doFilter(request, response);
    }
}
