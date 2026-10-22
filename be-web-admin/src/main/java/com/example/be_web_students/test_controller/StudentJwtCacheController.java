package com.example.be_web_students.test_controller;

import com.example.be_web_students.model.cached.StudentAccessToken;
import com.example.be_web_students.repository.cached.StudentAccessTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/cached/student-token")
public class StudentJwtCacheController {

    private final StudentAccessTokenRepository studentAccessTokenRepository;

    @Autowired
    public StudentJwtCacheController(StudentAccessTokenRepository studentAccessTokenRepository) {
        this.studentAccessTokenRepository = studentAccessTokenRepository;
    }

    @PostMapping
    public ResponseEntity<?> postStudentJWTCached(@RequestBody Map<String, Object> studentJWTPost) throws RuntimeException {

        Number studentIdInt = (Number) studentJWTPost.get("studentId");
        Long studentId = studentIdInt != null ? studentIdInt.longValue() : null;
        String jwtToken = (String) studentJWTPost.get("jwtToken");
        LocalDateTime dateLogin = LocalDateTime.now();

        StudentAccessToken studentAccessToken = new StudentAccessToken();
        studentAccessToken.setStudentId(studentId);
        studentAccessToken.setJwtToken(jwtToken);
        studentAccessToken.setDateLogin(dateLogin);

        studentAccessTokenRepository.save(studentAccessToken);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping
    //curl -X DELETE "http://localhost:8080/api/cached?jwtToken=123"
    public ResponseEntity<?> deleteStudentJWTCached_By_JwtToken(@RequestParam String jwtToken) throws RuntimeException {

//      System.out.println("JWT Token: "+jwtToken);

        StudentAccessToken studentAccessTokenFound = studentAccessTokenRepository.findStudentAccessTokenByJwtToken(jwtToken);

//      System.out.println("Cached student: "+ studentJwtCacheFound);

        if(studentAccessTokenFound != null) {
            studentAccessTokenRepository.delete(studentAccessTokenFound);
            return ResponseEntity.ok("Đã xóa cache thành công cho JWT Token!");
        }
        return ResponseEntity.internalServerError().body("Lỗi khi xóa.");
    }
}
