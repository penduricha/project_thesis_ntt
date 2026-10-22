package com.example.be_web_students.service.cached;

import com.example.be_web_students.model.cached.StudentAccessToken;
import com.example.be_web_students.model.cached.StudentRefreshToken;
import io.lettuce.core.RedisBusyException;

import java.util.Map;

public interface I_StudentTokenService {

    public boolean addStudentAccessToken(StudentAccessToken studentAccessToken)  throws RedisBusyException;

    public boolean findStudentAccessTokenByJwtToken(String jwtToken) throws RedisBusyException;

    public boolean addStudentRefreshToken(StudentRefreshToken studentRefreshToken)  throws RedisBusyException;

    public boolean findStudentRefreshTokenByJwtToken(String jwtToken) throws RedisBusyException;

    public boolean removeStudentRefreshTokenByJwtToken(String jwtToken) throws RedisBusyException;

    public boolean removeStudentAccessTokenByJwtToken(String jwtToken) throws RedisBusyException;

    public Map<String, Object> createTokenStudent(Long studentId) throws RedisBusyException;
}
