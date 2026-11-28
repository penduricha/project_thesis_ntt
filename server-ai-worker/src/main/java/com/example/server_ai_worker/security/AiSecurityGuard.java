package com.example.server_ai_worker.security;

import java.util.Arrays;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.example.server_ai_worker.config.AiProperties;
import jakarta.annotation.PostConstruct;

/** Fail closed: only server-to-server authentication is supported by this worker. */
@Component
public class AiSecurityGuard {
    private static final Logger log = LoggerFactory.getLogger(AiSecurityGuard.class);
    private static final Set<String> DEV_PROFILES = Set.of("dev", "test", "local");
    private final AiProperties props;
    private final Environment environment;

    public AiSecurityGuard(AiProperties props, Environment environment) {
        this.props = props;
        this.environment = environment;
    }

    @PostConstruct
    public void validateOnStartup() {
        String raw = props.getSecurity().getInternalToken();
        String token = raw == null ? "" : raw.trim();
        if (!token.isEmpty() && token.length() < 16) {
            throw new IllegalStateException("AI_INTERNAL_TOKEN phải dài ít nhất 16 ký tự.");
        }
        boolean dev = Arrays.stream(environment.getActiveProfiles()).anyMatch(DEV_PROFILES::contains);
        if (token.isEmpty()) {
            if (!dev) throw new IllegalStateException("Phải cấu hình AI_INTERNAL_TOKEN cho server-ai-worker ngoài profile dev/test/local.");
            log.warn("AI_INTERNAL_TOKEN trống: API worker sẽ từ chối mọi request.");
        }
        if (props.getSecurity().getJwtSecret() != null && !props.getSecurity().getJwtSecret().isBlank()) {
            log.warn("JWT người dùng không còn được worker chấp nhận; chỉ dùng kết nối server-to-server.");
        }
    }
}
