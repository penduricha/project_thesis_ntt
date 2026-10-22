package com.example.be_web_students.controller;

import com.example.be_web_students.dto.LoginRequestStudentDTO;
import com.example.be_web_students.security.jwt.JwtTokenProviderStudent;
import com.example.be_web_students.service.cached.impl.StudentTokenServiceImpl;
import com.example.be_web_students.service.jpa.impl.StudentServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/student")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class StudentControllerManagement {

    private final StudentServiceImpl studentService;

    private final StudentTokenServiceImpl studentTokenService;

    @Autowired
    private JwtTokenProviderStudent jwtTokenProviderStudent;


    public StudentControllerManagement(StudentServiceImpl studentService, StudentTokenServiceImpl studentTokenService) {
        this.studentService = studentService;
        this.studentTokenService = studentTokenService;
    }

    //    {
    //  "studentId": 2500021772,
    //  "password": "1234"
    //}

    //Login successfully
    //        {
    //            "success": true,
    //                "message": "Đăng nhập thành công",
    //                "data": {
    //            "studentId": 2500021772,
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
    public ResponseEntity<?> loginStudent(@RequestBody Map<String, Object> studentRequestLogin) throws RuntimeException {
        Number studentIdInt = (Number) studentRequestLogin.get("studentId");
        Long studentId = studentIdInt != null ? studentIdInt.longValue() : null;
        String password = (String) studentRequestLogin.get("password");

        LoginRequestStudentDTO loginRequestStudentDTO = new LoginRequestStudentDTO();
        loginRequestStudentDTO.setStudentId(studentId);
        loginRequestStudentDTO.setPassword(password);

        // Gọi service trả về kết quả (ví dụ trả về Map, Token, hoặc DTO)
        Object result = studentService.loginStudent(
                loginRequestStudentDTO.getStudentId(),
                loginRequestStudentDTO.getPassword()
        );

        return ResponseEntity.ok(result);
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<?> logoutStudent(@RequestBody Map<String, Object> studentRequestLogout) {

        String accessToken = (String) studentRequestLogout.get("accessToken");
        String refreshToken = (String) studentRequestLogout.get("refreshToken");

//        if(accessToken == null || refreshToken == null) {
//            Map<String, Object> responseNoToken = new HashMap<>();
//            responseNoToken.put("status", 401);
//            responseNoToken.put("message", "No access token or refresh token provided.");
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(responseNoToken);
//        }
        //        if(status1 && status2) {
//            responseReturn.put("status", HttpStatus.OK.value());
//            responseReturn.put("message", "Log out successfully.");
//        } else {
//            responseReturn.put("status", 401);
//            responseReturn.put("message", "Can't remove access token or refresh token.");
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(responseReturn);
//        }
        Map<String, Object> responseReturn = new HashMap<>();

        try {
            studentTokenService.removeStudentAccessTokenByJwtToken(accessToken);
            studentTokenService.removeStudentRefreshTokenByJwtToken(refreshToken);
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
    public ResponseEntity<?> loginStudentAutoBySendAccessToken(
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
        if (!jwtTokenProviderStudent.validateToken(jwtToken)) {
            Map<String, Object> responseInvalid = new HashMap<>();
            responseInvalid.put("success", false);
            responseInvalid.put("message", "Token is invalid or expired.");
            responseInvalid.put("data", null);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(responseInvalid);
        }

        // 4. Kiểm tra sự tồn tại của Access Token trong Redis DB
        boolean isTokenExist = studentTokenService.findStudentAccessTokenByJwtToken(jwtToken);
        if (!isTokenExist) {
            Map<String, Object> responseNotFound = new HashMap<>();
            responseNotFound.put("success", false);
            responseNotFound.put("message", "Token is unexisted or revoked in Redis.");
            responseNotFound.put("data", null);
            // Trả về 401 Unauthorized khi token không tìm thấy trong Redis
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(responseNotFound);
        }

        // 5. Trích xuất studentId từ Token hợp lệ
        Long studentId = jwtTokenProviderStudent.getStudentIdFromToken(jwtToken);

        // 6. Trả về Response thành công (HTTP 200 OK)
        Map<String, Object> responseSuccess = new HashMap<>();
        responseSuccess.put("success", true);
        responseSuccess.put("message", "Auto login successfully.");
        responseSuccess.put("data", Map.of("studentId", studentId));

        return ResponseEntity.ok(responseSuccess);
    }

    @GetMapping("/auth/login/auto/refresh-token")
    public ResponseEntity<?> loginStudentAutoBySendRefreshToken(
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
        if (!jwtTokenProviderStudent.validateToken(jwtToken)) {
            Map<String, Object> responseInvalid = new HashMap<>();
            responseInvalid.put("success", false);
            responseInvalid.put("message", "Token is invalid or expired.");
            responseInvalid.put("data", null);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(responseInvalid);
        }

        // 4. Kiểm tra sự tồn tại của Access Token trong Redis DB
        boolean isTokenExist = studentTokenService.findStudentRefreshTokenByJwtToken(jwtToken);
        if (!isTokenExist) {
            Map<String, Object> responseNotFound = new HashMap<>();
            responseNotFound.put("success", false);
            responseNotFound.put("message", "Token is unexisted or revoked in Redis.");
            responseNotFound.put("data", null);
            // Trả về 401 Unauthorized khi token không tìm thấy trong Redis
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(responseNotFound);
        }

        // 5. Trích xuất studentId từ Token hợp lệ
        Long studentId = jwtTokenProviderStudent.getStudentIdFromToken(jwtToken);

        // 6. Trả về Response thành công (HTTP 200 OK)
        Map<String, Object> responseSuccess = new HashMap<>();

        // Delete refresh token old and create 2 new tokens.
        if(studentTokenService.removeStudentRefreshTokenByJwtToken(jwtToken)) {
            Map<String, Object> tokenNew = studentTokenService.createTokenStudent(studentId);
            if(!tokenNew.isEmpty()) {
                responseSuccess.put("success", true);
                responseSuccess.put("message", "Auto login successfully.");
                responseSuccess.put("data", tokenNew);
            }
        }
        return ResponseEntity.ok(responseSuccess);
    }
}
