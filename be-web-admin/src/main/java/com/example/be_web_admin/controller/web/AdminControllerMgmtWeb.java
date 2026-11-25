package com.example.be_web_admin.controller.web;

import com.example.be_web_admin.dto.AdminLoginDTO;
import com.example.be_web_admin.dto.LoginOutcome;
import com.example.be_web_admin.security.jwt.user.AdminJwtTokenProvider;
import com.example.be_web_admin.service.jpa.impl.AdminLoginServiceImpl;
import com.example.be_web_admin.service.redis.impl.AdminTokenServiceImpl;
import com.example.be_web_admin.service.jpa.impl.AdminServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/web/admin")
public class AdminControllerMgmtWeb {

    private final AdminServiceImpl adminService;

    private final AdminTokenServiceImpl adminTokenService;

    private final AdminLoginServiceImpl adminLoginService;

    @Autowired
    private AdminJwtTokenProvider adminJwtTokenProvider;


    public AdminControllerMgmtWeb(AdminServiceImpl adminService, AdminTokenServiceImpl adminTokenService, AdminLoginServiceImpl adminLoginService) {
        this.adminService = adminService;
        this.adminTokenService = adminTokenService;
        this.adminLoginService = adminLoginService;
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<?> logoutAdmin(@RequestBody Map<String, Object> adminRequestLogout) {

        String accessToken = toNonBlank(adminRequestLogout.get("accessToken"));
        String refreshToken = toNonBlank(adminRequestLogout.get("refreshToken"));

        Map<String, Object> responseReturn = new HashMap<>();

        // Khớp bản mobile: thiếu cả hai token -> 400, không phải 401 như trước.
        if (accessToken == null && refreshToken == null) {
            responseReturn.put("status", HttpStatus.BAD_REQUEST.value());
            responseReturn.put("message", "Missing access token and refresh token.");
            return ResponseEntity.badRequest().body(responseReturn);
        }

        try {
            if (accessToken != null) {
                adminTokenService.removeAdminAccessTokenByJwtToken(accessToken);
            }
            if (refreshToken != null) {
                adminTokenService.removeAdminRefreshTokenByJwtToken(refreshToken);
            }
        } catch (Exception e) {
            log.error("Exception logout web: {}", e.getMessage());
            responseReturn.put("status", 401);
            responseReturn.put("message", "Can't remove access token or refresh token.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(responseReturn);
        }

        responseReturn.put("status", HttpStatus.OK.value());
        responseReturn.put("message", "Log out successfully.");
        return ResponseEntity.ok(responseReturn);
    }

    private static String toNonBlank(Object value) {
        if (value instanceof String text) {
            String trimmed = text.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        return null;
    }

    @GetMapping("/auth/login/auto/access-token")
    public ResponseEntity<?> loginAdminAutoBySendAccessToken(
            @RequestHeader(value = "Authorization", required = false) String authHeader) throws RuntimeException {

        // 1. Kiểm tra sự tồn tại & định dạng của Header Authorization
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            Map<String, Object> responseError = new HashMap<>();
            responseError.put("success", false);
            responseError.put("message", "Wrong format Header Authorization (Bearer <token>).");
            responseError.put("data", null);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(responseError);
        }

        // 2. Lấy Token ra khỏi chuỗi "Bearer "
        String jwtToken = authHeader.substring(7);

        // 3. Verify chữ ký, hạn sử dụng và token_type == ACCESS
        if (!adminJwtTokenProvider.validateAccessToken(jwtToken)) {
            Map<String, Object> responseInvalid = new HashMap<>();
            responseInvalid.put("success", false);
            responseInvalid.put("message", "Token is invalid or expired.");
            responseInvalid.put("data", null);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(responseInvalid);
        }

        // 4. Kiểm tra sự tồn tại của Access Token trong Redis DB
        boolean isTokenExist = adminTokenService.findAdminAccessTokenByJwtToken(jwtToken);
        if (!isTokenExist) {
            Map<String, Object> responseNotFound = new HashMap<>();
            responseNotFound.put("success", false);
            responseNotFound.put("message", "Token is unexisted or revoked in Redis.");
            responseNotFound.put("data", null);
            // Trả về 401 Unauthorized khi token không tìm thấy trong Redis
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(responseNotFound);
        }

        // 5. Trích xuất adminId từ Token hợp lệ
        Long adminId = adminJwtTokenProvider.getAdminIdFromToken(jwtToken);

        // 6. Trả về Response thành công (HTTP 200 OK)
        Map<String, Object> responseSuccess = new HashMap<>();
        responseSuccess.put("success", true);
        responseSuccess.put("message", "Auto login successfully.");
        responseSuccess.put("data", Map.of("adminId", adminId));

        return ResponseEntity.ok(responseSuccess);
    }

    @GetMapping("/auth/login/auto/refresh-token")
    public ResponseEntity<?> loginAdminAutoBySendRefreshToken(
            @RequestHeader(value = "Authorization", required = false) String authHeader) throws RuntimeException {

        // 1. Kiểm tra sự tồn tại & định dạng của Header Authorization
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            Map<String, Object> responseError = new HashMap<>();
            responseError.put("success", false);
            responseError.put("message", "Wrong format Header Authorization (Bearer <token>).");
            responseError.put("data", null);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(responseError);
        }

        // 2. Lấy Token ra khỏi chuỗi "Bearer "
        String jwtToken = authHeader.substring(7);

        // 3. Verify chữ ký, hạn sử dụng và token_type == REFRESH
        if (!adminJwtTokenProvider.validateRefreshToken(jwtToken)) {
            Map<String, Object> responseInvalid = new HashMap<>();
            responseInvalid.put("success", false);
            responseInvalid.put("message", "Token is invalid or expired.");
            responseInvalid.put("data", null);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(responseInvalid);
        }

        // 4. Kiểm tra sự tồn tại của Access Token trong Redis DB
        boolean isTokenExist = adminTokenService.findAdminRefreshTokenByJwtToken(jwtToken);
        if (!isTokenExist) {
            Map<String, Object> responseNotFound = new HashMap<>();
            responseNotFound.put("success", false);
            responseNotFound.put("message", "Token is unexisted or revoked in Redis.");
            responseNotFound.put("data", null);
            // Trả về 401 Unauthorized khi token không tìm thấy trong Redis
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(responseNotFound);
        }

        // 5. Trích xuất adminId từ Token hợp lệ
        Long adminId = adminJwtTokenProvider.getAdminIdFromToken(jwtToken);

        // 6. Xoay vòng token: xóa refresh token cũ, tạo cặp token mới.
        //    Lỗi ở bước nào cũng trả 401 kèm thông báo (giống bản mobile). Bản cũ trả 200 với thân rỗng,
        //    khiến Vue đọc data.accessToken bị TypeError rồi xóa phiên đăng nhập oan.
        if (!adminTokenService.removeAdminRefreshTokenByJwtToken(jwtToken)) {
            Map<String, Object> responseRemoveFailed = new HashMap<>();
            responseRemoveFailed.put("success", false);
            responseRemoveFailed.put("message", "Can't remove old refresh token.");
            responseRemoveFailed.put("data", null);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(responseRemoveFailed);
        }

        Map<String, Object> tokenNew = adminTokenService.createTokenAdmin(adminId);
        if (tokenNew == null || tokenNew.isEmpty()) {
            Map<String, Object> responseCreateFailed = new HashMap<>();
            responseCreateFailed.put("success", false);
            responseCreateFailed.put("message", "Can't create new tokens.");
            responseCreateFailed.put("data", null);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(responseCreateFailed);
        }

        // 7. Trả về Response thành công (HTTP 200 OK)
        Map<String, Object> responseSuccess = new HashMap<>();
        responseSuccess.put("success", true);
        responseSuccess.put("message", "Auto login successfully.");
        responseSuccess.put("data", tokenNew);
        return ResponseEntity.ok(responseSuccess);
    }

    @GetMapping("/information/basic")
    public ResponseEntity<?> getAdminInformationBasic(@RequestParam(required = false) Long adminId) throws RuntimeException {
        Long authAdminId = resolveAuthenticatedAdminId();
        if (authAdminId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", HttpStatus.UNAUTHORIZED.value(), "message", "Unauthorized", "data", Map.of()));
        }
        if (adminId != null && !adminId.equals(authAdminId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("status", HttpStatus.FORBIDDEN.value(), "message", "Forbidden", "data", Map.of()));
        }

        Map<String, Object> responseHttp = new HashMap<>();
        Map<String, Object> responseData = adminService.getAdminInfo(authAdminId);

        responseHttp.put("status", HttpStatus.OK.value());

        if (responseData.isEmpty()) {
            responseHttp.put("message", "Admin not found.");
        } else {
            responseHttp.put("message", "Admin found successfully.");
        }
        responseHttp.put("data", responseData);
        return ResponseEntity.status(HttpStatus.OK).body(responseHttp);
    }

    @GetMapping("/information/detail")
    public ResponseEntity<?> getAdminInformationDetail(@RequestParam(required = false) Long adminId) throws RuntimeException {
        Long authAdminId = resolveAuthenticatedAdminId();
        if (authAdminId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", HttpStatus.UNAUTHORIZED.value(), "message", "Unauthorized", "data", Map.of()));
        }
        if (adminId != null && !adminId.equals(authAdminId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("status", HttpStatus.FORBIDDEN.value(), "message", "Forbidden", "data", Map.of()));
        }

        Map<String, Object> responseHttp = new HashMap<>();
        Map<String, Object> responseData = adminService.getAdminDetailInformation(authAdminId);

        responseHttp.put("status", HttpStatus.OK.value());

        if (responseData.isEmpty()) {
            responseHttp.put("message", "Admin not found.");
        } else {
            responseHttp.put("message", "Admin found successfully.");
        }
        responseHttp.put("data", responseData);
        return ResponseEntity.status(HttpStatus.OK).body(responseHttp);
    }

    private Long resolveAuthenticatedAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long id) {
            return id;
        }
        return null;
    }

    // Đăng nhập 1 request: adminId + password + totpCode (totpCode có thể trống).
    //  - Chưa bật 2FA                -> totpCode để trống vẫn success=true kèm token
    //  - Đã bật 2FA, thiếu mã        -> success=false, data.code="TOTP_REQUIRED"
    //  - Đã bật 2FA, mã sai/hết hạn  -> success=false, data.code="TOTP_INVALID"
    //  - Nhập sai quá nhiều lần      -> success=false, data.code="LOCKED", data.retryAfterSeconds
    @PostMapping({"/auth/login", "/auth/login/totp"}) // /auth/login/totp giữ lại cho client cũ
    public ResponseEntity<?> loginAdmin(@RequestBody AdminLoginDTO req, HttpServletRequest http) {
        LoginOutcome outcome = adminLoginService.login(req, http.getRemoteAddr(), http.getHeader("User-Agent"));
        return ResponseEntity.ok(buildLoginResponse(outcome, req.getAdminId()));
    }

    private Map<String, Object> buildLoginResponse(LoginOutcome outcome, Long adminId) {
        Map<String, Object> body = new HashMap<>();
        switch (outcome.status()) {
            case OK -> {
                body.put("success", true);
                body.put("message", "Đăng nhập thành công");
                body.put("data", Map.of(
                        "adminId", adminId,
                        "accessToken", outcome.accessToken(),
                        "refreshToken", outcome.refreshToken()));
            }
            case TOTP_REQUIRED -> {
                body.put("success", false);
                body.put("message", "Tài khoản đã bật xác thực 2 lớp, cần mã 6 số.");
                body.put("data", Map.of("code", "TOTP_REQUIRED"));
            }
            case TOTP_INVALID -> {
                body.put("success", false);
                body.put("message", "Mã xác thực không đúng hoặc đã hết hạn.");
                body.put("data", Map.of("code", "TOTP_INVALID"));
            }
            case LOCKED -> {
                body.put("success", false);
                // Câu chữ không nói "mã" vì khoá có thể do sai MẬT KHẨU (bộ đếm mật khẩu) chứ không
                // chỉ do sai mã TOTP — nói sai lý do sẽ khiến người dùng đi tìm lỗi ở ô mã.
                body.put("message", "Bạn đã nhập sai quá nhiều lần. Vui lòng thử lại sau.");
                body.put("data", Map.of("code", "LOCKED", "retryAfterSeconds", outcome.retryAfterSeconds()));
            }
            default -> { // BAD_CREDENTIALS
                body.put("success", false);
                body.put("message", "Mã quản trị viên hoặc mật khẩu không chính xác");
                body.put("data", null);
            }
        }
        return body;
    }
}
