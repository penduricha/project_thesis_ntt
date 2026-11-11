package com.example.be_web_admin.model.jpa;

import com.example.be_web_admin.enums.AuthEvent;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Nhật ký xác thực, chỉ thêm mới, không sửa (@Immutable).
 * adminId / deviceId KHÔNG đặt khoá ngoại cứng để log vẫn còn khi tài khoản/thiết bị bị xoá,
 * và vẫn ghi được cả lần đăng nhập với adminId không tồn tại.
 */
@Entity
@Immutable
@Setter
@Getter
//@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ntt_auth_audit_logs", indexes = {
        @Index(name = "idx_audit_admin_time", columnList = "admin_id, created_at")
})
public class AuthAuditLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "bigint", name = "log_id")
    private Long logId;

    @Column(columnDefinition = "bigint", name = "admin_id")
    private Long adminId;

    @Column(columnDefinition = "bigint", name = "device_id")
    private Long deviceId;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, name = "event_type")
    private AuthEvent eventType;

    @Column(name = "success")
    private Boolean success;

    @Column(length = 45, name = "ip_address")
    private String ipAddress;

    @Column(length = 255, name = "user_agent")
    private String userAgent;

    @Column(length = 255, name = "detail")
    private String detail;

    @CreationTimestamp
    @Column(columnDefinition = "datetime(6)", name = "created_at", updatable = false)
    private Instant createdAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuthAuditLog that)) return false;
        return logId != null && Objects.equals(logId, that.logId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
