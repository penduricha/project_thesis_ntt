package com.example.server_ai_worker.demo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Bảng ntt_demo_eval_items — kết quả dự đoán trên từng câu hỏi của một lần chạy.
 * Không ghi vào ntt_ai_tasks / ntt_ai_suggestions (bảng ER v4 dùng chung).
 */
@Entity
@Table(name = "ntt_demo_eval_items")
public class DemoEvalItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id", nullable = false, updatable = false, columnDefinition = "bigint")
    private Long id;

    @Column(name = "run_id", nullable = false, updatable = false, columnDefinition = "bigint")
    private Long runId;

    @Column(name = "demo_question_id", nullable = false, updatable = false, columnDefinition = "bigint")
    private Long demoQuestionId;

    @Column(name = "ext_id", nullable = false, length = 16, columnDefinition = "varchar(16)")
    private String extId;

    @Column(name = "subject_code", nullable = false, length = 16, columnDefinition = "varchar(16)")
    private String subjectCode;

    @Column(name = "gold_label", nullable = false, length = 20, columnDefinition = "varchar(20)")
    private String goldLabel;

    /** Predicted label theo quy tắc: none nếu không có suggestion; ngược lại issue_type
     *  của suggestion severity cao nhất (key_doubt được quy đổi thành logic). */
    @Column(name = "predicted_label", nullable = false, length = 20, columnDefinition = "varchar(20)")
    private String predictedLabel;

    /** issue_type thô từ AI trước khi quy đổi (vd: key_doubt). */
    @Column(name = "raw_predicted_issue", length = 20, columnDefinition = "varchar(20)")
    private String rawPredictedIssue;

    @Column(name = "suggestion_count", nullable = false, columnDefinition = "smallint")
    private Integer suggestionCount = 0;

    @Column(name = "top_severity", length = 10, columnDefinition = "varchar(10)")
    private String topSeverity;

    @Column(name = "latency_ms", columnDefinition = "bigint")
    private Long latencyMs;

    @Column(name = "model_name", length = 120, columnDefinition = "varchar(120)")
    private String modelName;

    @Column(name = "node_name", length = 120, columnDefinition = "varchar(120)")
    private String nodeName;

    @Column(name = "error_code", length = 40, columnDefinition = "varchar(40)")
    private String errorCode;

    @Column(name = "prediction_correct", nullable = false, columnDefinition = "tinyint(1)")
    private Boolean predictionCorrect = false;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    protected DemoEvalItem() {
    }

    public DemoEvalItem(Long runId, Long demoQuestionId, String extId, String subjectCode, String goldLabel) {
        this.runId = runId;
        this.demoQuestionId = demoQuestionId;
        this.extId = extId;
        this.subjectCode = subjectCode;
        this.goldLabel = goldLabel;
    }

    @PrePersist
    void applyDefaults() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public Long getRunId() { return runId; }
    public Long getDemoQuestionId() { return demoQuestionId; }
    public String getExtId() { return extId; }
    public String getSubjectCode() { return subjectCode; }
    public String getGoldLabel() { return goldLabel; }
    public String getPredictedLabel() { return predictedLabel; }
    public void setPredictedLabel(String v) { this.predictedLabel = v; }
    public String getRawPredictedIssue() { return rawPredictedIssue; }
    public void setRawPredictedIssue(String v) { this.rawPredictedIssue = v; }
    public Integer getSuggestionCount() { return suggestionCount; }
    public void setSuggestionCount(Integer v) { this.suggestionCount = v; }
    public String getTopSeverity() { return topSeverity; }
    public void setTopSeverity(String v) { this.topSeverity = v; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long v) { this.latencyMs = v; }
    public String getModelName() { return modelName; }
    public void setModelName(String v) { this.modelName = v; }
    public String getNodeName() { return nodeName; }
    public void setNodeName(String v) { this.nodeName = v; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String v) { this.errorCode = v; }
    public Boolean getPredictionCorrect() { return predictionCorrect; }
    public void setPredictionCorrect(Boolean v) { this.predictionCorrect = v; }
    public Instant getCreatedAt() { return createdAt; }
}
