package com.example.be_web_admin.service.cached.impl;

import com.example.be_web_admin.model.cached.AdminAccessToken;
import com.example.be_web_admin.model.cached.AdminRefreshToken;
import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.repository.cached.AdminAccessTokenRepository;
import com.example.be_web_admin.repository.cached.AdminRefreshTokenRepository;
import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.example.be_web_admin.security.jwt.AdminJwtTokenProvider;
import com.example.be_web_admin.service.cached.I_AdminTokenService;
import io.lettuce.core.RedisBusyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class AdminTokenServiceImpl implements I_AdminTokenService {

    private final AdminAccessTokenRepository adminAccessTokenRepository;

    private final AdminRefreshTokenRepository adminRefreshTokenRepository;

    private final AdminRepository adminRepository;

    private final AdminJwtTokenProvider adminJwtTokenProvider;

    public AdminTokenServiceImpl(AdminAccessTokenRepository adminAccessTokenRepository, AdminRefreshTokenRepository adminRefreshTokenRepository, AdminRepository adminRepository, AdminJwtTokenProvider adminJwtTokenProvider) {
        this.adminAccessTokenRepository = adminAccessTokenRepository;
        this.adminRefreshTokenRepository = adminRefreshTokenRepository;
        this.adminRepository = adminRepository;
        this.adminJwtTokenProvider = adminJwtTokenProvider;
    }

    @Override
    public boolean addAdminAccessToken(AdminAccessToken adminAccessToken) throws RedisBusyException {
        boolean adminAccessTokenFound = findAdminAccessTokenByJwtToken(adminAccessToken.getJwtToken());
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
        return adminAccessTokenFound != null;
    }

    @Override
    public boolean addAdminRefreshToken(AdminRefreshToken adminRefreshToken) throws RedisBusyException {
        boolean adminRefreshTokenFound = findAdminRefreshTokenByJwtToken(adminRefreshToken.getJwtToken());
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
        return adminRefreshToken != null;
    }

//    String accessToken = jwtTokenProvider.generateAccessToken(adminFound.getAdminId());
//    String refreshToken = jwtTokenProvider.generateRefreshToken(adminFound.getAdminId());
    @Override
    public boolean removeAdminRefreshTokenByJwtToken(String jwtToken) throws RedisBusyException {
        AdminRefreshToken adminRefreshTokenFound = adminRefreshTokenRepository
                .findAdminRefreshTokenByJwtToken(jwtToken);
        if(adminRefreshTokenFound != null) {
            adminRefreshTokenRepository.delete(adminRefreshTokenFound);
            return true;
        }
        return false;
    }

    @Override
    public boolean removeAdminAccessTokenByJwtToken(String jwtToken) throws RedisBusyException {
        AdminAccessToken adminAccessTokenFound = adminAccessTokenRepository
                .findAdminAccessTokenByJwtToken(jwtToken);
        if(adminAccessTokenFound != null) {
            adminAccessTokenRepository.delete(adminAccessTokenFound);
            return true;
        }
        return false;
    }

    @Override
    public Map<String, Object> createTokenAdmin(Long adminId) throws RedisBusyException {
        Map<String, Object> dataResult = new HashMap<>();

        Admin adminFound = adminRepository.findAdminByAdminId(adminId);

        if(adminFound != null) {
            String accessToken = adminJwtTokenProvider.generateAccessToken(adminId);
            String refreshToken = adminJwtTokenProvider.generateRefreshToken(adminId);
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
                if (addAdminRefreshToken(adminRefreshToken)) {
                    dataResult.put("adminId", adminFound.getAdminId());
                    dataResult.put("accessToken", accessToken);
                    dataResult.put("refreshToken", refreshToken);
                }
            }
        }
        return dataResult;
    }
}
