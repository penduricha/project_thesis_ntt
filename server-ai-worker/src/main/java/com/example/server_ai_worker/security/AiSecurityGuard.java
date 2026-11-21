package com.example.server_ai_worker.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.example.server_ai_worker.config.AiProperties;

import jakarta.annotation.PostConstruct;

/**
 * Kiểm tra cấu hình xác thực khi khởi động server-ai-worker:
 * - Chặn khóa JWT từng bị lộ trong Git;
 * - Yêu cầu ít nhất JWT_SECRET (>= 32 byte) hoặc AI_INTERNAL_TOKEN (>= 16 ký tự) ngoài profile dev/test/local.
 */
@Component
public class AiSecurityGuard {

    private static final Logger log = LoggerFactory.getLogger(AiSecurityGuard.class);
    private static final Set<String> DEV_PROFILES = Set.of("dev", "test", "local");
    private static final String COMPROMISED_SECRET_SHA256 =
            "31cc11ae9a450003200e1d2fba434159c65c7ce6b147a65353c06c080dc25a6e";

    private final AiProperties props;
    private final Environment environment;

    public AiSecurityGuard(AiProperties props, Environment environment) {
        this.props = props;
        this.environment = environment;
    }

    @PostConstruct
    public void validateOnStartup() {
        String jwtSecret = safeTrim(props.getSecurity().getJwtSecret());
        String internalToken = safeTrim(props.getSecurity().getInternalToken());

        if (!jwtSecret.isEmpty()) {
            if (sha256Hex(jwtSecret).equalsIgnoreCase(COMPROMISED_SECRET_SHA256)) {
                throw new IllegalStateException(
                        "JWT_SECRET đang trùng với khóa mẫu cũ từng lộ trong Git. Hãy đổi khóa mới.");
            }
            if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalStateException("JWT_SECRET phải dài ít nhất 32 byte.");
            }
        }
        if (!internalToken.isEmpty() && internalToken.length() < 16) {
            throw new IllegalStateException("AI_INTERNAL_TOKEN phải dài ít nhất 16 ký tự.");
        }

        boolean isDevProfile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(DEV_PROFILES::contains);
        if (jwtSecret.isEmpty() && internalToken.isEmpty()) {
            if (!isDevProfile) {
                throw new IllegalStateException(
                        "Phải cấu hình JWT_SECRET (>= 32 byte) hoặc AI_INTERNAL_TOKEN (>= 16 ký tự) trong server-ai-worker/.env (hoặc biến môi trường) khi chạy ngoài profile dev/test/local.");
            }
            log.warn("JWT_SECRET và AI_INTERNAL_TOKEN đều đang trống: các API /web/ai/** sẽ từ chối mọi request (401) cho tới khi cấu hình.");
        }
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
