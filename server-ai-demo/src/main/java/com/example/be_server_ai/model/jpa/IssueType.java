package com.example.be_server_ai.model.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.util.Objects;

/**
 * Bảng ntt_issue_types (ER v4): danh mục loại lỗi, 6 dòng seed do Server 1 quản lý.
 * Chỉ đọc (@Immutable): dùng để đưa danh sách issue_type hợp lệ vào enum của JSON Schema
 * và để kiểm tra trước khi INSERT ntt_ai_suggestions (cột issue_type có khóa ngoại).
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

    protected IssueType() {
        // JPA
    }

    /** Chỉ dùng để nạp dữ liệu demo (DemoDataInit). Bình thường bảng này do Server 1 quản lý. */
    public IssueType(String issueType, String labelVi, boolean evaluated) {
        this.issueType = issueType;
        this.labelVi = labelVi;
        this.evaluated = evaluated;
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
