package com.example.be_web_admin.controller.mobile;

import com.example.be_web_admin.security.AuthSessionStore;
import com.example.be_web_admin.security.jwt.user.AdminJwtTokenProvider;
import com.example.be_web_admin.service.redis.impl.AdminTokenServiceImpl;
import com.example.be_web_admin.service.redis.impl.IdempotentEnrollmentConfirmService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** Disabled by default: enable only on a disposable, migrated integration test environment. */
@RestController
@RequestMapping("/mobile/admin/2fa/enrollment")
@ConditionalOnProperty(prefix = "app.totp", name = "idempotent-confirm-enabled", havingValue = "true")
public class IdempotentEnrollmentConfirmController {
    private final IdempotentEnrollmentConfirmService service;
    private final AdminJwtTokenProvider jwt;
    private final AdminTokenServiceImpl tokens;
    public IdempotentEnrollmentConfirmController(IdempotentEnrollmentConfirmService service,
                                                 AdminJwtTokenProvider jwt, AdminTokenServiceImpl tokens) {
        this.service = service; this.jwt = jwt; this.tokens = tokens;
    }

    @PostMapping("/confirm-v2")
    public ResponseEntity<?> confirm(@RequestHeader(value = "Authorization", required = false) String auth,
                                     @RequestBody(required = false) Map<String, Object> body,
                                     HttpServletRequest request) {
        AuthSessionStore.Identity identity = resolve(auth);
        if (identity == null) return response(HttpStatus.UNAUTHORIZED, false, "Session is invalid or ended.", Map.of());
        Object rawId = body == null ? null : body.get("credentialId");
        Object rawCode = body == null ? null : body.get("code");
        Object rawRequest = body == null ? null : body.get("requestId");
        if (!(rawId instanceof Number || rawId instanceof String) || !(rawCode instanceof String code) ||
                !(rawRequest instanceof String requestId) || !code.matches("[0-9]{6}") ||
                !requestId.matches("[0-9a-f]{64}")) {
            return response(HttpStatus.BAD_REQUEST, false, "Invalid confirmation request.", Map.of());
        }
        Long credentialId;
        try {
            String id = rawId.toString();
            if (!id.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException();
            credentialId = Long.valueOf(id);
        } catch (NumberFormatException e) {
            return response(HttpStatus.BAD_REQUEST, false, "Invalid confirmation request.", Map.of());
        }
        IdempotentEnrollmentConfirmService.Result result = service.confirm(identity, credentialId, requestId, code,
                request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT));
        return switch (result) {
            case CREATED, REPLAYED -> response(HttpStatus.OK, true, "2FA activated.",
                    Map.of("credentialId", credentialId, "totpEnabled", true));
            case INVALID -> response(HttpStatus.UNPROCESSABLE_ENTITY, false, "Wrong code.", Map.of());
            case CONFLICT -> response(HttpStatus.CONFLICT, false, "Confirmation cannot be reconciled.", Map.of());
            case EXPIRED -> response(HttpStatus.GONE, false, "Enrollment expired.", Map.of());
            case NOT_FOUND -> response(HttpStatus.NOT_FOUND, false, "Enrollment not found.", Map.of());
            case UNAUTHORIZED -> response(HttpStatus.UNAUTHORIZED, false, "Session is invalid or ended.", Map.of());
            case LOCKED, RATE_LIMITED -> response(HttpStatus.TOO_MANY_REQUESTS, false, "Too many attempts.", Map.of());
        };
    }

    private AuthSessionStore.Identity resolve(String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) return null;
        String token = auth.substring(7);
        AuthSessionStore.Identity identity = jwt.parseIdentity(token, "ACCESS");
        if (identity == null || identity.client() != AuthSessionStore.Client.MOBILE) return null;
        try { return tokens.findAdminAccessTokenByJwtToken(token) ? identity : null; }
        catch (RuntimeException e) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Authentication temporarily unavailable", e); }
    }
    private ResponseEntity<?> response(HttpStatus status, boolean success, String message, Object data) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(Map.of("success", success, "message", message, "data", data));
    }
}
