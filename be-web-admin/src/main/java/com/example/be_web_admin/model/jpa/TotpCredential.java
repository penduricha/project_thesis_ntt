package com.example.be_web_admin.model.jpa;

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
@Table(name = "ntt_totp_credentials")
public class TotpCredential implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "bigint", name = "credential_id")
    private Long credentialId;

    // 1 admin có n credential (n thiết bị).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", foreignKey = @ForeignKey(name = "fk_admin_totp_credential"))
    @JsonIgnore
    private Admin admin;

    // 1 thiết bị - 1 credential (unique = true tạo quan hệ 1-1).
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", unique = true,
            foreignKey = @ForeignKey(name = "fk_device_totp_credential"))
    @JsonIgnore
    private MobileDevice device;

    // Secret key đã mã hoá AES-256-GCM (32 byte khoá + 16 byte tag = 48 byte).
    // Không bao giờ trả ra JSON.
    @JsonIgnore
    @Column(columnDefinition = "varbinary(64)", name = "secret_cipher")
    private byte[] secretCipher;

    // IV (nonce) 12 byte của GCM: không được dùng lại nên đặt unique.
    @JsonIgnore
    @Column(columnDefinition = "varbinary(12)", name = "iv", unique = true)
    private byte[] iv;

    // Salt cho PBKDF2.
    @JsonIgnore
    @Column(columnDefinition = "varbinary(16)", name = "kdf_salt")
    private byte[] kdfSalt;

    // Phiên bản khoá master, dùng khi xoay khoá.
    @Column(name = "key_version")
    private Integer keyVersion = 1;

    @Column(length = 20, name = "algorithm")
    private String algorithm = "HmacSHA256";

    @Column(columnDefinition = "tinyint", name = "digits")
    private Byte digits = 6;

    @Column(columnDefinition = "smallint", name = "period_seconds")
    private Short periodSeconds = 30;

    // true sau khi app gửi đúng mã TOTP đầu tiên.
    @Column(name = "confirmed")
    private Boolean confirmed = false;

    @CreationTimestamp
    @Column(columnDefinition = "datetime(6)", name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(columnDefinition = "datetime(6)", name = "confirmed_at")
    private Instant confirmedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TotpCredential that)) return false;
        return credentialId != null && Objects.equals(credentialId, that.credentialId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
