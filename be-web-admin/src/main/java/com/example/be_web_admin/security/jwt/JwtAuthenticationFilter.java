package com.example.be_web_admin.security.jwt;

import com.example.be_web_admin.security.AuthSessionStore;
import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.repository.jpa.AdminRepository;
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

    /**
     * Đọc vai trò thật của admin.
     *
     * <p>Trước đây filter này gán cứng {@code ROLE_ADMIN} cho mọi token hợp lệ, nên tài khoản
     * LECTURER cũng có toàn quyền như ADMIN. Không lấy vai trò từ JWT claim vì:</p>
     * <ul>
     *   <li>token đã cấp trước khi vá không có claim đó — sẽ phải chờ hết hạn/đăng nhập lại;</li>
     *   <li>claim nằm trong token 5 phút, hạ quyền một admin mà token cũ vẫn còn quyền cũ.</li>
     * </ul>
     * <p>Đọc từ DB là nguồn sự thật và có hiệu lực ngay. Truy vấn này chỉ chạy SAU khi Redis đã
     * xác nhận token còn sống, nên nó chỉ chạy trên request có token hợp lệ (không phải mọi
     * request, và không phải request bị từ chối).</p>
     *
     * <p>Không tìm thấy admin thì không xác thực. Lỗi Redis/DB trả 503 để client không
     * nhầm sự cố tạm thời với phiên bị thu hồi.</p>
     */
    private final AdminRepository adminRepository;

    public JwtAuthenticationFilter(AdminJwtTokenProvider tokenProvider,
                                   AdminTokenService adminTokenService,
                                   AdminRepository adminRepository) {
        this.tokenProvider = tokenProvider;
        this.adminTokenService = adminTokenService;
        this.adminRepository = adminRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
                String token = header.substring(BEARER.length()).trim();
                AuthSessionStore.Identity identity = tokenProvider.parseIdentity(token, "ACCESS");
                String path = request.getServletPath();
                boolean allowedClient = identity != null &&
                        ((path.startsWith("/web/") && identity.client() == AuthSessionStore.Client.WEB) ||
                         (path.startsWith("/mobile/") && identity.client() == AuthSessionStore.Client.MOBILE));
                if (allowedClient) {
                    Long adminId = identity.adminId();
                    try {
                        if (adminTokenService.findAdminAccessTokenByJwtToken(token)) {
                            String role = resolveRole(adminId);
                            if (role != null) {
                                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                        adminId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                                authentication.setDetails(identity);
                                SecurityContextHolder.getContext().setAuthentication(authentication);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Không kiểm tra được trạng thái xác thực: {}", e.getMessage());
                        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                        response.setContentType("application/json");
                        response.setCharacterEncoding("UTF-8");
                        response.getWriter().write("{\"success\":false,\"code\":\"AUTH_UNAVAILABLE\",\"message\":\"Authentication temporarily unavailable\"}");
                        return;
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    /** Trả "ADMIN" / "LECTURER", hoặc null nếu không xác định được (=> không cấp quyền nào). */
    private String resolveRole(Long adminId) {
        Admin admin = adminRepository.findAdminByAdminId(adminId);
        if (admin == null || !admin.isLoginAllowed() || admin.getRole() == null) return null;
        return admin.getRole().name();
    }
}
