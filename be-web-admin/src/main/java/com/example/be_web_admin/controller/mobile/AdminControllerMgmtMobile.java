package com.example.be_web_admin.controller.mobile;

import com.example.be_web_admin.dto.AdminLoginDTO;
import com.example.be_web_admin.dto.LoginOutcome;
import com.example.be_web_admin.enums.LoginStatus;
import com.example.be_web_admin.model.redis.SessionLoginMobile;
import com.example.be_web_admin.security.jwt.user.AdminJwtTokenProvider;
import com.example.be_web_admin.service.jpa.impl.AdminLoginServiceImpl;
import com.example.be_web_admin.service.jpa.impl.AdminServiceImpl;
import com.example.be_web_admin.service.redis.SessionLoginMobileService;
import com.example.be_web_admin.service.redis.impl.AdminTokenServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/mobile/admin")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AdminControllerMgmtMobile {

    private final AdminServiceImpl adminService;

    // Cùng một dịch vụ đăng nhập với Web: mật khẩu BCrypt + bắt buộc mã TOTP khi tài khoản đã bật 2FA.
    private final AdminLoginServiceImpl adminLoginService;

    private final AdminTokenServiceImpl adminTokenService;

    private final SessionLoginMobileService sessionLoginMobileService;

    private final AdminJwtTokenProvider adminJwtTokenProvider;

    public AdminControllerMgmtMobile(AdminServiceImpl adminService,
                                     AdminLoginServiceImpl adminLoginService,
                                     AdminTokenServiceImpl adminTokenService,
                                     SessionLoginMobileService sessionLoginMobileService,
                                     AdminJwtTokenProvider adminJwtTokenProvider) {
        this.adminService = adminService;
        this.adminLoginService = adminLoginService;
        this.adminTokenService = adminTokenService;
        this.sessionLoginMobileService = sessionLoginMobileService;
        this.adminJwtTokenProvider = adminJwtTokenProvider;
    }

    // ===================== LOGIN =====================
    //    Request:
    //    {
    //      "adminId": 2500021775,
    //      "password": "1234",
    //      "totpCode": "123456",                       (không bắt buộc; app tự sinh từ khóa lưu trên máy)
    //      "deviceName": "Samsung Galaxy S26 Ultra",   (không bắt buộc)
    //      "platform": "ANDROID"                       (không bắt buộc)
    //    }
    //
    //    Thành công (HTTP 200): giống Web, kèm cặp token.
    //    Sai mật khẩu (HTTP 200, success=false, data=null).
    //    Tài khoản đã bật 2FA (giống Web, HTTP 200, success=false, data.code):
    //      TOTP_REQUIRED (thiếu mã) | TOTP_INVALID (mã sai/hết hạn/đã dùng) | LOCKED (+ data.retryAfterSeconds)
    //
    //    Tài khoản đã đăng nhập ở thiết bị mobile khác (HTTP 409):
    //    {
    //      "success": false,
    //      "status": 409,
    //      "errorCode": "ACCOUNT_ALREADY_LOGGED_IN_ON_ANOTHER_DEVICE",
    //      "severity": "CRITICAL",
    //      "message": "Tài khoản đã đăng nhập ở một thiết bị khác.",
    //      "data": {
    //        "deviceName": "Pixel 8",
    //        "platform": "ANDROID",
    //        "loggedInAt": "2026-11-10T02:48:00Z"
    //      }
    //    }
    @PostMapping("/auth/login")
    public ResponseEntity<?> loginAdminMobile(@RequestBody Map<String, Object> adminRequestLogin,
                                              HttpServletRequest httpRequest) throws RuntimeException {
        Long adminId = toLong(adminRequestLogin.get("adminId"));
        String password = toStringOrNull(adminRequestLogin.get("password"));
        String deviceName = toStringOrNull(adminRequestLogin.get("deviceName"));
        String platform = toStringOrNull(adminRequestLogin.get("platform"));

        if (adminId == null || password == null || password.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(errorBody("Mã quản trị viên hoặc mật khẩu không được để trống."));
        }

        // 1. Xác thực mật khẩu VÀ mã TOTP (nếu tài khoản đã bật 2FA) bằng cùng dịch vụ với Web.
        //    Trước đây ở đây chỉ kiểm mật khẩu nên token cấp ra dùng được cả trên Web, bỏ qua 2FA.
        //    Chưa bật 2FA thì totpCode bị bỏ qua. Mọi lỗi xác thực đều dừng ở đây, chưa chạm tới phiên mobile,
        //    nên người chưa qua đủ các lớp xác thực không dò được trạng thái phiên.
        AdminLoginDTO loginRequest = new AdminLoginDTO(adminId, password);
        loginRequest.setTotpCode(toStringOrNull(adminRequestLogin.get("totpCode")));
        LoginOutcome outcome = adminLoginService.login(
                loginRequest, httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        if (outcome.status() != LoginStatus.OK) {
            return ResponseEntity.ok(buildLoginFailure(outcome));
        }

        // 2. Đã có phiên mobile ở thiết bị khác thì thu hồi cặp token vừa cấp và cảnh báo.
        Optional<SessionLoginMobile> existingSession = sessionLoginMobileService.findByAdminId(adminId);
        if (existingSession.isPresent()) {
            revokeIssuedTokens(outcome);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(buildAlreadyLoggedInWarning(existingSession.get()));
        }

        // 3. Chưa có phiên thì lưu phiên đăng nhập mobile.
        sessionLoginMobileService.createSession(adminId, deviceName, platform, httpRequest.getRemoteAddr());

        return ResponseEntity.ok(buildLoginSuccess(adminId, outcome));
    }

    // ===================== LOGOUT =====================
    //    Request: { "accessToken": "...", "refreshToken": "..." }
    @PostMapping("/auth/logout")
    public ResponseEntity<?> logoutAdminMobile(@RequestBody Map<String, Object> adminRequestLogout) {

        String accessToken = toStringOrNull(adminRequestLogout.get("accessToken"));
        String refreshToken = toStringOrNull(adminRequestLogout.get("refreshToken"));

        Map<String, Object> responseReturn = new HashMap<>();

        if (accessToken == null && refreshToken == null) {
            responseReturn.put("status", HttpStatus.BAD_REQUEST.value());
            responseReturn.put("message", "Missing access token and refresh token.");
            return ResponseEntity.badRequest().body(responseReturn);
        }

        // Lấy adminId trước khi xoá token (ưu tiên refresh token vì sống lâu hơn).
        Long adminId = resolveAdminId(refreshToken, accessToken);

        try {
            if (accessToken != null) {
                adminTokenService.removeAdminAccessTokenByJwtToken(accessToken);
            }
            if (refreshToken != null) {
                adminTokenService.removeAdminRefreshTokenByJwtToken(refreshToken);
            }
            // Xoá phiên đăng nhập mobile để thiết bị khác có thể đăng nhập.
            if (adminId != null) {
                sessionLoginMobileService.removeByAdminId(adminId);
            }
        } catch (Exception e) {
            log.error("Exception logout mobile: {}", e.getMessage());
            responseReturn.put("status", 401);
            responseReturn.put("message", "Can't remove access token, refresh token or mobile session.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(responseReturn);
        }

        responseReturn.put("status", HttpStatus.OK.value());
        responseReturn.put("message", "Log out successfully.");
        return ResponseEntity.ok(responseReturn);
    }

    // ===================== AUTO LOGIN BẰNG ACCESS TOKEN =====================
    @GetMapping("/auth/login/auto/access-token")
    public ResponseEntity<?> loginAdminMobileAutoBySendAccessToken(
            @RequestHeader(value = "Authorization", required = false) String authHeader) throws RuntimeException {

        // 1. Kiểm tra sự tồn tại & định dạng của Header Authorization
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorBody("Wrong format Header Authorization (Bearer <token>)."));
        }

        // 2. Lấy Token ra khỏi chuỗi "Bearer "
        String jwtToken = authHeader.substring(7);

        // 3. Verify chữ ký & hạn sử dụng JWT Token
        if (!adminJwtTokenProvider.validateToken(jwtToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorBody("Token is invalid or expired."));
        }

        // 4. Kiểm tra sự tồn tại của Access Token trong Redis DB
        if (!adminTokenService.findAdminAccessTokenByJwtToken(jwtToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorBody("Token is unexisted or revoked in Redis."));
        }

        // 5. Trích xuất adminId từ Token hợp lệ
        Long adminId = adminJwtTokenProvider.getAdminIdFromToken(jwtToken);

        // 6. Phải còn phiên đăng nhập mobile
        if (!sessionLoginMobileService.existsByAdminId(adminId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorBody("Mobile session is unexisted or ended."));
        }
        sessionLoginMobileService.touch(adminId);

        // 7. Trả về Response thành công (HTTP 200 OK)
        Map<String, Object> responseSuccess = new HashMap<>();
        responseSuccess.put("success", true);
        responseSuccess.put("message", "Auto login successfully.");
        responseSuccess.put("data", Map.of("adminId", adminId));

        return ResponseEntity.ok(responseSuccess);
    }

    // ===================== AUTO LOGIN BẰNG REFRESH TOKEN =====================
    @GetMapping("/auth/login/auto/refresh-token")
    public ResponseEntity<?> loginAdminMobileAutoBySendRefreshToken(
            @RequestHeader(value = "Authorization", required = false) String authHeader) throws RuntimeException {

        // 1. Kiểm tra sự tồn tại & định dạng của Header Authorization
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorBody("Wrong format Header Authorization (Bearer <token>)."));
        }

        // 2. Lấy Token ra khỏi chuỗi "Bearer "
        String jwtToken = authHeader.substring(7);

        // 3. Verify chữ ký & hạn sử dụng JWT Token
        if (!adminJwtTokenProvider.validateToken(jwtToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorBody("Token is invalid or expired."));
        }

        // 4. Kiểm tra sự tồn tại của Refresh Token trong Redis DB
        if (!adminTokenService.findAdminRefreshTokenByJwtToken(jwtToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorBody("Token is unexisted or revoked in Redis."));
        }

        // 5. Trích xuất adminId từ Token hợp lệ
        Long adminId = adminJwtTokenProvider.getAdminIdFromToken(jwtToken);

        // 6. Phải còn phiên đăng nhập mobile
        if (!sessionLoginMobileService.existsByAdminId(adminId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorBody("Mobile session is unexisted or ended."));
        }

        // 7. Xoay vòng token: xoá refresh token cũ, tạo cặp token mới.
        if (!adminTokenService.removeAdminRefreshTokenByJwtToken(jwtToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorBody("Can't remove old refresh token."));
        }
        Map<String, Object> tokenNew = adminTokenService.createTokenAdmin(adminId);
        if (tokenNew == null || tokenNew.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorBody("Can't create new tokens."));
        }

        // 8. Gia hạn phiên mobile theo token mới
        sessionLoginMobileService.touch(adminId);

        Map<String, Object> responseSuccess = new HashMap<>();
        responseSuccess.put("success", true);
        responseSuccess.put("message", "Auto login successfully.");
        responseSuccess.put("data", tokenNew);
        return ResponseEntity.ok(responseSuccess);
    }

    // ===================== HÀM HỖ TRỢ =====================

    private Map<String, Object> buildAlreadyLoggedInWarning(SessionLoginMobile session) {
        Map<String, Object> data = new HashMap<>();
        data.put("deviceName", session.getDeviceName());
        data.put("platform", session.getPlatform());
        data.put("loggedInAt", session.getCreatedAt() == null ? null : session.getCreatedAt().toString());

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("status", HttpStatus.CONFLICT.value());
        body.put("errorCode", "ACCOUNT_ALREADY_LOGGED_IN_ON_ANOTHER_DEVICE");
        body.put("severity", "CRITICAL");
        body.put("message", "Tài khoản đã đăng nhập ở một thiết bị khác.");
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

    /** Cùng dạng body với đăng nhập mobile cũ (có adminName) để app Flutter không phải đổi cách đọc. */
    private Map<String, Object> buildLoginSuccess(Long adminId, LoginOutcome outcome) {
        Map<String, Object> data = new HashMap<>();
        data.put("adminId", adminId);
        data.put("adminName", adminService.getAdminNameByAdminId(adminId));
        data.put("accessToken", outcome.accessToken());
        data.put("refreshToken", outcome.refreshToken());

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("message", "Login successfully");
        body.put("data", data);
        return body;
    }

    /** Giống buildLoginResponse của Web: data.code cho các lỗi 2FA, data=null cho sai mật khẩu. */
    private Map<String, Object> buildLoginFailure(LoginOutcome outcome) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        switch (outcome.status()) {
            case TOTP_REQUIRED -> {
                body.put("message", "Tài khoản đã bật xác thực 2 lớp, cần mã 6 số.");
                body.put("data", Map.of("code", "TOTP_REQUIRED"));
            }
            case TOTP_INVALID -> {
                body.put("message", "Mã xác thực không đúng hoặc đã hết hạn.");
                body.put("data", Map.of("code", "TOTP_INVALID"));
            }
            case LOCKED -> {
                body.put("message", "Nhập sai mã quá nhiều lần, vui lòng thử lại sau.");
                body.put("data", Map.of("code", "LOCKED", "retryAfterSeconds", outcome.retryAfterSeconds()));
            }
            default -> { // BAD_CREDENTIALS
                body.put("message", "Admin Id or password is wrong.");
                body.put("data", null);
            }
        }
        return body;
    }

    private void revokeIssuedTokens(LoginOutcome outcome) {
        try {
            if (outcome.accessToken() != null) {
                adminTokenService.removeAdminAccessTokenByJwtToken(outcome.accessToken());
            }
            if (outcome.refreshToken() != null) {
                adminTokenService.removeAdminRefreshTokenByJwtToken(outcome.refreshToken());
            }
        } catch (Exception e) {
            log.error("Can't revoke tokens issued for rejected mobile login: {}", e.getMessage());
        }
    }

    private Long resolveAdminId(String... jwtTokens) {
        for (String jwtToken : jwtTokens) {
            if (jwtToken == null) continue;
            try {
                if (adminJwtTokenProvider.validateToken(jwtToken)) {
                    return adminJwtTokenProvider.getAdminIdFromToken(jwtToken);
                }
            } catch (Exception e) {
                log.warn("Can't read adminId from token: {}", e.getMessage());
            }
        }
        return null;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String toStringOrNull(Object value) {
        return value instanceof String text ? text : null;
    }
}