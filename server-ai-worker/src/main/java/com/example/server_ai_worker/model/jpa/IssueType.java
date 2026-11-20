package com.example.server_ai_worker.model.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.util.Objects;

/**
 * Bảng ntt_issue_types: danh mục loại lỗi dùng chung cho AI và đánh giá P/R/F1.
 * Chuẩn hóa 5 nhãn lỗi (spelling, grammar, logic, distractor, key_doubt) + none.
 */
@Entity
@Table(name = "ntt_issue_types")
@Immutable
public class IssueType {

    @Id
    @Column(name = "issue_type", nullable = false, length = 20, columnDefinition = "varchar(20)")
    private String issueType;

    @Column(name = "label_vi", nullable = false, length = 100, columnDefinition = "varchar(100)")
    private String labelVi;

    @Column(name = "is_evaluated", nullable = false, columnDefinition = "bit(1)")
    private Boolean evaluated;

    @Column(name = "sort_order", nullable = false, columnDefinition = "int default 0")
    private Integer sortOrder = 0;

    protected IssueType() {
    }

    public IssueType(String issueType, String labelVi, boolean evaluated, int sortOrder) {
        this.issueType = issueType;
        this.labelVi = labelVi;
        this.evaluated = evaluated;
        this.sortOrder = sortOrder;
    }

    public String getIssueType() {
        return issueType;
    }

    public String getLabelVi() {
        return labelVi;
    }

    public Boolean getEvaluated() {
        return evaluated;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IssueType other)) {
            return false;
        }
        return issueType != null && Objects.equals(issueType, other.issueType);
    }

    @Override
    public int hashCode() {
        return IssueType.class.hashCode();
    }

    @Override
    public String toString() {
        return "IssueType{" + issueType + "}";
    }
}
