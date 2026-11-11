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
@Table(name = "ntt_backup_codes")
public class BackupCode implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "bigint", name = "code_id")
    private Long codeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", foreignKey = @ForeignKey(name = "fk_admin_backup_code"))
    @JsonIgnore
    private Admin admin;

    // BCrypt (60 ký tự). Hash có salt nên không cần unique.
    @JsonIgnore
    @Column(length = 60, columnDefinition = "char(60)", name = "code_hash")
    private String codeHash;

    @Column(name = "used")
    private Boolean used = false;

    @Column(columnDefinition = "datetime(6)", name = "used_at")
    private Instant usedAt;

    @CreationTimestamp
    @Column(columnDefinition = "datetime(6)", name = "created_at", updatable = false)
    private Instant createdAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BackupCode that)) return false;
        return codeId != null && Objects.equals(codeId, that.codeId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
