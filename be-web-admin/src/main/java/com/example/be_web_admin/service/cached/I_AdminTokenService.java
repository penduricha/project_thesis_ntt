package com.example.be_web_admin.service.cached;

import com.example.be_web_admin.model.cached.AdminAccessToken;
import com.example.be_web_admin.model.cached.AdminRefreshToken;
import io.lettuce.core.RedisBusyException;

import java.util.Map;

public interface I_AdminTokenService {

    public boolean addAdminAccessToken(AdminAccessToken adminAccessToken)  throws RedisBusyException;

    public boolean findAdminAccessTokenByJwtToken(String jwtToken) throws RedisBusyException;

    public boolean addAdminRefreshToken(AdminRefreshToken adminRefreshToken)  throws RedisBusyException;

    public boolean findAdminRefreshTokenByJwtToken(String jwtToken) throws RedisBusyException;

    public boolean removeAdminRefreshTokenByJwtToken(String jwtToken) throws RedisBusyException;

    public boolean removeAdminAccessTokenByJwtToken(String jwtToken) throws RedisBusyException;

    public Map<String, Object> createTokenAdmin(Long adminId) throws RedisBusyException;
}
