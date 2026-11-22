package com.example.be_web_admin.model.jpa.qbank;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Bảng ntt_subjects — môn học/học phần. Lớp thứ ba của thiết kế 3 tầng:
 * class diagram (Subject) -> ER v4 (ntt_subjects) -> DDL (02-business-tables.sql).
 */
@Entity
@Table(name = "ntt_subjects")
@Getter
@Setter
@NoArgsConstructor
public class Subject implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subject_id", columnDefinition = "bigint", nullable = false, updatable = false)
    private Long subjectId;

    @Column(name = "subject_code", length = 30, nullable = false)
    private String subjectCode;

    @Column(name = "subject_name", length = 200, nullable = false)
    private String subjectName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_active", columnDefinition = "bit(1)", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    /** FK mềm tới ntt_admins (DDL cố ý KHÔNG khai báo FK cứng). */
    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", columnDefinition = "datetime(6)", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", columnDefinition = "datetime(6)", nullable = false)
    private Instant updatedAt;
}
