package com.example.be_web_admin.service.jpa.impl;

import com.example.be_web_admin.model.redis.AdminAccessToken;
import com.example.be_web_admin.model.redis.AdminRefreshToken;
import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.model.jpa.Course;
import com.example.be_web_admin.repository.jpa.CourseRepository;
import com.example.be_web_admin.security.jwt.user.AdminJwtToken;


import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.example.be_web_admin.service.redis.impl.AdminTokenServiceImpl;
import com.example.be_web_admin.service.jpa.AdminService;
import io.lettuce.core.RedisBusyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//Gradle cho security
// Spring Boot Starter Security (Bao gồm BCryptPasswordEncoder)
//implementation("org.springframework.boot:spring-boot-starter-security")
//
//        // Thư viện hỗ trợ xử lý JWT (jjwt) tương thích Java 21 và Spring Boot 3
//        implementation("io.jsonwebtoken:jjwt-api:0.12.5")
//        runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
//        runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")
//
//        // Test dependencies cho Security (nếu cần viết Unit Test)
//        testImplementation("org.springframework.security:spring-security-test")

@Service
public class AdminServiceImpl implements AdminService {

    private final AdminRepository adminRepository;

    private final CourseRepository courseRepository;

    private final AdminTokenServiceImpl adminTokenService;

    @Autowired
    private AdminJwtToken adminJwtToken;

//    @Autowired
//    private RedisTemplate<String, String> redisTemplate;

    public AdminServiceImpl(AdminRepository adminRepository, CourseRepository courseRepository, AdminTokenServiceImpl adminTokenService) {
        this.adminRepository = adminRepository;
        this.courseRepository = courseRepository;
        this.adminTokenService = adminTokenService;
    }

    @Override
    public boolean addAdmin(Admin admin, Long courseId) throws JpaSystemException {
        Course courseFound = courseRepository.findCourseByCourseId(courseId);
        if(courseFound != null) {

            courseFound.getAdmins().add(admin);
            admin.setCourse(courseFound);

            adminRepository.save(admin);
            return true;
        }
        return false;
    }

    @Override
    public Object loginAdmin(Long adminId, String password) throws RedisBusyException, JpaSystemException {
        // 1. Tìm sinh viên theo ID
        Admin adminFound = adminRepository.findAdminByAdminId(adminId);
        if (adminFound == null) {
            Map<String, Object> responseNotFound = new HashMap<>();
            responseNotFound.put("success", false);
            responseNotFound.put("message", "Admin Id or password is wrong.");
            responseNotFound.put("data", null);
            return responseNotFound;
        }

        // 2. Kiểm tra mật khẩu chuẩn bảo mật BCrypt
        int strengthPassword = 12;
        boolean isMatch = comparePasswordBcrypt(password, adminFound.getPassword(), strengthPassword);
        if (!isMatch) {
            Map<String, Object> responsePasswordNotMatched = new HashMap<>();
            responsePasswordNotMatched.put("success", false);
            responsePasswordNotMatched.put("message", "Admin Id or password is wrong.");
            responsePasswordNotMatched.put("data", null);
            return responsePasswordNotMatched;
        }

        // 3. Tạo cặp JWT Token RIÊNG BIỆT (Access Token & Refresh Token)
        // - Access Token (5 phút): Dùng để xác thực request thông thường.
        // - Refresh Token (30 ngày): Dùng để xin cấp lại Access Token mới.
        String accessToken = adminJwtToken.generateAccessToken(adminFound.getAdminId());
        String refreshToken = adminJwtToken.generateRefreshToken(adminFound.getAdminId());

        LocalDateTime now = LocalDateTime.now();

        // 4. Lưu Access Token vào Cache (nếu hệ thống cũ của bạn yêu cầu lưu cả Access Token)
        AdminAccessToken adminAccessToken = new AdminAccessToken();
        adminAccessToken.setJwtToken(accessToken); // Dùng đúng accessToken
        adminAccessToken.setAdminId(adminId);
        adminAccessToken.setDateLogin(now);

        Map<String, Object> responseSuccess = new HashMap<>();

        // Lưu Access Token thành công -> Tiến hành lưu Refresh Token
        if (adminTokenService.addAdminAccessToken(adminAccessToken)) {
            AdminRefreshToken adminRefreshToken = new AdminRefreshToken();
            adminRefreshToken.setJwtToken(refreshToken); // Dùng đúng refreshToken (KHÔNG dùng chung accessToken)
            adminRefreshToken.setAdminId(adminId);
            adminRefreshToken.setDateLogin(now);

            // Lưu Refresh Token thành công -> Trả về kết quả cho FE
            if (adminTokenService.addAdminRefreshToken(adminRefreshToken)) {
                responseSuccess.put("success", true);
                responseSuccess.put("message", "Login successfully"); // Sửa lại chính tả "message"

                Map<String, Object> responseSuccessData = new HashMap<>();
                responseSuccessData.put("adminId", adminAccessToken.getAdminId());
                responseSuccessData.put("adminName", adminFound.getAdminName());
                responseSuccessData.put("accessToken", accessToken);   // Trả về Access Token
                responseSuccessData.put("refreshToken", refreshToken); // Trả thêm Refresh Token cho FE lưu trữ

                responseSuccess.put("data", responseSuccessData);
            }
        }
        return responseSuccess;
    }

    @Override
    public Map<String, Object> getAdminInfo(Long adminId) throws JpaSystemException {
        Map<String, Object> responseData = new HashMap<>();
        Admin adminFound = adminRepository.findAdminByAdminId(adminId);
        if (adminFound != null) {
            responseData.put("adminId", adminFound.getAdminId());
            responseData.put("adminName", adminFound.getAdminName());
            responseData.put("gender", adminFound.getGender());
            responseData.put("dateOfBirthAdmin", adminFound.getDateOfBirthAdmin());
        }
        return responseData;
    }

    @Override
    public Map<String, Object> getAdminDetailInformation(Long adminId) throws JpaSystemException {
        Map<String, Object> responseData = new HashMap<>();
        Admin adminFound = adminRepository.findAdminByAdminId(adminId);
        if (adminFound != null) {
            responseData.put("adminId", adminFound.getAdminId());
            responseData.put("address", adminFound.getAddress());
            responseData.put("idCard", adminFound.getIdCard());
            responseData.put("industry", adminFound.getCourse().getIndustry());
            responseData.put("faculty", adminFound.getCourse().getFaculty());
            responseData.put("trainingSystem", adminFound.getCourse().getTrainingSystem());
            responseData.put("typeOfTraining", adminFound.getCourse().getTypeOfTraining());
            responseData.put("major", adminFound.getCourse().getMajor());
            responseData.put("branch", adminFound.getCourse().getBranch());
        }
        return responseData;
    }

    @Override
    public List<Admin> getAllAdmins() throws JpaSystemException {
        return adminRepository.findAll();
    }

    @Override
    public String getAdminNameByAdminId(Long adminId) throws JpaSystemException {
        Admin adminFound = adminRepository.findAdminByAdminId(adminId);
        if (adminFound != null) {
            return adminFound.getAdminName();
        }
        return null;
    }

//    public String generateHash(String rawText, int strength) {
//        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(strength);
//        return encoder.encode(rawText);
//    }

    public boolean comparePasswordBcrypt(String rawText, String hashedPassword, int strength) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(strength);
        return encoder.matches(rawText, hashedPassword);
    }
}
