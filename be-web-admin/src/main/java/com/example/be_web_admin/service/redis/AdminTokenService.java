package com.example.be_web_admin.service.redis;

import com.example.be_web_admin.model.redis.AdminAccessToken;
import com.example.be_web_admin.model.redis.AdminRefreshToken;
import io.lettuce.core.RedisBusyException;

import java.util.Map;

public interface AdminTokenService {

    public boolean addAdminAccessToken(AdminAccessToken adminAccessToken)  throws RedisBusyException;

    public boolean findAdminAccessTokenByJwtToken(String jwtToken) throws RedisBusyException;

    public boolean addAdminRefreshToken(AdminRefreshToken adminRefreshToken)  throws RedisBusyException;

    public boolean findAdminRefreshTokenByJwtToken(String jwtToken) throws RedisBusyException;

    public boolean removeAdminRefreshTokenByJwtToken(String jwtToken) throws RedisBusyException;

    public boolean removeAdminAccessTokenByJwtToken(String jwtToken) throws RedisBusyException;

    public Map<String, Object> createTokenAdmin(Long adminId) throws RedisBusyException;
}
