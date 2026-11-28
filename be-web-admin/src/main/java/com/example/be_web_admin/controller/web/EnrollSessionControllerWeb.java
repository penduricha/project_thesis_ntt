package com.example.be_web_admin.controller.web;

import com.example.be_web_admin.security.jwt.user.AdminJwtTokenProvider;
import com.example.be_web_admin.security.AuthSessionStore;
import com.example.be_web_admin.security.EnrollmentGenerationStore;
import com.example.be_web_admin.service.redis.impl.AdminTokenServiceImpl;
import com.example.be_web_admin.service.redis.impl.EnrollSessionServiceImpl;
import com.example.be_web_admin.service.redis.impl.EnrollSessionServiceImpl.StartedEnrollment;
import com.example.be_web_admin.service.redis.impl.TotpAlreadyEnabledException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Giai đoạn 1 (Vue mở / đóng modal): sinh khóa Base32 vào Redis và hủy khóa đó.
 * Vue: EnrollSessionService.js -> EnrollSessionRepository.js -> ModalActivate2FA.vue
 */
@Slf4j
@RestController
@RequestMapping("/web/admin")
public class EnrollSessionControllerWeb {

    private final EnrollSessionServiceImpl enrollSessionService;
    private final AdminJwtTokenProvider adminJwtTokenProvider;
    private final AdminTokenServiceImpl adminTokenService;

    public EnrollSessionControllerWeb(EnrollSessionServiceImpl enrollSessionService,
                                      AdminJwtTokenProvider adminJwtTokenProvider,
                                      AdminTokenServiceImpl adminTokenService) {
        this.enrollSessionService = enrollSessionService;
        this.adminJwtTokenProvider = adminJwtTokenProvider;
        this.adminTokenService = adminTokenService;
    }

    // POST /api/web/admin/2fa/enrollment     Header: Authorization: Bearer <accessToken>
    // Body (không bắt buộc): { "periodSeconds": 30 }   (15 | 30 | 45 | 60, mặc định 30)
    // 201: { success, message, data: { secretKey, periodSeconds, expiresInSeconds, generation } }
    // 400 period sai | 401 token sai | 409 admin đã bật 2FA
    @PostMapping("/2fa/enrollment")
    public ResponseEntity<?> startEnrollment(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) Map<String, Object> request) {

        Long adminId = resolveAdminId(authHeader);
        if (adminId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorBody("Token is missing, invalid or expired."));
        }

        Integer periodSeconds = toInteger(request == null ? null : request.get("periodSeconds"));
        if (periodSeconds != null && !enrollSessionService.isAllowedPeriod(periodSeconds)) {
            return ResponseEntity.badRequest()
                    .body(errorBody("periodSeconds must be 15, 30, 45 or 60."));
        }

        try {
            StartedEnrollment started = enrollSessionService.startEnrollment(adminId, periodSeconds);

            Map<String, Object> data = new HashMap<>();
            data.put("secretKey", started.secretKey());
            data.put("generation", started.generation());
            data.put("periodSeconds", started.periodSeconds());
            data.put("expiresInSeconds", started.expiresInSeconds());

            // Phản hồi chứa khóa bí mật: cấm trình duyệt / proxy lưu cache.
            return ResponseEntity.status(HttpStatus.CREATED)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(successBody("Secret key generated.", data));

        } catch (EnrollmentGenerationStore.BusyException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody("Enrollment is being processed."));
        } catch (TotpAlreadyEnabledException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(errorBody("2FA is already enabled."));
        } catch (org.springframework.dao.DataAccessException e) {
            log.warn("Enrollment database unavailable for admin {}", adminId);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(errorBody("Enrollment temporarily unavailable."));
        } catch (Exception e) {
            log.error("Start enrollment failed for admin {}", adminId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorBody("Can't generate secret key."));
        }
    }

    // DELETE /api/web/admin/2fa/enrollment?generation=...   Header: Authorization: Bearer <accessToken>
    // Chỉ xóa đúng generation còn READY; CLAIMED trả 409. Chỉ xóa khóa chờ ở Redis;
    // phần đã ghi MariaDB (chờ xác nhận) xóa bằng DELETE /2fa/enrollment/pending ở EnrollmentController.
    @DeleteMapping("/2fa/enrollment")
    public ResponseEntity<?> cancelEnrollment(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "generation", required = false) String generation) {

        Long adminId = resolveAdminId(authHeader);
        if (adminId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorBody("Token is missing, invalid or expired."));
        }

        if (!EnrollmentGenerationStore.validGeneration(generation)) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED).body(errorBody("Valid generation required; update the client."));
        }
        try {
            boolean deleted = enrollSessionService.cancelEnrollment(adminId, generation);
            return ResponseEntity.ok(successBody(deleted ? "Enrollment cancelled." : "No matching enrollment.", Map.of("deleted", deleted)));
        } catch (EnrollmentGenerationStore.BusyException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody("Enrollment is being processed."));
        }
    }

    // ===================== HÀM HỖ TRỢ =====================

    private Long resolveAdminId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String jwtToken = authHeader.substring(7);
        AuthSessionStore.Identity identity = adminJwtTokenProvider.parseIdentity(jwtToken, "ACCESS");
        if (identity == null || identity.client() != AuthSessionStore.Client.WEB) return null;
        try {
            return adminTokenService.findAdminAccessTokenByJwtToken(jwtToken) ? identity.adminId() : null;
        } catch (RuntimeException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Authentication temporarily unavailable", e);
        }
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
}