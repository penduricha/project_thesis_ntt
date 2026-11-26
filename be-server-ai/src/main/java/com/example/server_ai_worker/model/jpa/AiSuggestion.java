package com.example.server_ai_worker.model.jpa;

import com.example.server_ai_worker.enums.SuggestionDecision;
import com.example.server_ai_worker.enums.SuggestionSeverity;
import com.example.server_ai_worker.enums.SuggestionTargetField;
import com.example.server_ai_worker.model.jpa.converter.SuggestionDecisionConverter;
import com.example.server_ai_worker.model.jpa.converter.SuggestionSeverityConverter;
import com.example.server_ai_worker.model.jpa.converter.SuggestionTargetFieldConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.Objects;

/**
 * Bảng ntt_ai_suggestions (ER v4, 19 cột): Server 2 chỉ INSERT; phần duyệt thuộc Server 1.
 */
@Entity
@Table(name = "ntt_ai_suggestions")
@DynamicUpdate
public class AiSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "suggestion_id", nullable = false, updatable = false, columnDefinition = "bigint")
    private Long suggestionId;

    @Column(name = "task_id", nullable = false, updatable = false, length = 36, columnDefinition = "char(36)")
    private String taskId;

    @Column(name = "seq_no", nullable = false, updatable = false, columnDefinition = "smallint")
    private Integer seqNo;

    @Column(name = "issue_type", nullable = false, updatable = false, length = 20, columnDefinition = "varchar(20)")
    private String issueType;

    @Convert(converter = SuggestionSeverityConverter.class)
    @Column(name = "severity", nullable = false, updatable = false, length = 10, columnDefinition = "enum('low','medium','high')")
    private SuggestionSeverity severity;

    @Convert(converter = SuggestionTargetFieldConverter.class)
    @Column(name = "target_field", nullable = false, updatable = false, length = 20, columnDefinition = "enum('question','stem','option_a','option_b','option_c','option_d')")
    private SuggestionTargetField targetField;

    @Column(name = "original_text", updatable = false, columnDefinition = "text")
    private String originalText;

    @Column(name = "span_start", updatable = false, columnDefinition = "int")
    private Integer spanStart;

    @Column(name = "span_end", updatable = false, columnDefinition = "int")
    private Integer spanEnd;

    @Column(name = "explanation", nullable = false, updatable = false, columnDefinition = "text")
    private String explanation;

    @Column(name = "proposed_text", updatable = false, columnDefinition = "text")
    private String proposedText;

    @Column(name = "suggested_difficulty", updatable = false, columnDefinition = "tinyint")
    private Integer suggestedDifficulty;

    @Convert(converter = SuggestionDecisionConverter.class)
    @Column(name = "decision", nullable = false, length = 10, columnDefinition = "enum('pending','accepted','rejected','stale')")
    private SuggestionDecision decision;

    @Column(name = "decided_by", columnDefinition = "bigint")
    private Long decidedBy;

    @Column(name = "decided_at", columnDefinition = "datetime(6)")
    private Instant decidedAt;

    @Column(name = "reject_reason", length = 255, columnDefinition = "varchar(255)")
    private String rejectReason;

    @Column(name = "final_text", columnDefinition = "text")
    private String finalText;

    @Column(name = "applied_revision_id", columnDefinition = "bigint")
    private Long appliedRevisionId;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    protected AiSuggestion() {
    }

    public AiSuggestion(String taskId, Integer seqNo, String issueType, SuggestionSeverity severity,
                        SuggestionTargetField targetField, String originalText, Integer spanStart,
                        Integer spanEnd, String explanation, String proposedText,
                        Integer suggestedDifficulty) {
        this.taskId = taskId;
        this.seqNo = seqNo;
        this.issueType = issueType;
        this.severity = severity;
        this.targetField = targetField;
        this.originalText = originalText;
        this.spanStart = spanStart;
        this.spanEnd = spanEnd;
        this.explanation = explanation;
        this.proposedText = proposedText;
        this.suggestedDifficulty = suggestedDifficulty;
    }

    @PrePersist
    void applyDefaults() {
        if (decision == null) {
            decision = SuggestionDecision.PENDING;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getSuggestionId() {
        return suggestionId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Integer getSeqNo() {
        return seqNo;
    }

    public void setSeqNo(Integer seqNo) {
        this.seqNo = seqNo;
    }

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String issueType) {
        this.issueType = issueType;
    }

    public SuggestionSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(SuggestionSeverity severity) {
        this.severity = severity;
    }

    public SuggestionTargetField getTargetField() {
        return targetField;
    }

    public void setTargetField(SuggestionTargetField targetField) {
        this.targetField = targetField;
    }

    public String getOriginalText() {
        return originalText;
    }

    public void setOriginalText(String originalText) {
        this.originalText = originalText;
    }

    public Integer getSpanStart() {
        return spanStart;
    }

    public void setSpanStart(Integer spanStart) {
        this.spanStart = spanStart;
    }

    public Integer getSpanEnd() {
        return spanEnd;
    }

    public void setSpanEnd(Integer spanEnd) {
        this.spanEnd = spanEnd;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getProposedText() {
        return proposedText;
    }

    public void setProposedText(String proposedText) {
        this.proposedText = proposedText;
    }

    public Integer getSuggestedDifficulty() {
        return suggestedDifficulty;
    }

    public void setSuggestedDifficulty(Integer suggestedDifficulty) {
        this.suggestedDifficulty = suggestedDifficulty;
    }

    public SuggestionDecision getDecision() {
        return decision;
    }

    public void setDecision(SuggestionDecision decision) {
        this.decision = decision;
    }

    public Long getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(Long decidedBy) {
        this.decidedBy = decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public String getFinalText() {
        return finalText;
    }

    public void setFinalText(String finalText) {
        this.finalText = finalText;
    }

    public Long getAppliedRevisionId() {
        return appliedRevisionId;
    }

    public void setAppliedRevisionId(Long appliedRevisionId) {
        this.appliedRevisionId = appliedRevisionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AiSuggestion other)) {
            return false;
        }
        return suggestionId != null && Objects.equals(suggestionId, other.suggestionId);
    }

    @Override
    public int hashCode() {
        return AiSuggestion.class.hashCode();
    }

    @Override
    public String toString() {
        return "AiSuggestion{id=" + suggestionId + ", taskId=" + taskId + ", seq=" + seqNo
                + ", issueType=" + issueType + ", decision=" + decision + "}";
    }
}
