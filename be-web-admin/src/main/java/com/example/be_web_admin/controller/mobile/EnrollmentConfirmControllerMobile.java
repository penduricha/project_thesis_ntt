package com.example.be_web_admin.controller.mobile;

import com.example.be_web_admin.security.jwt.user.AdminJwtTokenProvider;
import com.example.be_web_admin.security.AuthSessionStore;
import com.example.be_web_admin.service.redis.impl.AdminTokenServiceImpl;
import com.example.be_web_admin.service.redis.impl.EnrollmentConfirmServiceImpl;
import com.example.be_web_admin.service.redis.impl.EnrollmentConfirmServiceImpl.ConfirmExpiredException;
import com.example.be_web_admin.service.redis.impl.EnrollmentConfirmServiceImpl.NoPendingEnrollmentException;
import com.example.be_web_admin.service.redis.impl.EnrollmentConfirmServiceImpl.PendingParams;
import com.example.be_web_admin.service.redis.impl.TooManyAttemptsException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Giai đoạn 3 của cài 2FA (app Flutter). Gọi SAU KHI web đã bấm "Kích hoạt" (giao diện màu vàng "Đang chờ xác nhận").
 * App mobile gọi qua HTTP nên không cần @CrossOrigin (CORS chỉ áp dụng cho trình duyệt).
 */
@Slf4j
@RestController
@RequestMapping("/mobile/admin/2fa/enrollment")
public class EnrollmentConfirmControllerMobile {

    private final EnrollmentConfirmServiceImpl confirmService;
    private final AdminJwtTokenProvider adminJwtTokenProvider;
    private final AdminTokenServiceImpl adminTokenService;

    public EnrollmentConfirmControllerMobile(EnrollmentConfirmServiceImpl confirmService,
                                             AdminJwtTokenProvider adminJwtTokenProvider,
                                             AdminTokenServiceImpl adminTokenService) {
        this.confirmService = confirmService;
        this.adminJwtTokenProvider = adminJwtTokenProvider;
        this.adminTokenService = adminTokenService;
    }

    // GET /api/mobile/admin/2fa/enrollment/pending      Header: Authorization: Bearer <accessToken>
    // 200: { success, message, data: { algorithm, digits, periodSeconds, expiresInSeconds } }   (không có khóa)
    // 401 token sai | 404: web chưa bấm "Kích hoạt" (app nên chờ và hỏi lại) | 410: quá thời hạn xác nhận | 500
    @GetMapping("/pending")
    public ResponseEntity<?> pending(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long adminId = resolveAdminId(authHeader);
        if (adminId == null) return unauthorized();
        try {
            PendingParams p = confirmService.getPendingParams(adminId);
            Map<String, Object> data = new HashMap<>();
            data.put("credentialId", p.credentialId());
            data.put("algorithm", p.algorithm());
            data.put("digits", p.digits());
            data.put("periodSeconds", p.periodSeconds());
            data.put("expiresInSeconds", p.expiresInSeconds());
            return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                    .body(body(true, "Pending enrollment found.", data));
        } catch (NoPendingEnrollmentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(false, "No pending enrollment. Activate 2FA on the web first.", null));
        } catch (ConfirmExpiredException e) {
            return ResponseEntity.status(HttpStatus.GONE).body(body(false, "Enrollment expired. Start again on the web.", null));
        } catch (Exception e) {
            log.error("Get pending enrollment failed for admin {}", adminId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body(false, "Can't read pending enrollment.", null));
        }
    }

    // POST /api/mobile/admin/2fa/enrollment/confirm     Body: { "code": "123456" }
    // 200: 2FA bật hoàn toàn | 422: mã sai | 400: không đủ 6 số | 401 token sai
    // 404 / 410: như trên | 429: nhập sai quá số lần, phần cài dở đã bị xóa, phải cài lại trên web | 500
    @PostMapping("/confirm")
    public ResponseEntity<?> confirm(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                     @RequestBody(required = false) Map<String, Object> request,
                                     HttpServletRequest http) {
        Long adminId = resolveAdminId(authHeader);
        if (adminId == null) return unauthorized();

        Object raw = request == null ? null : request.get("code");
        String code = raw == null ? null : raw.toString().replaceAll("\\s", "");
        if (code == null || !code.matches("\\d{6}")) {
            return ResponseEntity.badRequest().body(body(false, "Code must be 6 digits.", null));
        }
        Object rawCredential = request == null ? null : request.get("credentialId");
        if (rawCredential == null) {
            // Old mobile clients cannot stage a key tied to a specific generation.
            return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                    .body(body(false, "Update the app to confirm this enrollment safely.", null));
        }
        Long credentialId;
        try {
            String value = rawCredential.toString();
            if (!value.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException();
            credentialId = Long.valueOf(value);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(body(false, "Invalid credentialId.", null));
        }
        try {
            boolean ok = confirmService.confirm(adminId, credentialId, code, http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT));
            if (!ok) {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body(body(false, "Wrong code. Check the key you entered.", null));
            }
            Map<String, Object> data = new HashMap<>();
            data.put("totpEnabled", true);
            if (credentialId != null) data.put("credentialId", credentialId);
            return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                    .body(body(true, "2FA activated.", data));
        } catch (NoPendingEnrollmentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(false, "No pending enrollment.", null));
        } catch (ConfirmExpiredException e) {
            return ResponseEntity.status(HttpStatus.GONE).body(body(false, "Enrollment expired. Start again on the web.", null));
        } catch (TooManyAttemptsException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(body(false, "Too many wrong codes. Enrollment was removed. Start again on the web.", null));
        } catch (Exception e) {
            log.error("Confirm enrollment failed for admin {}", adminId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body(false, "Can't confirm 2FA.", null));
        }
    }

    /** Reconcile a lost confirm response against the exact credential for this account. */
    @PostMapping("/receipt/{credentialId}")
    public ResponseEntity<?> receipt(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                     @PathVariable Long credentialId,
                                     @RequestBody(required = false) Map<String, Object> request) {
        Long adminId = resolveAdminId(authHeader);
        if (adminId == null) return unauthorized();
        Object rawCode = request == null ? null : request.get("code");
        String code = rawCode == null ? "" : rawCode.toString();
        if (!code.matches("[0-9]{6}")) return ResponseEntity.badRequest().body(body(false, "Code must be 6 digits.", null));
        try {
            if (!confirmService.verifyConfirmedGeneration(adminId, credentialId, code)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).cacheControl(org.springframework.http.CacheControl.noStore())
                        .body(body(false, "Credential cannot be reconciled.", null));
            }
            return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                    .body(body(true, "Credential confirmed.", Map.of("totpEnabled", true, "credentialId", credentialId)));
        } catch (TooManyAttemptsException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).cacheControl(org.springframework.http.CacheControl.noStore())
                    .body(body(false, "Too many attempts. Try again later.", null));
        }
    }

    private Long resolveAdminId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        String token = authHeader.substring(7);
        AuthSessionStore.Identity identity = adminJwtTokenProvider.parseIdentity(token, "ACCESS");
        if (identity == null || identity.client() != AuthSessionStore.Client.MOBILE) return null;
        try {
            return adminTokenService.findAdminAccessTokenByJwtToken(token) ? identity.adminId() : null;
        } catch (RuntimeException e) {
            // A store/account outage must not be mistaken for a revoked token.
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Authentication temporarily unavailable", e);
        }
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body(false, "Token is missing, invalid or expired.", null));
    }

    private Map<String, Object> body(boolean success, String message, Object data) {
        Map<String, Object> b = new HashMap<>();
        b.put("success", success);
        b.put("message", message);
        b.put("data", data);
        return b;
    }
}