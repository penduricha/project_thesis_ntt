package com.example.be_web_admin.controller.mobile;

import com.example.be_web_admin.security.jwt.user.AdminJwtTokenProvider;
import com.example.be_web_admin.service.redis.impl.AdminTokenServiceImpl;
import com.example.be_web_admin.service.redis.impl.EnrollmentServiceImpl.AdminNotFoundException;
import com.example.be_web_admin.service.redis.impl.TotpDisableServiceImpl;
import com.example.be_web_admin.service.redis.impl.TotpDisableServiceImpl.DisableLockedException;
import com.example.be_web_admin.service.redis.impl.TotpDisableServiceImpl.InvalidTotpCodeException;
import com.example.be_web_admin.service.redis.impl.TotpDisableServiceImpl.TotpNotEnabledException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Tắt 2FA từ app Flutter (nút xóa TOTP trong app Authenticator).
 * App mobile gọi qua HTTP nên không cần @CrossOrigin (CORS chỉ áp dụng cho trình duyệt).
 */
@Slf4j
@RestController
@RequestMapping("/admin/mobile/2fa")
public class TotpDisableControllerMobile {

    private final TotpDisableServiceImpl disableService;
    private final AdminJwtTokenProvider adminJwtTokenProvider;
    private final AdminTokenServiceImpl adminTokenService;

    public TotpDisableControllerMobile(TotpDisableServiceImpl disableService,
                                       AdminJwtTokenProvider adminJwtTokenProvider,
                                       AdminTokenServiceImpl adminTokenService) {
        this.disableService = disableService;
        this.adminJwtTokenProvider = adminJwtTokenProvider;
        this.adminTokenService = adminTokenService;
    }

    // POST /api/admin/mobile/2fa/disable     Header: Authorization: Bearer <accessToken>
    // Body: { "code": "123456" }  (mã TOTP hiện tại sinh từ khóa đang lưu trong app)
    // 200: 2FA đã tắt (đã xóa credential, totp_enabled = false) | 400: không đủ 6 số | 401 token sai
    // 404: admin chưa bật 2FA (app coi như đã tắt, được xóa khóa cục bộ)
    // 422: mã sai / hết hạn / đã dùng (data.code = TOTP_INVALID | TOTP_REPLAYED)
    // 429: nhập sai quá 5 lần, bị khóa 5 phút (data.retryAfterSeconds, header Retry-After) | 500
    @PostMapping("/disable")
    public ResponseEntity<?> disable(@RequestHeader(value = "Authorization", required = false) String authHeader,
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
            disableService.disable(adminId, code, http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(body(true, "2FA turned off.", Map.of("totpEnabled", false)));
        } catch (TotpNotEnabledException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(false, "2FA is not enabled.", null));
        } catch (InvalidTotpCodeException e) {
            String message = e.isReplayed()
                    ? "This code was just used. Wait for the next code and try again."
                    : "Wrong code.";
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(body(false, message, Map.of("code", e.isReplayed() ? "TOTP_REPLAYED" : "TOTP_INVALID")));
        } catch (DisableLockedException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                    .body(body(false, "Too many wrong codes. Try again later.",
                            Map.of("code", "LOCKED", "retryAfterSeconds", e.getRetryAfterSeconds())));
        } catch (AdminNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(false, "Admin not found.", null));
        } catch (Exception e) {
            log.error("Disable 2FA failed for admin {}", adminId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body(false, "Can't turn off 2FA.", null));
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
