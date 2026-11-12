package com.example.be_web_admin.repository.jpa;

import com.example.be_web_admin.model.jpa.AuthAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthAuditLogRepository extends JpaRepository<AuthAuditLog,Long> {
}
