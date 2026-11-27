package com.example.be_web_admin.controller.mobile;

import com.example.be_web_admin.security.AuthSessionStore;
import com.example.be_web_admin.security.SessionLogoutService;
import com.example.be_web_admin.dto.AdminLoginDTO;
import com.example.be_web_admin.dto.LoginOutcome;
import com.example.be_web_admin.enums.LoginStatus;
import com.example.be_web_admin.model.redis.SessionLoginMobile;
import com.example.be_web_admin.security.jwt.user.AdminJwtTokenProvider;
import com.example.be_web_admin.service.jpa.impl.AdminLoginServiceImpl;
import com.example.be_web_admin.service.jpa.impl.AdminServiceImpl;
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
public class AdminControllerMgmtMobile {

    private final AdminServiceImpl adminService;

    // Cùng một dịch vụ đăng nhập với Web: mật khẩu BCrypt + bắt buộc mã TOTP khi tài khoản đã bật 2FA.
    private final AdminLoginServiceImpl adminLoginService;

    private final AdminTokenServiceImpl adminTokenService;

    private final AuthSessionStore sessions;
    private final SessionLogoutService logoutService;

    private final AdminJwtTokenProvider adminJwtTokenProvider;

    public AdminControllerMgmtMobile(AdminServiceImpl adminService,
                                     AdminLoginServiceImpl adminLoginService,
                                     AdminTokenServiceImpl adminTokenService,
                                     AuthSessionStore sessions, SessionLogoutService logoutService,
                                     AdminJwtTokenProvider adminJwtTokenProvider) {
        this.adminService = adminService;
        this.adminLoginService = adminLoginService;
        this.adminTokenService = adminTokenService;
        this.sessions = sessions;
        this.logoutService = logoutService;
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
        LoginOutcome outcome = adminLoginService.loginMobile(
                loginRequest, httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"), deviceName, platform);
        if (outcome.status() == LoginStatus.MOBILE_CONFLICT) {
            Optional<SessionLoginMobile> existing = sessions.mobileInfo(adminId);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(buildAlreadyLoggedInWarning(existing.orElse(null)));
        }
        if (outcome.status() != LoginStatus.OK) return ResponseEntity.ok(buildLoginFailure(outcome));

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

        try {
            if (!logoutService.logout(accessToken, refreshToken, AuthSessionStore.Client.MOBILE)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody("Session is invalid or ended."));
            }
        } catch (Exception e) {
            log.error("Exception logout mobile: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorBody("Cannot revoke session."));
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

        // 3. Verify chữ ký, hạn sử dụng và token_type == ACCESS
        if (!adminJwtTokenProvider.validateAccessToken(jwtToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorBody("Token is invalid or expired."));
        }

        // 4. Kiểm tra sự tồn tại của Access Token trong Redis DB
        if (!adminTokenService.findAdminAccessTokenByJwtToken(jwtToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorBody("Token is unexisted or revoked in Redis."));
        }

        // 5. Trích xuất adminId từ Token hợp lệ
        AuthSessionStore.Identity identity = adminJwtTokenProvider.parseIdentity(jwtToken, "ACCESS");
        Long adminId = identity == null ? null : identity.adminId();

        if (identity == null || identity.client() != AuthSessionStore.Client.MOBILE || !sessions.touch(identity)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorBody("Mobile session is unexisted or ended."));
        }

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

        // 3. Verify chữ ký, hạn sử dụng và token_type == REFRESH
        if (!adminJwtTokenProvider.validateRefreshToken(jwtToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorBody("Token is invalid or expired."));
        }

        // 4. Kiểm tra sự tồn tại của Refresh Token trong Redis DB
        if (!adminTokenService.findAdminRefreshTokenByJwtToken(jwtToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorBody("Token is unexisted or revoked in Redis."));
        }

        // 5. Trích xuất adminId từ Token hợp lệ
        AuthSessionStore.Identity identity = adminJwtTokenProvider.parseIdentity(jwtToken, "REFRESH");
        Long adminId = identity == null ? null : identity.adminId();

        if (identity == null || identity.client() != AuthSessionStore.Client.MOBILE || !sessions.touch(identity)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorBody("Mobile session is unexisted or ended."));
        }

        // 7. Xoay vòng token: xoá refresh token cũ, tạo cặp token mới.
        if (!adminTokenService.removeAdminRefreshTokenByJwtToken(jwtToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorBody("Can't remove old refresh token."));
        }
        Map<String, Object> tokenNew = adminTokenService.createTokenAdmin(identity);
        if (tokenNew == null || tokenNew.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorBody("Can't create new tokens."));
        }

        // 8. Gia hạn phiên mobile theo token mới

        Map<String, Object> responseSuccess = new HashMap<>();
        responseSuccess.put("success", true);
        responseSuccess.put("message", "Auto login successfully.");
        responseSuccess.put("data", tokenNew);
        return ResponseEntity.ok(responseSuccess);
    }

    // ===================== TÊN QUẢN TRỊ VIÊN (menu của app) =====================
    //    GET /api/mobile/admin/information/admin-name     Header: Authorization: Bearer <accessToken>
    //    adminId lấy từ chính access token (app không gửi lên), nên không ai tra được tên của admin khác.
    //    200: { success: true, message, data: { adminName } } | 401: token sai / hết hạn / đã bị thu hồi
    //    404: không còn admin này
    //    Trước đây app gọi đường dẫn này nhưng Spring chưa có endpoint nên luôn nhận 404.
    @GetMapping("/information/admin-name")
    public ResponseEntity<?> getAdminNameMobile(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        Long adminId = resolveAdminIdFromAccessToken(authHeader);
        if (adminId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorBody("Token is missing, invalid or expired."));
        }

        String adminName = adminService.getAdminNameByAdminId(adminId);
        if (adminName == null || adminName.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody("Admin not found."));
        }

        Map<String, Object> responseSuccess = new HashMap<>();
        responseSuccess.put("success", true);
        responseSuccess.put("message", "Admin name found successfully.");
        responseSuccess.put("data", Map.of("adminName", adminName));
        return ResponseEntity.ok(responseSuccess);
    }

    // ===================== HÀM HỖ TRỢ =====================

    /** Cùng cách kiểm tra access token với các controller 2FA mobile. null = token không dùng được. */
    private Long resolveAdminIdFromAccessToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        String jwtToken = authHeader.substring(7);
        try {
            if (!adminJwtTokenProvider.validateAccessToken(jwtToken)) return null;
            if (!adminTokenService.findAdminAccessTokenByJwtToken(jwtToken)) return null;
            return adminJwtTokenProvider.getAdminIdFromToken(jwtToken);
        } catch (Exception e) {
            log.warn("Can't read adminId from access token: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> buildAlreadyLoggedInWarning(SessionLoginMobile session) {
        Map<String, Object> data = new HashMap<>();
        if (session != null) {
            data.put("deviceName", session.getDeviceName());
            data.put("platform", session.getPlatform());
            data.put("loggedInAt", session.getCreatedAt() == null ? null : session.getCreatedAt().toString());
        } else {
            data.put("deviceName", null);
            data.put("platform", null);
            data.put("loggedInAt", null);
        }

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
                // Như bản web: khoá có thể do sai mật khẩu, không chỉ do sai mã TOTP.
                body.put("message", "Quá nhiều lần thử. Vui lòng thử lại sau.");
                body.put("data", Map.of("code", "LOCKED", "retryAfterSeconds", outcome.retryAfterSeconds()));
            }
            default -> { // BAD_CREDENTIALS
                body.put("message", "Admin Id or password is wrong.");
                body.put("data", null);
            }
        }
        return body;
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