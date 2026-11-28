package com.example.be_web_admin.repository.jpa;

import com.example.be_web_admin.model.jpa.Admin;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Repository
public interface AdminRepository extends JpaRepository<Admin,Long> {
    Admin findAdminByAdminId(Long adminId);

    /** Serialize 2FA state transitions for one admin within a database transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Admin a where a.adminId = :id")
    Admin findForTotpUpdate(@Param("id") Long id);

    @Transactional
    @Modifying
    @Query("update Admin a set a.lastLoginAt = :at where a.adminId = :id")
    int updateLastLoginAt(@Param("id") Long id, @Param("at") Instant at);
}
