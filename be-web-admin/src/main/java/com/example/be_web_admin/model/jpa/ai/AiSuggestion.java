package com.example.be_web_admin.model.jpa.ai;

import com.example.be_web_admin.converter.SeverityDConverter;
import com.example.be_web_admin.converter.SuggestionDecisionDConverter;
import com.example.be_web_admin.converter.TargetFieldDConverter;
import com.example.be_web_admin.enums.ai.SeverityD;
import com.example.be_web_admin.enums.ai.SuggestionDecisionD;
import com.example.be_web_admin.enums.ai.TargetFieldD;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Bảng ntt_ai_suggestions — mỗi đề xuất 1 dòng, bất biến sau khi chèn.
 *
 * <p>Ba bất biến được CSDL bảo vệ mà entity phải tôn trọng:</p>
 * <ul>
 *   <li>{@code targetField} KHÔNG có correct_label => AI không thể đổi đáp án đúng (đề cương 3.3).</li>
 *   <li>accepted/rejected => decided_by + decided_at bắt buộc (chk_sugg_decided).</li>
 *   <li>accepted phải kèm applied_revision_id (ràng buộc bổ sung ở service, xem D8).</li>
 * </ul>
 */
@Entity
@Table(name = "ntt_ai_suggestions")
@Getter
@Setter
@NoArgsConstructor
public class AiSuggestion implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "suggestion_id", columnDefinition = "bigint", nullable = false, updatable = false)
    private Long suggestionId;

    @Column(name = "task_id", length = 36, columnDefinition = "char(36)", nullable = false, updatable = false)
    private String taskId;

    @Column(name = "seq_no", columnDefinition = "smallint", nullable = false, updatable = false)
    private Integer seqNo;

    /** FK tới danh mục ntt_issue_types (không phải enum: danh mục là dữ liệu). */
    @Column(name = "issue_type", length = 20, nullable = false, updatable = false)
    private String issueType;

    @Convert(converter = SeverityDConverter.class)
    @Column(name = "severity", columnDefinition = SeverityD.SQL_TYPE, nullable = false, updatable = false)
    private SeverityD severity = SeverityD.MEDIUM;

    @Convert(converter = TargetFieldDConverter.class)
    @Column(name = "target_field", columnDefinition = TargetFieldD.SQL_TYPE, nullable = false, updatable = false)
    private TargetFieldD targetField;

    @Column(name = "original_text", columnDefinition = "text", updatable = false)
    private String originalText;

    @Column(name = "span_start", columnDefinition = "int", updatable = false)
    private Integer spanStart;

    @Column(name = "span_end", columnDefinition = "int", updatable = false)
    private Integer spanEnd;

    @Column(name = "explanation", columnDefinition = "text", nullable = false, updatable = false)
    private String explanation;

    @Column(name = "proposed_text", columnDefinition = "text", updatable = false)
    private String proposedText;

    @Column(name = "suggested_difficulty", columnDefinition = "tinyint", updatable = false)
    private Integer suggestedDifficulty;

    @Convert(converter = SuggestionDecisionDConverter.class)
    @Column(name = "decision", columnDefinition = SuggestionDecisionD.SQL_TYPE, nullable = false)
    private SuggestionDecisionD decision = SuggestionDecisionD.PENDING;

    @Column(name = "decided_by", columnDefinition = "bigint")
    private Long decidedBy;

    @Column(name = "decided_at", columnDefinition = "datetime(6)")
    private Instant decidedAt;

    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    /** Nội dung người duyệt chốt trước khi áp dụng (có thể khác proposed_text của AI). */
    @Column(name = "final_text", columnDefinition = "text")
    private String finalText;

    @Column(name = "applied_revision_id", columnDefinition = "bigint")
    private Long appliedRevisionId;

    @CreationTimestamp
    @Column(name = "created_at", columnDefinition = "datetime(6)", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", insertable = false, updatable = false)
    private AiTask task;
}
