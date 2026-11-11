package com.example.be_web_admin.model.jpa;

import com.example.be_web_admin.enums.SessionStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Lịch sử / danh sách phiên đăng nhập Web (lưu lâu dài để hiển thị).
 * Trạng thái đang hoạt động nhanh nằm ở Redis: model.redis.ActiveWebSession (cùng sessionId = sid).
 */
@Entity
@Setter
@Getter
//@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ntt_web_sessions")
public class WebSession implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // UUID do ứng dụng sinh, cũng là claim "sid" trong JWT nên không dùng IDENTITY.
    @Id
    @Column(length = 36, columnDefinition = "char(36)", name = "session_id")
    private String sessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", foreignKey = @ForeignKey(name = "fk_admin_web_session"))
    @JsonIgnore
    private Admin admin;

    // Thiết bị đã duyệt TOTP cho phiên này (có thể NULL).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", foreignKey = @ForeignKey(name = "fk_device_web_session"))
    @JsonIgnore
    private MobileDevice device;

    // SHA-256 (hex) của refresh token, không lưu token gốc.
    @JsonIgnore
    @Column(length = 64, columnDefinition = "char(64)", name = "refresh_token_hash", unique = true)
    private String refreshTokenHash;

    @Column(length = 45, name = "ip_address")
    private String ipAddress;

    @Column(length = 255, name = "user_agent")
    private String userAgent;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, name = "status")
    private SessionStatus status = SessionStatus.ACTIVE;

    @CreationTimestamp
    @Column(columnDefinition = "datetime(6)", name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(columnDefinition = "datetime(6)", name = "expires_at")
    private Instant expiresAt;

    @Column(columnDefinition = "datetime(6)", name = "revoked_at")
    private Instant revokedAt;

    @Column(length = 100, name = "revoke_reason")
    private String revokeReason;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WebSession that)) return false;
        return Objects.equals(sessionId, that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(sessionId);
    }
}
