package com.example.be_web_admin.repository.redis;

import com.example.be_web_admin.model.redis.SessionLoginMobile;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SessionLoginMobileRepository extends CrudRepository<SessionLoginMobile, Long> {
    // Khoá chính là adminId nên findById / existsById / deleteById là đủ.
}
