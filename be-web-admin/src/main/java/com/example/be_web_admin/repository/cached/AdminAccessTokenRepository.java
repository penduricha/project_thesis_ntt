package com.example.be_web_admin.repository.cached;

import com.example.be_web_admin.model.cached.AdminAccessToken;


import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
//'org.springframework.boot:spring-boot-starter-data-redis'
public interface AdminAccessTokenRepository extends CrudRepository<AdminAccessToken, Long> {

    AdminAccessToken findAdminAccessTokenByJwtToken(String jwtToken);

    //Long deleteByJwtToken(String jwtToken);

}
