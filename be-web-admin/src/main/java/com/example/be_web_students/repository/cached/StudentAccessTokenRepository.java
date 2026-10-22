package com.example.be_web_students.repository.cached;

import com.example.be_web_students.model.cached.StudentAccessToken;


import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
//'org.springframework.boot:spring-boot-starter-data-redis'
public interface StudentAccessTokenRepository extends CrudRepository<StudentAccessToken, Long> {

    StudentAccessToken findStudentAccessTokenByJwtToken(String jwtToken);

    //Long deleteByJwtToken(String jwtToken);

}
