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
 * Bảng ntt_demo_eval_runs — một lần chạy đánh giá (rule-only hoặc AI đầy đủ).
 */
@Entity
@Table(name = "ntt_demo_eval_runs")
public class DemoEvalRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "run_id", nullable = false, updatable = false, columnDefinition = "bigint")
    private Long id;

    @Column(name = "mode", nullable = false, length = 10, columnDefinition = "varchar(10)")
    private String mode; // rule | ai

    @Column(name = "status", nullable = false, length = 12, columnDefinition = "varchar(12)")
    private String status; // running | done | stopped | failed

    @Column(name = "total_questions", nullable = false, columnDefinition = "int")
    private Integer totalQuestions;

    @Column(name = "processed_count", nullable = false, columnDefinition = "int")
    private Integer processedCount = 0;

    @Column(name = "correct_count", nullable = false, columnDefinition = "int")
    private Integer correctCount = 0;

    @Column(name = "error_count", nullable = false, columnDefinition = "int")
    private Integer errorCount = 0;

    @Column(name = "accuracy", columnDefinition = "double")
    private Double accuracy;

    @Column(name = "macro_f1", columnDefinition = "double")
    private Double macroF1;

    @Column(name = "micro_f1", columnDefinition = "double")
    private Double microF1;

    @Column(name = "started_at", nullable = false, updatable = false, columnDefinition = "datetime(6)")
    private Instant startedAt;

    @Column(name = "finished_at", columnDefinition = "datetime(6)")
    private Instant finishedAt;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    protected DemoEvalRun() {
    }

    public DemoEvalRun(String mode, int totalQuestions) {
        this.mode = mode;
        this.status = "running";
        this.totalQuestions = totalQuestions;
        this.processedCount = 0;
        this.correctCount = 0;
        this.errorCount = 0;
    }

    @PrePersist
    void applyDefaults() {
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public String getMode() { return mode; }
    public void setMode(String v) { this.mode = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Integer getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(Integer v) { this.totalQuestions = v; }
    public Integer getProcessedCount() { return processedCount; }
    public void setProcessedCount(Integer v) { this.processedCount = v; }
    public Integer getCorrectCount() { return correctCount; }
    public void setCorrectCount(Integer v) { this.correctCount = v; }
    public Integer getErrorCount() { return errorCount; }
    public void setErrorCount(Integer v) { this.errorCount = v; }
    public Double getAccuracy() { return accuracy; }
    public void setAccuracy(Double v) { this.accuracy = v; }
    public Double getMacroF1() { return macroF1; }
    public void setMacroF1(Double v) { this.macroF1 = v; }
    public Double getMicroF1() { return microF1; }
    public void setMicroF1(Double v) { this.microF1 = v; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant v) { this.finishedAt = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
}
