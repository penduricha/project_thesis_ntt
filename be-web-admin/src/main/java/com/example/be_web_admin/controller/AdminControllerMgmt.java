package com.example.be_web_admin.controller;

import com.example.be_web_admin.dto.AdminLoginDTO;
import com.example.be_web_admin.security.jwt.AdminJwtTokenProvider;
import com.example.be_web_admin.service.cached.impl.AdminTokenServiceImpl;
import com.example.be_web_admin.service.jpa.impl.AdminServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AdminControllerMgmt {

    private final AdminServiceImpl adminService;

    private final AdminTokenServiceImpl adminTokenService;

    @Autowired
    private AdminJwtTokenProvider adminJwtTokenProvider;


    public AdminControllerMgmt(AdminServiceImpl adminService, AdminTokenServiceImpl adminTokenService) {
        this.adminService = adminService;
        this.adminTokenService = adminTokenService;
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
    @PostMapping("/auth/login")
    public ResponseEntity<?> loginAdmin(@RequestBody Map<String, Object> adminRequestLogin) throws RuntimeException {
        Number adminIdInt = (Number) adminRequestLogin.get("adminId");
        Long adminId = adminIdInt != null ? adminIdInt.longValue() : null;
        String password = (String) adminRequestLogin.get("password");

        AdminLoginDTO loginRequestAdminDTO = new AdminLoginDTO();
        loginRequestAdminDTO.setAdminId(adminId);
        loginRequestAdminDTO.setPassword(password);

        // Gọi service trả về kết quả (ví dụ trả về Map, Token, hoặc DTO)
        Object result = adminService.loginAdmin(
                loginRequestAdminDTO.getAdminId(),
                loginRequestAdminDTO.getPassword()
        );

        return ResponseEntity.ok(result);
    }

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
}
