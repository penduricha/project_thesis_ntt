package com.example.be_web_students.repository.cached;


import com.example.be_web_students.model.cached.StudentRefreshToken;
import org.springframework.data.repository.CrudRepository;

public interface StudentRefreshTokenRepository extends CrudRepository<StudentRefreshToken, Long> {
    StudentRefreshToken findStudentRefreshTokenByJwtToken(String jwtToken);
}
