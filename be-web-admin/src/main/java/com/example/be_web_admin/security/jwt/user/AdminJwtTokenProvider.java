package com.example.be_web_admin.security.jwt.user;

import com.example.be_web_admin.security.AuthSessionStore;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;

@Slf4j
@Component
public class AdminJwtTokenProvider {

    private static final int MIN_SECRET_BYTES = 32;
    private static final Set<String> DEV_PROFILES = Set.of("dev", "test", "local");

    // Thời gian hết hạn Access Token: 5 phút = 300,000 ms (khớp AdminAccessToken timeToLive = 300s)
    private static final long ACCESS_TOKEN_EXPIRATION = 5 * 60 * 1000L;

    // Thời gian hết hạn Refresh Token: 30 ngày = 2,592,000,000 ms
    private static final long REFRESH_TOKEN_EXPIRATION = 30L * 24 * 60 * 60 * 1000L;

    private final SecretKey signingKey;
    private final boolean usingEphemeralDevKey;
    private final String configuredSecret;

    @Autowired
    public AdminJwtTokenProvider(
            @Value("${app.jwt.secret:}") String configuredSecret,
            Environment environment) {
        this.configuredSecret = configuredSecret == null ? "" : configuredSecret.trim();
        if (this.configuredSecret.isEmpty()) {
            String[] activeProfiles = environment == null ? new String[0] : environment.getActiveProfiles();
            boolean isDevLike = Arrays.stream(activeProfiles).anyMatch(DEV_PROFILES::contains);
            if (!isDevLike) {
                log.warn("JWT_SECRET chưa được cấu hình; JwtSecretGuard sẽ chặn khởi động ngoài profile dev/test/local.");
            } else {
                log.warn("JWT_SECRET trống trong profile dev/test/local: sinh khóa tạm trong bộ nhớ (token mất hiệu lực sau khi restart).");
            }
            byte[] randomBytes = new byte[48];
            new SecureRandom().nextBytes(randomBytes);
            this.signingKey = Keys.hmacShaKeyFor(randomBytes);
            this.usingEphemeralDevKey = true;
        } else {
            byte[] keyBytes = this.configuredSecret.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException(
                        "JWT_SECRET phải dài ít nhất " + MIN_SECRET_BYTES + " byte (256-bit cho HS256).");
            }
            this.signingKey = Keys.hmacShaKeyFor(keyBytes);
            this.usingEphemeralDevKey = false;
        }
    }

    /** Constructor tiện cho unit test thuần Java. */
    public AdminJwtTokenProvider(String configuredSecret) {
        this(configuredSecret, null);
    }

    public boolean isUsingEphemeralDevKey() {
        return usingEphemeralDevKey;
    }

    public String getConfiguredSecret() {
        return configuredSecret;
    }

    private SecretKey getSigningKey() {
        return signingKey;
    }

    /**
     * 1. Tạo Access Token (Thời hạn 5 phút)
     */
    public String generateAccessToken(Long adminId) { return generateAccessToken(adminId, null); }

    public String generateAccessToken(Long adminId, AuthSessionStore.Identity identity) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + ACCESS_TOKEN_EXPIRATION);

        var builder = Jwts.builder()
                // jti: iat/exp chỉ chính xác đến GIÂY, nên hai lần cấp token cho cùng admin trong cùng một giây
                // sẽ ra chuỗi y hệt nếu không có jti.
                .id(java.util.UUID.randomUUID().toString())
                .subject(String.valueOf(adminId))
                .issuedAt(now)
                .expiration(expiryDate)
                .claim("token_type", "ACCESS");
        if (identity != null) builder.claim("session_id", identity.sessionId()).claim("client", identity.client().name());
        return builder.signWith(getSigningKey()).compact();
    }

    /**
     * 2. Tạo Refresh Token (Thời hạn 30 ngày)
     */
    public String generateRefreshToken(Long adminId) { return generateRefreshToken(adminId, null); }

    public String generateRefreshToken(Long adminId, AuthSessionStore.Identity identity) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + REFRESH_TOKEN_EXPIRATION);

        var builder = Jwts.builder()
                .id(java.util.UUID.randomUUID().toString())
                .subject(String.valueOf(adminId))
                .issuedAt(now)
                .expiration(expiryDate)
                .claim("token_type", "REFRESH");
        if (identity != null) builder.claim("session_id", identity.sessionId()).claim("client", identity.client().name());
        return builder.signWith(getSigningKey()).compact();
    }

    /**
     * 3. Giải mã và Lấy adminId từ Token
     */
    public Long getAdminIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return Long.parseLong(claims.getSubject());
    }

    /**
     * 4. Verify/Xác thực Token (chữ ký, hạn dùng và token_type hợp lệ).
     */
    public boolean validateToken(String token) {
        return parseAdminIdWithType(token, null) != null;
    }

    public boolean validateAccessToken(String token) {
        return parseAccessTokenAdminId(token) != null;
    }

    public boolean validateRefreshToken(String token) {
        return parseRefreshTokenAdminId(token) != null;
    }

    /**
     * 5. Xác thực ACCESS token bằng chữ ký + hạn + token_type == "ACCESS", trả adminId hoặc null.
     */
    public Long parseAccessTokenAdminId(String token) {
        return parseAdminIdWithType(token, "ACCESS");
    }

    /**
     * 6. Xác thực REFRESH token bằng chữ ký + hạn + token_type == "REFRESH", trả adminId hoặc null.
     */
    public Long parseRefreshTokenAdminId(String token) {
        return parseAdminIdWithType(token, "REFRESH");
    }

    /** Session claims are mandatory for live tokens. Legacy tokens must log in again. */
    public AuthSessionStore.Identity parseIdentity(String token, String expectedType) {
        if (token == null || token.isBlank()) return null;
        try {
            Claims claims = Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
            if (!expectedType.equals(claims.get("token_type", String.class))) return null;
            String sessionId = claims.get("session_id", String.class);
            String client = claims.get("client", String.class);
            if (sessionId == null || !sessionId.matches("[0-9a-fA-F-]{36}") || client == null) return null;
            return new AuthSessionStore.Identity(Long.parseLong(claims.getSubject()),
                    AuthSessionStore.Client.valueOf(client), sessionId);
        } catch (JwtException | IllegalArgumentException | NullPointerException e) { return null; }
    }

    private Long parseAdminIdWithType(String token, String expectedType) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String tokenType = claims.get("token_type", String.class);
            if (expectedType != null) {
                if (!expectedType.equals(tokenType)) {
                    return null;
                }
            } else if (!"ACCESS".equals(tokenType) && !"REFRESH".equals(tokenType)) {
                return null;
            }
            return Long.parseLong(claims.getSubject());
        } catch (ExpiredJwtException e) {
            log.debug("JWT token expired.");
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT token invalid.");
        }
        return null;
    }
}
