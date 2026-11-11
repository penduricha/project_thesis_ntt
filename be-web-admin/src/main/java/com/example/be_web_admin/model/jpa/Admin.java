package com.example.be_web_admin.model.jpa;

import com.example.be_web_admin.enums.AccountStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Setter
@Getter
//@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ntt_admins")
public class Admin implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // adminId là định danh đăng nhập (thay email), do ứng dụng cấp nên không dùng IDENTITY.
    // PK đã tự có index + unique.
    @Id
    @Column(unique = true, columnDefinition = "bigint", name = "admin_id")
    //@GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long adminId;

    // Chỉ cho ghi vào, không trả hash ra JSON.
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(length = 100, name = "password")
    private String password;

    @Column(length = 100, name = "admin_name")
    private String adminName;

    @Column(length = 255, name = "address")
    private String address;

    // Số CCCD/CMND: mỗi người một số nên phải duy nhất.
    @Column(length = 12, name = "id_card", unique = true)
    private String idCard;

    @Column(name = "gender")
    private Boolean gender;

    @Column(columnDefinition = "date", name = "date_of_birth_admin")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate dateOfBirthAdmin;

    // ===== Field mới phục vụ 2FA / quản lý phiên =====

    // Đã bật TOTP (đã ghép thiết bị và xác nhận mã đầu tiên) hay chưa.
    @Column(name = "totp_enabled")
    private Boolean totpEnabled = false;

    // PENDING (mới đăng ký, chờ OTP) -> ACTIVE -> LOCKED / DISABLED.
    @Enumerated(EnumType.STRING)
    @Column(length = 20, name = "account_status")
    private AccountStatus accountStatus = AccountStatus.PENDING;

    @Column(columnDefinition = "datetime(6)", name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(columnDefinition = "datetime(6)", name = "last_login_at")
    private Instant lastLoginAt;

    @CreationTimestamp
    @Column(columnDefinition = "datetime(6)", name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(columnDefinition = "datetime(6)", name = "updated_at")
    private Instant updatedAt;

    @ManyToOne
    @JoinColumn(name = "course_id", foreignKey = @ForeignKey(name = "fk_course_admin"))
    @JsonIgnore
    private Course course;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Admin admin)) return false;
        return Objects.equals(adminId, admin.adminId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(adminId);
    }
}
