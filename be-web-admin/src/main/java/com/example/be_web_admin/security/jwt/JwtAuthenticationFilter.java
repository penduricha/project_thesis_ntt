package com.example.be_web_admin.security.jwt;

import com.example.be_web_admin.security.jwt.user.AdminJwtTokenProvider;
import com.example.be_web_admin.service.redis.AdminTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Xác thực Bearer ACCESS JWT và đối chiếu Redis AdminAccessToken cho các API web/mobile (trừ /mobile/chat/**).
 * Không khai báo @Component để tránh Spring Boot đăng ký thành servlet filter toàn cục chạy trùng với ChatJwtFilter.
 */
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final AdminJwtTokenProvider tokenProvider;
    private final AdminTokenService adminTokenService;

    public JwtAuthenticationFilter(AdminJwtTokenProvider tokenProvider, AdminTokenService adminTokenService) {
        this.tokenProvider = tokenProvider;
        this.adminTokenService = adminTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
                String token = header.substring(BEARER.length()).trim();
                Long adminId = tokenProvider.parseAccessTokenAdminId(token);
                if (adminId != null) {
                    try {
                        if (adminTokenService.findAdminAccessTokenByJwtToken(token)) {
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(
                                            adminId,
                                            null,
                                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
                        }
                    } catch (Exception e) {
                        log.warn("Không kiểm tra được access token trên Redis: {}", e.getMessage());
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
