package com.example.be_web_admin.service.redis.impl;

import com.example.be_web_admin.security.AuthSessionStore;
import com.example.be_web_admin.model.redis.AdminAccessToken;
import com.example.be_web_admin.model.redis.AdminRefreshToken;
import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.repository.redis.AdminAccessTokenRepository;
import com.example.be_web_admin.repository.redis.AdminRefreshTokenRepository;
import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.example.be_web_admin.security.jwt.user.AdminJwtTokenProvider;
import com.example.be_web_admin.service.redis.AdminTokenService;
import io.lettuce.core.RedisBusyException;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.Duration;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class AdminTokenServiceImpl implements AdminTokenService {

    private final AdminAccessTokenRepository adminAccessTokenRepository;

    private final AdminRefreshTokenRepository adminRefreshTokenRepository;

    private final AdminRepository adminRepository;

    private final AdminJwtTokenProvider adminJwtTokenProvider;
    private final StringRedisTemplate redis;
    private final AuthSessionStore sessions;

    public AdminTokenServiceImpl(AdminAccessTokenRepository adminAccessTokenRepository, AdminRefreshTokenRepository adminRefreshTokenRepository, AdminRepository adminRepository, AdminJwtTokenProvider adminJwtTokenProvider, StringRedisTemplate redis, AuthSessionStore sessions) {
        this.adminAccessTokenRepository = adminAccessTokenRepository;
        this.adminRefreshTokenRepository = adminRefreshTokenRepository;
        this.adminRepository = adminRepository;
        this.adminJwtTokenProvider = adminJwtTokenProvider;
        this.redis = redis;
        this.sessions = sessions;
    }

    @Override
    public boolean addAdminAccessToken(AdminAccessToken adminAccessToken) throws RedisBusyException {
        boolean adminAccessTokenFound = adminAccessTokenRepository.findAdminAccessTokenByJwtToken(adminAccessToken.getJwtToken()) != null;
        if(!adminAccessTokenFound) {
            adminAccessTokenRepository.save(adminAccessToken);
            return true;
        }
        return false;
    }

    @Override
    public boolean findAdminAccessTokenByJwtToken(String jwtToken) throws RedisBusyException {
        AdminAccessToken adminAccessTokenFound = adminAccessTokenRepository
                .findAdminAccessTokenByJwtToken(jwtToken);
        if (adminAccessTokenFound == null) return false;
        AuthSessionStore.Identity identity = adminJwtTokenProvider.parseIdentity(jwtToken, "ACCESS");
        return identity != null && sessions.isActive(identity) && isAccountAllowed(identity.adminId());
    }

    @Override
    public boolean addAdminRefreshToken(AdminRefreshToken adminRefreshToken) throws RedisBusyException {
        boolean adminRefreshTokenFound = adminRefreshTokenRepository.findAdminRefreshTokenByJwtToken(adminRefreshToken.getJwtToken()) != null;
        if(!adminRefreshTokenFound) {
            adminRefreshTokenRepository.save(adminRefreshToken);
            return true;
        }
        return false;
    }

    @Override
    public boolean findAdminRefreshTokenByJwtToken(String jwtToken) throws RedisBusyException {
        AdminRefreshToken adminRefreshToken = adminRefreshTokenRepository
                .findAdminRefreshTokenByJwtToken(jwtToken);
        if (adminRefreshToken == null || Boolean.TRUE.equals(redis.hasKey(consumedKey(jwtToken)))) return false;
        AuthSessionStore.Identity identity = adminJwtTokenProvider.parseIdentity(jwtToken, "REFRESH");
        return identity != null && sessions.isActive(identity) && isAccountAllowed(identity.adminId());
    }

//    String accessToken = jwtTokenProvider.generateAccessToken(adminFound.getAdminId());
//    String refreshToken = jwtTokenProvider.generateRefreshToken(adminFound.getAdminId());
    @Override
    public boolean removeAdminRefreshTokenByJwtToken(String jwtToken) throws RedisBusyException {
        if (adminJwtTokenProvider.parseRefreshTokenAdminId(jwtToken) == null) return false;
        AdminRefreshToken token = adminRefreshTokenRepository.findAdminRefreshTokenByJwtToken(jwtToken);
        if (token == null) return false;
        // A distributed one-use claim, kept longer than the maximum refresh-token lifetime.
        // If deletion fails, the claim remains and subsequent refresh attempts fail closed.
        Boolean claimed = redis.opsForValue().setIfAbsent(consumedKey(jwtToken), "1", Duration.ofDays(31));
        if (!Boolean.TRUE.equals(claimed)) return false;
        adminRefreshTokenRepository.delete(token);
        return true;
    }

    @Override
    public void removeAdminAccessTokenByJwtToken(String jwtToken) throws RedisBusyException {
        AdminAccessToken adminAccessTokenFound = adminAccessTokenRepository
                .findAdminAccessTokenByJwtToken(jwtToken);
        if(adminAccessTokenFound != null) {
            adminAccessTokenRepository.delete(adminAccessTokenFound);
        }
    }

    @Override
    public Map<String, Object> createTokenAdmin(AuthSessionStore.Identity identity) throws RedisBusyException {
        Map<String, Object> dataResult = new HashMap<>();
        if (identity == null || !sessions.isActive(identity)) return dataResult;
        Long adminId = identity.adminId();

        Admin adminFound = adminRepository.findAdminByAdminId(adminId);

        if(adminFound != null && adminFound.isLoginAllowed()) {
            String accessToken = adminJwtTokenProvider.generateAccessToken(adminId, identity);
            String refreshToken = adminJwtTokenProvider.generateRefreshToken(adminId, identity);
            LocalDateTime now = LocalDateTime.now();

            AdminAccessToken adminAccessToken = new AdminAccessToken();
            adminAccessToken.setJwtToken(accessToken);
            adminAccessToken.setAdminId(adminId);
            adminAccessToken.setDateLogin(now);

            if (addAdminAccessToken(adminAccessToken)) {
                AdminRefreshToken adminRefreshToken = new AdminRefreshToken();
                adminRefreshToken.setJwtToken(refreshToken);
                adminRefreshToken.setAdminId(adminId);
                adminRefreshToken.setDateLogin(now);

                // Lưu Refresh Token thành công -> Trả về kết quả cho FE
                try {
                    if (addAdminRefreshToken(adminRefreshToken)) {
                        dataResult.put("adminId", adminFound.getAdminId());
                        dataResult.put("accessToken", accessToken);
                        dataResult.put("refreshToken", refreshToken);
                    } else {
                        removeAdminAccessTokenByJwtToken(accessToken);
                    }
                } catch (RuntimeException e) {
                    try { removeAdminAccessTokenByJwtToken(accessToken); } catch (RuntimeException ignored) { }
                    throw e;
                }
            }
        }
        return dataResult;
    }
    private boolean isAccountAllowed(Long adminId) {
        if (adminId == null) return false;
        Admin admin = adminRepository.findAdminByAdminId(adminId);
        return admin != null && admin.isLoginAllowed();
    }

    private static String consumedKey(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return "auth:refresh:consumed:" + HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

}
