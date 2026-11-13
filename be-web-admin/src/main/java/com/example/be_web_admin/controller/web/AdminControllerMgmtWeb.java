package com.example.be_web_admin.controller.web;

import com.example.be_web_admin.dto.AdminLoginDTO;
import com.example.be_web_admin.dto.LoginOutcome;
import com.example.be_web_admin.security.jwt.user.AdminJwtTokenProvider;
import com.example.be_web_admin.service.jpa.impl.AdminLoginServiceImpl;
import com.example.be_web_admin.service.redis.impl.AdminTokenServiceImpl;
import com.example.be_web_admin.service.jpa.impl.AdminServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/web/admin")
@CrossOrigin(origins = "*", allowedHeaders = "*")
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

    //    {
    //  "adminId": 2500021772,
    //  "password": "1234"
    //}

    //Login successfully
    //        {
    //            "success": true,
    //                "message": "Đăng nhập thành công",
    //                "data": {
    //            "adminId": 2500021772,
    //                    "jwtToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
    //        }
    //        }

    //Login fail
    //    {
    //        "success": false,
    //            "message": "Mã sinh viên hoặc mật khẩu không chính xác",
    //            "data": null
    //    }
//    @PostMapping("/auth/login")
//    public ResponseEntity<?> loginAdmin(@RequestBody Map<String, Object> adminRequestLogin) throws RuntimeException {
//        Number adminIdInt = (Number) adminRequestLogin.get("adminId");
//        Long adminId = adminIdInt != null ? adminIdInt.longValue() : null;
//        String password = (String) adminRequestLogin.get("password");
//
//        AdminLoginDTO loginRequestAdminDTO = new AdminLoginDTO();
//        loginRequestAdminDTO.setAdminId(adminId);
//        loginRequestAdminDTO.setPassword(password);
//
//        // Gọi service trả về kết quả (ví dụ trả về Map, Token, hoặc DTO)
//        Object result = adminService.loginAdmin(
//                loginRequestAdminDTO.getAdminId(),
//                loginRequestAdminDTO.getPassword()
//        );
//
//        return ResponseEntity.ok(result);
//    }

//    @PostMapping("/auth/login/mobile")
//    public ResponseEntity<?> loginAdmin(@RequestBody Map<String, Object> adminRequestLogin) throws RuntimeException {
//
//    }

    //    @PostMapping("/auth/logout/mobile")
//    public ResponseEntity<?> loginAdmin(@RequestBody Map<String, Object> adminRequestLogin) throws RuntimeException {
//
//    }


    @PostMapping("/auth/logout")
    public ResponseEntity<?> logoutAdmin(@RequestBody Map<String, Object> adminRequestLogout) {

        String accessToken = (String) adminRequestLogout.get("accessToken");
        String refreshToken = (String) adminRequestLogout.get("refreshToken");

        Map<String, Object> responseReturn = new HashMap<>();

        try {
            adminTokenService.removeAdminAccessTokenByJwtToken(accessToken);
            adminTokenService.removeAdminRefreshTokenByJwtToken(refreshToken);
        } catch(Exception e) {
            System.out.println("Exception logout:"+ e.getMessage());
            responseReturn.put("status", 401);
            responseReturn.put("message", "Can't remove access token or refresh token.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(responseReturn);
        }

        responseReturn.put("status", HttpStatus.OK.value());
        responseReturn.put("message", "Log out successfully.");
        return ResponseEntity.ok(responseReturn);
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

        // 3. Verify chữ ký & hạn sử dụng JWT Token
        if (!adminJwtTokenProvider.validateToken(jwtToken)) {
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

        // 3. Verify chữ ký & hạn sử dụng JWT Token
        if (!adminJwtTokenProvider.validateToken(jwtToken)) {
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

        // 6. Trả về Response thành công (HTTP 200 OK)
        Map<String, Object> responseSuccess = new HashMap<>();

        // Delete refresh token old and create 2 new tokens.
        if(adminTokenService.removeAdminRefreshTokenByJwtToken(jwtToken)) {
            Map<String, Object> tokenNew = adminTokenService.createTokenAdmin(adminId);
            if(!tokenNew.isEmpty()) {
                responseSuccess.put("success", true);
                responseSuccess.put("message", "Auto login successfully.");
                responseSuccess.put("data", tokenNew);
            }
        }
        return ResponseEntity.ok(responseSuccess);
    }

    @GetMapping ("/information/basic")
    //@RequestHeader(value = "Authorization", required = false) String authHeader
    public ResponseEntity<?> getAdminInformationBasic(@RequestParam Long adminId) throws RuntimeException {
        Map<String, Object> responseHttp = new HashMap<>();
        Map<String, Object> responseData = adminService.getAdminInfo(adminId);

        responseHttp.put("status", HttpStatus.OK.value());

        if(responseData.isEmpty()) {
            responseHttp.put("message", "Admin not found.");
        } else {
            responseHttp.put("message", "Admin found successfully.");
        }
        responseHttp.put("data", responseData);
        return ResponseEntity.status(HttpStatus.OK).body(responseHttp);
    }

    @GetMapping ("/information/detail")
    //@RequestHeader(value = "Authorization", required = false) String authHeader
    public ResponseEntity<?> getAdminInformationDetail(@RequestParam Long adminId) throws RuntimeException {
        Map<String, Object> responseHttp = new HashMap<>();
        Map<String, Object> responseData = adminService.getAdminDetailInformation(adminId);

        responseHttp.put("status", HttpStatus.OK.value());

        if(responseData.isEmpty()) {
            responseHttp.put("message", "Admin not found.");
        } else {
            responseHttp.put("message", "Admin found successfully.");
        }
        responseHttp.put("data", responseData);
        return ResponseEntity.status(HttpStatus.OK).body(responseHttp);
    }

//    @PostMapping("/login")   // giữ đường dẫn hiện tại: Vue gọi /admin/auth/login
//    public ResponseEntity<?> login(@RequestBody AdminLoginDTO req, HttpServletRequest http) {
//        LoginOutcome r = adminLoginService.login(req, http.getRemoteAddr(), http.getHeader("User-Agent"));
//        return switch (r.status()) {
//            case OK -> ResponseEntity.ok(Map.of("success", true,
//                    "data", Map.of("accessToken", r.accessToken(), "refreshToken", r.refreshToken())));
//            case TOTP_REQUIRED, TOTP_INVALID -> ResponseEntity.ok(Map.of("success", false,
//                    "data", Map.of("code", r.status().name())));
//            case LOCKED -> ResponseEntity.ok(Map.of("success", false,
//                    "data", Map.of("code", "LOCKED", "retryAfterSeconds", r.retryAfterSeconds())));
//            default -> ResponseEntity.ok(Map.of("success", false));   // BAD_CREDENTIALS: giữ như cũ
//        };
//    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> loginAdmin(@RequestBody AdminLoginDTO req, HttpServletRequest http) {
        req.setTotpCode(null); // endpoint này không nhận mã 6 số
        LoginOutcome outcome = adminLoginService.login(req, http.getRemoteAddr(), http.getHeader("User-Agent"));
        return ResponseEntity.ok(buildLoginResponse(outcome, req.getAdminId()));
    }

    // Bước 2 (chỉ khi bước 1 trả TOTP_REQUIRED): gửi lại kèm mã 6 số từ app Authenticator.
    //  - Mã đúng                     -> success=true kèm token
    //  - Mã sai/hết hạn/đã dùng      -> success=false, data.code="TOTP_INVALID"
    //  - Nhập sai quá nhiều lần      -> success=false, data.code="LOCKED", data.retryAfterSeconds
    @PostMapping("/auth/login/totp")
    public ResponseEntity<?> loginAdminWithTotp(@RequestBody AdminLoginDTO req, HttpServletRequest http) {
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
                body.put("message", "Nhập sai mã quá nhiều lần, vui lòng thử lại sau.");
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
