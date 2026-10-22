package com.example.be_web_students.service.jpa.impl;

import com.example.be_web_students.model.cached.StudentAccessToken;
import com.example.be_web_students.model.cached.StudentRefreshToken;
import com.example.be_web_students.model.jpa.Course;
import com.example.be_web_students.model.jpa.Student;
import com.example.be_web_students.security.jwt.JwtTokenProviderStudent;


import com.example.be_web_students.repository.jpa.CourseRepository;
import com.example.be_web_students.repository.jpa.StudentRepository;
import com.example.be_web_students.service.cached.impl.StudentTokenServiceImpl;
import com.example.be_web_students.service.jpa.I_StudentService;
import io.lettuce.core.RedisBusyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
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
public class StudentServiceImpl implements I_StudentService {

    private final StudentRepository studentRepository;

    private final CourseRepository courseRepository;

    private final StudentTokenServiceImpl studentTokenService;

    @Autowired
    private JwtTokenProviderStudent jwtTokenProviderStudent;

//    @Autowired
//    private RedisTemplate<String, String> redisTemplate;

    public StudentServiceImpl(StudentRepository studentRepository, CourseRepository courseRepository, StudentTokenServiceImpl studentTokenService) {
        this.studentRepository = studentRepository;
        this.courseRepository = courseRepository;
        this.studentTokenService = studentTokenService;
    }

    @Override
    public boolean addStudent(Student student, Long courseId) throws JpaSystemException {

        Student studentFound = studentRepository.findStudentByStudentId(student.getStudentId());

        Course courseFound = courseRepository.findCourseByCourseId(courseId);

        if(studentFound == null && courseFound != null)
        {
            Course courseToMap = new Course();
            courseToMap.setCourseId(courseFound.getCourseId());

            //map relationship
            courseToMap.getStudents().add(student);
            student.setCourse(courseToMap);

            studentRepository.save(student);
            return true;
        }
        return false;
    }

    @Override
    public Object loginStudent(Long studentId, String password) throws RedisBusyException, JpaSystemException {
        // 1. Tìm sinh viên theo ID
        Student studentFound = studentRepository.findStudentByStudentId(studentId);
        if (studentFound == null) {
            Map<String, Object> responseNotFound = new HashMap<>();
            responseNotFound.put("success", false);
            responseNotFound.put("message", "Student Id or password is wrong.");
            responseNotFound.put("data", null);
            return responseNotFound;
        }

        // 2. Kiểm tra mật khẩu chuẩn bảo mật BCrypt
        int strengthPassword = 12;
        boolean isMatch = comparePasswordBcrypt(password, studentFound.getPassword(), strengthPassword);
        if (!isMatch) {
            Map<String, Object> responsePasswordNotMatched = new HashMap<>();
            responsePasswordNotMatched.put("success", false);
            responsePasswordNotMatched.put("message", "Student Id or password is wrong.");
            responsePasswordNotMatched.put("data", null);
            return responsePasswordNotMatched;
        }

        // 3. Tạo cặp JWT Token RIÊNG BIỆT (Access Token & Refresh Token)
        // - Access Token (5 phút): Dùng để xác thực request thông thường.
        // - Refresh Token (30 ngày): Dùng để xin cấp lại Access Token mới.
        String accessToken = jwtTokenProviderStudent.generateAccessToken(studentFound.getStudentId());
        String refreshToken = jwtTokenProviderStudent.generateRefreshToken(studentFound.getStudentId());

        LocalDateTime now = LocalDateTime.now();

        // 4. Lưu Access Token vào Cache (nếu hệ thống cũ của bạn yêu cầu lưu cả Access Token)
        StudentAccessToken studentAccessToken = new StudentAccessToken();
        studentAccessToken.setJwtToken(accessToken); // Dùng đúng accessToken
        studentAccessToken.setStudentId(studentId);
        studentAccessToken.setDateLogin(now);

        Map<String, Object> responseSuccess = new HashMap<>();

        // Lưu Access Token thành công -> Tiến hành lưu Refresh Token
        if (studentTokenService.addStudentAccessToken(studentAccessToken)) {
            StudentRefreshToken studentRefreshToken = new StudentRefreshToken();
            studentRefreshToken.setJwtToken(refreshToken); // Dùng đúng refreshToken (KHÔNG dùng chung accessToken)
            studentRefreshToken.setStudentId(studentId);
            studentRefreshToken.setDateLogin(now);

            // Lưu Refresh Token thành công -> Trả về kết quả cho FE
            if (studentTokenService.addStudentRefreshToken(studentRefreshToken)) {
                responseSuccess.put("success", true);
                responseSuccess.put("message", "Login successfully"); // Sửa lại chính tả "message"

                Map<String, Object> responseSuccessData = new HashMap<>();
                responseSuccessData.put("studentId", studentAccessToken.getStudentId());
                responseSuccessData.put("accessToken", accessToken);   // Trả về Access Token
                responseSuccessData.put("refreshToken", refreshToken); // Trả thêm Refresh Token cho FE lưu trữ

                responseSuccess.put("data", responseSuccessData);
            }
        }
        return responseSuccess;
    }

    @Override
    public Map<String, Object> getStudentInfo(Long studentId) throws JpaSystemException {
        Map<String, Object> responseData = new HashMap<>();
        Student studentFound = studentRepository.findStudentByStudentId(studentId);
        if (studentFound != null) {
            responseData.put("studentId", studentFound.getStudentId());
            responseData.put("studentName", studentFound.getStudentName());
            responseData.put("gender", studentFound.getGender());
            responseData.put("clazzName", studentFound.getClazzName());
            responseData.put("course", studentFound.getCourse());
        }
        return responseData;
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
