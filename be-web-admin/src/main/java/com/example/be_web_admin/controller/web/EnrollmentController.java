package com.example.be_web_admin.controller.web;

import com.example.be_web_admin.security.jwt.user.AdminJwtTokenProvider;
import com.example.be_web_admin.security.AuthSessionStore;
import com.example.be_web_admin.security.EnrollmentGenerationStore;
import com.example.be_web_admin.security.AuthenticationRateLimiter;
import com.example.be_web_admin.service.redis.impl.AdminTokenServiceImpl;
import com.example.be_web_admin.service.redis.impl.EnrollSessionServiceImpl;
import com.example.be_web_admin.service.redis.impl.EnrollmentServiceImpl;
import com.example.be_web_admin.service.redis.impl.EnrollmentServiceImpl.ActivationResult;
import com.example.be_web_admin.service.redis.impl.EnrollmentServiceImpl.AdminNotFoundException;
import com.example.be_web_admin.service.redis.impl.EnrollmentServiceImpl.EnrollmentExpiredException;
import com.example.be_web_admin.service.redis.impl.EnrollmentServiceImpl.TwoFaStatus;
import com.example.be_web_admin.service.redis.impl.TotpAlreadyEnabledException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Giai đoạn 2 (Vue bấm "Kích hoạt") + trạng thái + hủy phần chờ xác nhận.
 * Giai đoạn 3 (app Flutter gửi mã 6 số) nằm ở EnrollmentConfirmControllerMobile.
 */
@Slf4j
@RestController
@RequestMapping("/web/admin")
public class EnrollmentController {

    private final EnrollmentServiceImpl enrollmentService;
    private final AdminJwtTokenProvider adminJwtTokenProvider;
    private final AdminTokenServiceImpl adminTokenService;
    private final AuthenticationRateLimiter rateLimiter;

    public EnrollmentController(EnrollmentServiceImpl enrollmentService,
                                AdminJwtTokenProvider adminJwtTokenProvider,
                                AdminTokenServiceImpl adminTokenService,
                                AuthenticationRateLimiter rateLimiter) {
        this.enrollmentService = enrollmentService;
        this.adminJwtTokenProvider = adminJwtTokenProvider;
        this.adminTokenService = adminTokenService;
        this.rateLimiter = rateLimiter;
    }

    // ===================== KÍCH HOẠT: GHI KHÓA XUỐNG MARIADB =====================
    // POST /api/web/admin/2fa/enrollment/activate     Header: Authorization: Bearer <accessToken>
    // Body: { "generation": "UUID", "periodSeconds": 30 }; UUID phải khớp phiên đã hiển thị.
    // 201: { success, message, data: { adminId, credentialId, algorithm, digits, periodSeconds,
    //                                  totpEnabled:false, expiresInSeconds:600 } }
    // 401 token sai | 404 không có admin | 409 đã bật 2FA | 410 khóa chờ ở Redis hết hạn | 500
    @PostMapping("/2fa/enrollment/activate")
    public ResponseEntity<?> activateEnrollment(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) Map<String, Object> requestBody,
            HttpServletRequest request) {

        Long adminId = resolveAdminId(authHeader);
        if (adminId == null) {
            return unauthorized();
        }

        Object generationValue = requestBody == null ? null : requestBody.get("generation");
        String generation = generationValue instanceof String text ? text : null;
        if (!EnrollmentGenerationStore.validGeneration(generation)) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED).body(errorBody("Valid generation required; update the client."));
        }
        Integer periodSeconds = toInteger(requestBody == null ? null : requestBody.get("periodSeconds"));
        if (periodSeconds != null && !EnrollSessionServiceImpl.isPeriodAllowed(periodSeconds)) {
            return ResponseEntity.badRequest().body(errorBody("periodSeconds must be 15, 30, 45 or 60."));
        }

        try {
            ActivationResult result = enrollmentService.activate(
                    adminId, generation, periodSeconds, request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT));

            Map<String, Object> data = new HashMap<>();
            data.put("adminId", result.adminId());
            data.put("credentialId", result.credentialId());
            data.put("algorithm", result.algorithm());
            data.put("digits", result.digits());
            data.put("periodSeconds", result.periodSeconds());
            data.put("expiresInSeconds", result.expiresInSeconds());
            data.put("totpEnabled", false);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(successBody("2FA enrollment saved. Waiting for confirmation from the Authenticator app.", data));

        } catch (EnrollmentGenerationStore.BusyException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody("An enrollment is already pending."));
        } catch (EnrollmentExpiredException e) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .body(errorBody("Phiên đã hết hạn, hãy tạo lại khóa."));
        } catch (AdminNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody("Admin not found."));
        } catch (TotpAlreadyEnabledException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody("2FA is already enabled."));
        } catch (org.springframework.dao.DataAccessException e) {
            log.warn("Enrollment database unavailable for admin {}", adminId, e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorBody("Enrollment temporarily unavailable."));
        } catch (Exception e) {
            log.error("Activate enrollment failed for admin {}", adminId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorBody("Can't activate 2FA enrollment."));
        }
    }

    /** Resolve only the claimed generation shown to this authenticated web client. */
    @PostMapping("/2fa/enrollment/reconcile")
    public ResponseEntity<?> reconcileEnrollment(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) Map<String, Object> requestBody,
            HttpServletRequest request) {
        Long adminId = resolveAdminId(authHeader);
        if (adminId == null) return unauthorized();
        Object value = requestBody == null ? null : requestBody.get("generation");
        String generation = value instanceof String text ? text : null;
        if (!EnrollmentGenerationStore.validGeneration(generation)) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED).body(errorBody("Valid generation required."));
        }
        long wait = rateLimiter.reserve("enrollment-reconcile", adminId, 10, Duration.ofMinutes(5));
        if (wait > 0) return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(wait)).body(errorBody("Too many attempts. Try again later."));
        try {
            EnrollmentServiceImpl.ReconcileResult result = enrollmentService.reconcileClaim(adminId, generation,
                    request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT));
            return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(successBody("Enrollment operation reconciled; reload current 2FA status.",
                            Map.of("outcome", result.outcome(), "claimReleased", result.claimReleased())));
        } catch (AdminNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody("Admin not found."));
        } catch (Exception e) {
            log.warn("Enrollment reconciliation unavailable for admin {}", adminId, e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(errorBody("Cannot reconcile enrollment; retry later."));
        }
    }

    // ===================== TRẠNG THÁI 2FA (trang thông tin admin + polling) =====================
    // GET /api/web/admin/2fa/status     Header: Authorization: Bearer <accessToken>
    // 200: { success, message, data: { totpEnabled, pending, expiresInSeconds, periodSeconds } }
    //   chưa cài        -> totpEnabled=false, pending=false
    //   chờ xác nhận    -> totpEnabled=false, pending=true,  expiresInSeconds = giây còn lại (<=600)
    //   đã bật hoàn toàn-> totpEnabled=true,  pending=false
    // Vue gọi khi vào trang, và cứ ~3 giây khi pending=true để biết mobile đã xác nhận chưa.
    @GetMapping("/2fa/status")
    public ResponseEntity<?> getStatus(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        Long adminId = resolveAdminId(authHeader);
        if (adminId == null) {
            return unauthorized();
        }
        try {
            TwoFaStatus status = enrollmentService.getStatus(adminId);

            Map<String, Object> data = new HashMap<>();
            data.put("totpEnabled", status.totpEnabled());
            data.put("pending", status.pending());
            data.put("expiresInSeconds", status.expiresInSeconds());
            data.put("periodSeconds", status.periodSeconds());
            data.put("credentialId", status.credentialId());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(successBody("2FA status.", data));
        } catch (AdminNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody("Admin not found."));
        } catch (Exception e) {
            log.error("Get 2FA status failed for admin {}", adminId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorBody("Can't read 2FA status."));
        }
    }

    // ===================== HỦY VÀ CÀI LẠI (nút "Hủy và cài lại") =====================
    // DELETE /api/web/admin/2fa/enrollment/pending     Header: Authorization: Bearer <accessToken>
    // Xóa credential chưa xác nhận của lần cài dở. KHÔNG đụng 2FA đã bật.
    // Idempotent: luôn 200 nếu token hợp lệ.
    @DeleteMapping("/2fa/enrollment/pending")
    public ResponseEntity<?> cancelPending(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "credentialId", required = false) Long credentialId,
            HttpServletRequest request) {

        Long adminId = resolveAdminId(authHeader);
        if (adminId == null) {
            return unauthorized();
        }
        // Legacy clients without a generation must update; silently deleting the current
        // enrollment would allow an old request to cancel a newer one.
        if (credentialId == null || credentialId <= 0) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                    .body(errorBody("credentialId is required; update the client and reload 2FA status."));
        }
        try {
            boolean cancelled = enrollmentService.cancelPending(
                    adminId, credentialId, request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT));
            if (!cancelled) return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(errorBody("Enrollment changed; reload 2FA status."));
            return ResponseEntity.ok(successBody("Pending enrollment removed.", Map.of("cancelled", true, "credentialId", credentialId)));
        } catch (AdminNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody("Admin not found."));
        } catch (Exception e) {
            log.error("Cancel pending enrollment failed for admin {}", adminId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorBody("Can't cancel pending enrollment."));
        }
    }

    // ===================== HÀM HỖ TRỢ =====================

    private Long resolveAdminId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        String token = authHeader.substring(7);
        AuthSessionStore.Identity identity = adminJwtTokenProvider.parseIdentity(token, "ACCESS");
        if (identity == null || identity.client() != AuthSessionStore.Client.WEB) return null;
        try {
            return adminTokenService.findAdminAccessTokenByJwtToken(token) ? identity.adminId() : null;
        } catch (RuntimeException e) {
            // A store/account outage must not be mistaken for a revoked token.
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Authentication temporarily unavailable", e);
        }
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(errorBody("Token is missing, invalid or expired."));
    }

    private Map<String, Object> successBody(String message, Object data) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("message", message);
        body.put("data", data);
        return body;
    }

    private Map<String, Object> errorBody(String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);
        body.put("data", null);
        return body;
    }
}