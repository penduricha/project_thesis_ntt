package com.example.be_web_admin.model.jpa;

import com.example.be_web_admin.enums.DevicePlatform;
import com.example.be_web_admin.enums.DeviceStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Setter
@Getter
//@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ntt_mobile_devices")
public class MobileDevice implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "bigint", name = "device_id")
    private Long deviceId;

    // N thiết bị thuộc 1 admin (1-n). Cột admin_id tự có index do là khoá ngoại.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", foreignKey = @ForeignKey(name = "fk_admin_mobile_device"))
    @JsonIgnore
    private Admin admin;

    // ID cài đặt do app sinh ra, mỗi thiết bị một giá trị.
    @Column(length = 64, name = "device_uuid", unique = true)
    private String deviceUuid;

    @Column(length = 100, name = "device_name")
    private String deviceName;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, name = "platform")
    private DevicePlatform platform;

    @Column(length = 50, name = "os_version")
    private String osVersion;

    @Column(length = 20, name = "app_version")
    private String appVersion;

    // Khoá công khai ECDSA P-256 (Base64) để app ký challenge, mỗi thiết bị một khoá.
    @Column(length = 512, name = "public_key", unique = true)
    private String publicKey;

    // PENDING -> ACTIVE -> REVOKED
    @Enumerated(EnumType.STRING)
    @Column(length = 20, name = "status")
    private DeviceStatus status = DeviceStatus.PENDING;

    // 45 ký tự đủ cho IPv6.
    @Column(length = 45, name = "last_ip")
    private String lastIp;

    // Ghi từ Redis (MobileSession) xuống theo lô, không ghi mỗi request.
    @Column(columnDefinition = "datetime(6)", name = "last_seen_at")
    private Instant lastSeenAt;

    @CreationTimestamp
    @Column(columnDefinition = "datetime(6)", name = "registered_at", updatable = false)
    private Instant registeredAt;

    @Column(columnDefinition = "datetime(6)", name = "revoked_at")
    private Instant revokedAt;

    @Column(length = 100, name = "revoke_reason")
    private String revokeReason;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MobileDevice that)) return false;
        return deviceId != null && Objects.equals(deviceId, that.deviceId);
    }

    // deviceId do DB sinh nên hashCode phải cố định, nếu không sẽ đổi sau khi save().
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
