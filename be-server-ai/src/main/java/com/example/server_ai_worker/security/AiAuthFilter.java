package com.example.server_ai_worker.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.server_ai_worker.config.AiProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The worker is a trusted internal service, not an end-user authentication boundary.
 * JWT signatures alone cannot establish a live session, current role, or task ownership.
 * Only a server-to-server credential is accepted; be-web-admin must authorize the user first.
 * Do not expose this credential to a browser or publish the worker port.
 */
public class AiAuthFilter extends OncePerRequestFilter {
    private static final String INTERNAL_HEADER = "X-Internal-Token";
    private static final String BEARER = "Bearer ";
    private final AiProperties props;

    public AiAuthFilter(AiProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            String bearer = header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length())
                    ? trimToNull(header.substring(BEARER.length())) : null;
            String configured = trimToNull(props.getSecurity().getInternalToken());
            if (configured != null && (constantTimeEquals(request.getHeader(INTERNAL_HEADER), configured)
                    || constantTimeEquals(bearer, configured))) {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken("internal-worker", null,
                                List.of(new SimpleGrantedAuthority("ROLE_INTERNAL"))));
            }
        }
        filterChain.doFilter(request, response);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return a != null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
