package com.example.server_ai_worker.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.server_ai_worker.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Xác thực request tới /web/ai/** bằng một trong hai cơ chế:
 * 1) Token nội bộ (header X-Internal-Token hoặc Authorization: Bearer <internalToken>) dùng giữa Server 1 <-> Server 2;
 * 2) ACCESS JWT ký HMAC-SHA (HS256/HS384/HS512) do be-web-admin phát hành (kiểm tra chữ ký, exp, token_type == "ACCESS").
 *
 * Không gắn @Component để tránh đăng ký filter toàn cục hai lần ngoài SecurityFilterChain.
 */
public class AiAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AiAuthFilter.class);
    private static final String INTERNAL_HEADER = "X-Internal-Token";
    private static final String BEARER = "Bearer ";
    private static final int MIN_SECRET_BYTES = 32;

    private final AiProperties props;
    private final ObjectMapper json;

    public AiAuthFilter(AiProperties props, ObjectMapper json) {
        this.props = props;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String bearer = extractBearer(request.getHeader(HttpHeaders.AUTHORIZATION));
            String internalHeader = request.getHeader(INTERNAL_HEADER);
            String configuredInternal = trimToNull(props.getSecurity().getInternalToken());

            if (configuredInternal != null && (constantTimeEquals(internalHeader, configuredInternal)
                    || constantTimeEquals(bearer, configuredInternal))) {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                "internal-worker",
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_INTERNAL"),
                                        new SimpleGrantedAuthority("ROLE_ADMIN"))));
            } else if (bearer != null) {
                Long adminId = verifyAccessJwt(bearer, trimToNull(props.getSecurity().getJwtSecret()));
                if (adminId != null) {
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    adminId,
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    Long verifyAccessJwt(String token, String secret) {
        if (secret == null) {
            return null;
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }
        try {
            Base64.Decoder urlDecoder = Base64.getUrlDecoder();
            JsonNode header = json.readTree(urlDecoder.decode(parts[0]));
            String alg = header.path("alg").asText("");
            String jcaAlg = switch (alg) {
                case "HS256" -> "HmacSHA256";
                case "HS384" -> "HmacSHA384";
                case "HS512" -> "HmacSHA512";
                default -> null;
            };
            if (jcaAlg == null) {
                return null;
            }

            Mac mac = Mac.getInstance(jcaAlg);
            mac.init(new SecretKeySpec(secretBytes, jcaAlg));
            byte[] expectedSig = mac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            byte[] actualSig = urlDecoder.decode(parts[2]);
            if (!MessageDigest.isEqual(expectedSig, actualSig)) {
                return null;
            }

            JsonNode payload = json.readTree(urlDecoder.decode(parts[1]));
            JsonNode expNode = payload.get("exp");
            if (expNode == null || !expNode.isNumber()) {
                return null;
            }
            if (expNode.asLong() <= Instant.now().getEpochSecond()) {
                return null;
            }
            String tokenType = payload.path("token_type").asText("");
            if (!"ACCESS".equals(tokenType)) {
                return null;
            }
            String sub = payload.path("sub").asText("").trim();
            if (sub.isEmpty()) {
                return null;
            }
            return Long.parseLong(sub);
        } catch (Exception e) {
            log.debug("JWT không hợp lệ trên server-ai-worker: {}", e.getMessage());
            return null;
        }
    }

    private static String extractBearer(String authHeader) {
        if (authHeader == null || !authHeader.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return null;
        }
        return trimToNull(authHeader.substring(BEARER.length()));
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
