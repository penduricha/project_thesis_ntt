package com.example.be_web_students.service.cached.impl;

import com.example.be_web_students.model.cached.StudentAccessToken;
import com.example.be_web_students.model.cached.StudentRefreshToken;
import com.example.be_web_students.model.jpa.Student;
import com.example.be_web_students.repository.cached.StudentAccessTokenRepository;
import com.example.be_web_students.repository.cached.StudentRefreshTokenRepository;
import com.example.be_web_students.repository.jpa.StudentRepository;
import com.example.be_web_students.security.jwt.JwtTokenProviderStudent;
import com.example.be_web_students.service.cached.I_StudentTokenService;
import io.lettuce.core.RedisBusyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class StudentTokenServiceImpl implements I_StudentTokenService {

    private final StudentAccessTokenRepository studentAccessTokenRepository;

    private final StudentRefreshTokenRepository studentRefreshTokenRepository;

    private final StudentRepository studentRepository;

    private final JwtTokenProviderStudent jwtTokenProviderStudent;

    public StudentTokenServiceImpl(StudentAccessTokenRepository studentAccessTokenRepository, StudentRefreshTokenRepository studentRefreshTokenRepository, StudentRepository studentRepository, JwtTokenProviderStudent jwtTokenProviderStudent) {
        this.studentAccessTokenRepository = studentAccessTokenRepository;
        this.studentRefreshTokenRepository = studentRefreshTokenRepository;
        this.studentRepository = studentRepository;
        this.jwtTokenProviderStudent = jwtTokenProviderStudent;
    }

    @Override
    public boolean addStudentAccessToken(StudentAccessToken studentAccessToken) throws RedisBusyException {
        boolean studentAccessTokenFound = findStudentAccessTokenByJwtToken(studentAccessToken.getJwtToken());
        if(!studentAccessTokenFound) {
            studentAccessTokenRepository.save(studentAccessToken);
            return true;
        }
        return false;
    }

    @Override
    public boolean findStudentAccessTokenByJwtToken(String jwtToken) throws RedisBusyException {
        StudentAccessToken studentAccessTokenFound = studentAccessTokenRepository
                .findStudentAccessTokenByJwtToken(jwtToken);
        return studentAccessTokenFound != null;
    }

    @Override
    public boolean addStudentRefreshToken(StudentRefreshToken studentRefreshToken) throws RedisBusyException {
        boolean studentRefreshTokenFound = findStudentRefreshTokenByJwtToken(studentRefreshToken.getJwtToken());
        if(!studentRefreshTokenFound) {
            studentRefreshTokenRepository.save(studentRefreshToken);
            return true;
        }
        return false;
    }

    @Override
    public boolean findStudentRefreshTokenByJwtToken(String jwtToken) throws RedisBusyException {
        StudentRefreshToken studentRefreshToken = studentRefreshTokenRepository
                .findStudentRefreshTokenByJwtToken(jwtToken);
        return studentRefreshToken != null;
    }

//    String accessToken = jwtTokenProvider.generateAccessToken(studentFound.getStudentId());
//    String refreshToken = jwtTokenProvider.generateRefreshToken(studentFound.getStudentId());
    @Override
    public boolean removeStudentRefreshTokenByJwtToken(String jwtToken) throws RedisBusyException {
        StudentRefreshToken studentRefreshTokenFound = studentRefreshTokenRepository
                .findStudentRefreshTokenByJwtToken(jwtToken);
        if(studentRefreshTokenFound != null) {
            studentRefreshTokenRepository.delete(studentRefreshTokenFound);
            return true;
        }
        return false;
    }

    @Override
    public boolean removeStudentAccessTokenByJwtToken(String jwtToken) throws RedisBusyException {
        StudentAccessToken studentAccessTokenFound = studentAccessTokenRepository
                .findStudentAccessTokenByJwtToken(jwtToken);
        if(studentAccessTokenFound != null) {
            studentAccessTokenRepository.delete(studentAccessTokenFound);
            return true;
        }
        return false;
    }

    @Override
    public Map<String, Object> createTokenStudent(Long studentId) throws RedisBusyException {
        Map<String, Object> dataResult = new HashMap<>();

        Student studentFound = studentRepository.findStudentByStudentId(studentId);

        if(studentFound != null) {
            String accessToken = jwtTokenProviderStudent.generateAccessToken(studentId);
            String refreshToken = jwtTokenProviderStudent.generateRefreshToken(studentId);
            LocalDateTime now = LocalDateTime.now();

            StudentAccessToken studentAccessToken = new StudentAccessToken();
            studentAccessToken.setJwtToken(accessToken);
            studentAccessToken.setStudentId(studentId);
            studentAccessToken.setDateLogin(now);

            if (addStudentAccessToken(studentAccessToken)) {
                StudentRefreshToken studentRefreshToken = new StudentRefreshToken();
                studentRefreshToken.setJwtToken(refreshToken);
                studentRefreshToken.setStudentId(studentId);
                studentRefreshToken.setDateLogin(now);

                // Lưu Refresh Token thành công -> Trả về kết quả cho FE
                if (addStudentRefreshToken(studentRefreshToken)) {
                    dataResult.put("studentId", studentFound.getStudentId());
                    dataResult.put("accessToken", accessToken);
                    dataResult.put("refreshToken", refreshToken);
                }
            }
        }
        return dataResult;
    }
}
