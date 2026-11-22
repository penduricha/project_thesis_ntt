package com.example.be_web_admin.model.jpa.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * Bảng ntt_issue_types — danh mục loại lỗi dùng chung cho AI, UI và module thực nghiệm.
 *
 * <p>Khoá chính là mã chuỗi (spelling, grammar, presentation, logic, distractor, style,
 * key_doubt, none) nên entity này KHÔNG dùng enum: thêm loại lỗi mới chỉ cần 1 dòng seed.
 * Chỉ các dòng {@code isEvaluated = true} mới được tính P/R/F1 ở Chương 4.</p>
 */
@Entity
@Table(name = "ntt_issue_types")
@Getter
@Setter
@NoArgsConstructor
public class IssueType implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "issue_type", length = 20, nullable = false, updatable = false)
    private String issueType;

    @Column(name = "label_vi", length = 100, nullable = false)
    private String labelVi;

    @Column(name = "is_evaluated", columnDefinition = "bit(1)", nullable = false)
    private Boolean isEvaluated = Boolean.TRUE;

    @Column(name = "sort_order", columnDefinition = "int", nullable = false)
    private Integer sortOrder = 0;
}
