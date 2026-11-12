package com.example.be_web_admin.controller.web;

import com.example.be_web_admin.security.jwt.user.AdminJwtTokenProvider;
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

import java.util.HashMap;
import java.util.Map;

/**
 * Giai đoạn 2 (Vue bấm "Kích hoạt") + trạng thái + hủy phần chờ xác nhận.
 * Giai đoạn 3 (app Flutter gửi mã 6 số) nằm ở EnrollmentConfirmControllerMobile.
 */
@Slf4j
@RestController
@RequestMapping("/web/admin")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class EnrollmentController {

    private final EnrollmentServiceImpl enrollmentService;
    private final AdminJwtTokenProvider adminJwtTokenProvider;
    private final AdminTokenServiceImpl adminTokenService;

    public EnrollmentController(EnrollmentServiceImpl enrollmentService,
                                AdminJwtTokenProvider adminJwtTokenProvider,
                                AdminTokenServiceImpl adminTokenService) {
        this.enrollmentService = enrollmentService;
        this.adminJwtTokenProvider = adminJwtTokenProvider;
        this.adminTokenService = adminTokenService;
    }

    // ===================== KÍCH HOẠT: GHI KHÓA XUỐNG MARIADB =====================
    // POST /api/web/admin/2fa/enrollment/activate     Header: Authorization: Bearer <accessToken>
    // Body (không bắt buộc): { "periodSeconds": 30 } (15|30|45|60) = chu kỳ admin đang chọn. adminId lấy từ token.
    // 201: { success, message, data: { adminId, credentialId, algorithm, digits, periodSeconds,
    //                                  totpEnabled:false, expiresInSeconds:600, backupCodes:[10 mã] } }
    //      backupCodes chỉ hiển thị đúng một lần.
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

        Integer periodSeconds = toInteger(requestBody == null ? null : requestBody.get("periodSeconds"));
        if (periodSeconds != null && !EnrollSessionServiceImpl.isPeriodAllowed(periodSeconds)) {
            return ResponseEntity.badRequest().body(errorBody("periodSeconds must be 15, 30, 45 or 60."));
        }

        try {
            ActivationResult result = enrollmentService.activate(
                    adminId, periodSeconds, request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT));

            Map<String, Object> data = new HashMap<>();
            data.put("adminId", result.adminId());
            data.put("credentialId", result.credentialId());
            data.put("algorithm", result.algorithm());
            data.put("digits", result.digits());
            data.put("periodSeconds", result.periodSeconds());
            data.put("expiresInSeconds", result.expiresInSeconds());
            data.put("totpEnabled", false);
            data.put("backupCodes", result.backupCodes());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(successBody("2FA enrollment saved. Waiting for confirmation from the Authenticator app.", data));

        } catch (EnrollmentExpiredException e) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .body(errorBody("Phiên đã hết hạn, hãy tạo lại khóa."));
        } catch (AdminNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody("Admin not found."));
        } catch (TotpAlreadyEnabledException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody("2FA is already enabled."));
        } catch (Exception e) {
            log.error("Activate enrollment failed for admin {}", adminId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorBody("Can't activate 2FA enrollment."));
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
    // Xóa credential chưa xác nhận + mã dự phòng của lần cài dở. KHÔNG đụng 2FA đã bật.
    // Idempotent: luôn 200 nếu token hợp lệ.
    @DeleteMapping("/2fa/enrollment/pending")
    public ResponseEntity<?> cancelPending(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request) {

        Long adminId = resolveAdminId(authHeader);
        if (adminId == null) {
            return unauthorized();
        }
        try {
            enrollmentService.cancelPending(
                    adminId, request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT));
            return ResponseEntity.ok(successBody("Pending enrollment removed.", Map.of("cancelled", true)));
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
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String jwtToken = authHeader.substring(7);
        try {
            if (!adminJwtTokenProvider.validateToken(jwtToken)) {
                return null;
            }
            if (!adminTokenService.findAdminAccessTokenByJwtToken(jwtToken)) {
                return null;
            }
            return adminJwtTokenProvider.getAdminIdFromToken(jwtToken);
        } catch (Exception e) {
            log.warn("Can't resolve adminId from token: {}", e.getMessage());
            return null;
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