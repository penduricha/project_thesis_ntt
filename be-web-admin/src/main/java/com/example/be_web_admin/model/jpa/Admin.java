package com.example.be_web_admin.model.jpa;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.redis.core.index.Indexed;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Setter
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ntt_admins", indexes = {
        @Index(name = "idx_admin_id", columnList = "admin_id")
})
public class Admin implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Indexed
    @Column(nullable = false, unique = true, columnDefinition = "bigint", name = "admin_id")
    //@GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long adminId;

    @Column(nullable = false, columnDefinition = "nvarchar(100)")
    private String password;

    @Column(nullable = false, columnDefinition = "nvarchar(100)", name = "admin_name")
    private String adminName;

    @Column(nullable = false)
    private Boolean gender;

    @Column(nullable = false, columnDefinition = "date", name = "date_of_birth_admin")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate dateOfBirthAdmin;

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
