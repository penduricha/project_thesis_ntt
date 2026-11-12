package com.example.be_web_admin.controller.mobile;

import com.example.be_web_admin.security.jwt.user.AdminJwtTokenProvider;
import com.example.be_web_admin.service.redis.impl.AdminTokenServiceImpl;
import com.example.be_web_admin.service.redis.impl.EnrollmentConfirmServiceImpl;
import com.example.be_web_admin.service.redis.impl.EnrollmentConfirmServiceImpl.ConfirmExpiredException;
import com.example.be_web_admin.service.redis.impl.EnrollmentConfirmServiceImpl.NoPendingEnrollmentException;
import com.example.be_web_admin.service.redis.impl.EnrollmentConfirmServiceImpl.PendingParams;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/admin/mobile/2fa/enrollment")
@CrossOrigin(origins = "*", allowedHeaders = "*")
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

    // GET /api/admin/mobile/2fa/enrollment/pending      Header: Authorization: Bearer <accessToken>
    // 200: { success, message, data: { algorithm, digits, periodSeconds, expiresInSeconds } }   (không có khóa)
    // 404: web chưa bấm "Kích hoạt"  |  410: quá 10 phút
    @GetMapping("/pending")
    public ResponseEntity<?> pending(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long adminId = resolveAdminId(authHeader);
        if (adminId == null) return unauthorized();
        try {
            PendingParams p = confirmService.getPendingParams(adminId);
            Map<String, Object> data = new HashMap<>();
            data.put("algorithm", p.algorithm());
            data.put("digits", p.digits());
            data.put("periodSeconds", p.periodSeconds());
            data.put("expiresInSeconds", p.expiresInSeconds());
            return ResponseEntity.ok(body(true, "Pending enrollment found.", data));
        } catch (NoPendingEnrollmentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(false, "No pending enrollment. Activate 2FA on the web first.", null));
        } catch (ConfirmExpiredException e) {
            return ResponseEntity.status(HttpStatus.GONE).body(body(false, "Enrollment expired. Start again on the web.", null));
        }
    }

    // POST /api/admin/mobile/2fa/enrollment/confirm     Body: { "code": "123456" }
    // 200: 2FA bật hoàn toàn  |  422: mã sai  |  400: thiếu mã  |  404 / 410 như trên
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
        try {
            boolean ok = confirmService.confirm(adminId, code, http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT));
            if (!ok) {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body(body(false, "Wrong code. Check the key you entered.", null));
            }
            return ResponseEntity.ok(body(true, "2FA activated.", Map.of("totpEnabled", true)));
        } catch (NoPendingEnrollmentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(false, "No pending enrollment.", null));
        } catch (ConfirmExpiredException e) {
            return ResponseEntity.status(HttpStatus.GONE).body(body(false, "Enrollment expired. Start again on the web.", null));
        } catch (Exception e) {
            log.error("Confirm enrollment failed for admin {}", adminId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body(false, "Can't confirm 2FA.", null));
        }
    }

    private Long resolveAdminId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        String jwt = authHeader.substring(7);
        try {
            if (!adminJwtTokenProvider.validateToken(jwt)) return null;
            if (!adminTokenService.findAdminAccessTokenByJwtToken(jwt)) return null;
            return adminJwtTokenProvider.getAdminIdFromToken(jwt);
        } catch (Exception e) {
            return null;
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