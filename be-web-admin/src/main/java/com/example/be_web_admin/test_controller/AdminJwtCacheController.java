package com.example.be_web_admin.test_controller;

import com.example.be_web_admin.model.cached.AdminAccessToken;
import com.example.be_web_admin.repository.cached.AdminAccessTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/cached/admin-token")
public class AdminJwtCacheController {

    private final AdminAccessTokenRepository adminAccessTokenRepository;

    @Autowired
    public AdminJwtCacheController(AdminAccessTokenRepository adminAccessTokenRepository) {
        this.adminAccessTokenRepository = adminAccessTokenRepository;
    }

    @PostMapping
    public ResponseEntity<?> postAdminJWTCached(@RequestBody Map<String, Object> adminJWTPost) throws RuntimeException {

        Number adminIdInt = (Number) adminJWTPost.get("adminId");
        Long adminId = adminIdInt != null ? adminIdInt.longValue() : null;
        String jwtToken = (String) adminJWTPost.get("jwtToken");
        LocalDateTime dateLogin = LocalDateTime.now();

        AdminAccessToken adminAccessToken = new AdminAccessToken();
        adminAccessToken.setAdminId(adminId);
        adminAccessToken.setJwtToken(jwtToken);
        adminAccessToken.setDateLogin(dateLogin);

        adminAccessTokenRepository.save(adminAccessToken);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping
    //curl -X DELETE "http://localhost:8080/api/cached?jwtToken=123"
    public ResponseEntity<?> deleteAdminJWTCached_By_JwtToken(@RequestParam String jwtToken) throws RuntimeException {

//      System.out.println("JWT Token: "+jwtToken);

        AdminAccessToken adminAccessTokenFound = adminAccessTokenRepository.findAdminAccessTokenByJwtToken(jwtToken);

//      System.out.println("Cached admin: "+ adminJwtCacheFound);

        if(adminAccessTokenFound != null) {
            adminAccessTokenRepository.delete(adminAccessTokenFound);
            return ResponseEntity.ok("Đã xóa cache thành công cho JWT Token!");
        }
        return ResponseEntity.internalServerError().body("Lỗi khi xóa.");
    }
}
