package com.example.be_web_admin.repository.redis;


import com.example.be_web_admin.model.redis.AdminRefreshToken;
import org.springframework.data.repository.CrudRepository;

public interface AdminRefreshTokenRepository extends CrudRepository<AdminRefreshToken, Long> {
    AdminRefreshToken findAdminRefreshTokenByJwtToken(String jwtToken);
}
