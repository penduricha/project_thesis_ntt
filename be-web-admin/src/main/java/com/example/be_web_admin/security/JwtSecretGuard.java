package com.example.be_web_admin.security;

import com.example.be_web_admin.security.jwt.user.AdminJwtTokenProvider;
import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;

/**
 * Ngăn ứng dụng khởi động với JWT_SECRET rỗng, quá yếu (<32 byte) hoặc trùng khóa từng bị lộ trong Git
 * khi chạy ngoài các profile phát triển (dev, test, local).
 */
@Component
public class JwtSecretGuard {

    private static final Set<String> DEV_PROFILES = Set.of("dev", "test", "local");
    private static final String COMPROMISED_SECRET_SHA256 =
            "31cc11ae9a450003200e1d2fba434159c65c7ce6b147a65353c06c080dc25a6e";

    private final AdminJwtTokenProvider tokenProvider;
    private final Environment environment;

    public JwtSecretGuard(AdminJwtTokenProvider tokenProvider, Environment environment) {
        this.tokenProvider = tokenProvider;
        this.environment = environment;
    }

    @PostConstruct
    public void validateOnStartup() {
        String secret = tokenProvider.getConfiguredSecret();
        boolean isDevProfile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(DEV_PROFILES::contains);

        if (!isDevProfile && (tokenProvider.isUsingEphemeralDevKey() || secret.isBlank())) {
            throw new IllegalStateException(
                    "JWT_SECRET bắt buộc phải được cấu hình (>= 32 ký tự) khi không chạy dưới profile dev/test/local.");
        }

        if (!secret.isBlank() && sha256Hex(secret).equalsIgnoreCase(COMPROMISED_SECRET_SHA256)) {
            throw new IllegalStateException(
                    "JWT_SECRET đang trùng với khóa mẫu cũ từng lộ trong Git. Hãy tạo khóa mới bằng: openssl rand -base64 48");
        }
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
