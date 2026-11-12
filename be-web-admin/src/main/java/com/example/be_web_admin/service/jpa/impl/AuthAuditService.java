package com.example.be_web_admin.service.jpa.impl;

import com.example.be_web_admin.enums.AuthEvent;
import com.example.be_web_admin.model.jpa.AuthAuditLog;
import com.example.be_web_admin.repository.jpa.AuthAuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ghi nhật ký xác thực. Là việc phụ: lỗi ghi log chỉ được ghi cảnh báo, không bao giờ làm hỏng nghiệp vụ.
 *
 * Dùng TransactionTemplate REQUIRES_NEW thay cho @Transactional để try/catch nằm NGOÀI transaction:
 * nếu catch nằm trong @Transactional thì lỗi ở repository đã đánh dấu rollback-only và lúc commit sẽ ném
 * UnexpectedRollbackException.
 *
 * Nên gọi SAU khi transaction nghiệp vụ đã commit.
 */
@Slf4j
@Service
public class AuthAuditService {

    private static final int MAX_USER_AGENT_LENGTH = 255;
    private static final int MAX_DETAIL_LENGTH = 255;

    private final AuthAuditLogRepository repository;
    private final TransactionTemplate requiresNew;

    public AuthAuditService(AuthAuditLogRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void log(Long adminId, AuthEvent event, boolean success, String ip, String userAgent, String detail) {
        try {
            AuthAuditLog entry = new AuthAuditLog();
            entry.setAdminId(adminId);
            entry.setEventType(event);
            entry.setSuccess(success);
            entry.setIpAddress(ip);
            entry.setUserAgent(truncate(userAgent, MAX_USER_AGENT_LENGTH));
            entry.setDetail(truncate(detail, MAX_DETAIL_LENGTH));
            requiresNew.executeWithoutResult(status -> repository.save(entry));
        } catch (Exception e) {
            log.warn("Audit log failed (event={}, admin={}): {}", event, adminId, e.getMessage());
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
