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
 * Bảng ntt_demo_questions — bộ câu hỏi DEMO (ntt_questions-style, ER v4).
 * Chỉ dùng cho module đánh giá demo; KHÔNG phải bản sao production và không ghi vào
 * bảng câu hỏi thật của CSDL.
 */
@Entity
@Table(name = "ntt_demo_questions")
public class DemoQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "demo_question_id", nullable = false, updatable = false, columnDefinition = "bigint")
    private Long id;

    @Column(name = "ext_id", nullable = false, updatable = false, length = 16, columnDefinition = "varchar(16)", unique = true)
    private String extId;

    @Column(name = "subject_code", nullable = false, length = 16, columnDefinition = "varchar(16)")
    private String subjectCode;

    @Column(name = "subject_name", nullable = false, length = 120, columnDefinition = "varchar(120)")
    private String subjectName;

    @Column(name = "topic_name", length = 200, columnDefinition = "varchar(200)")
    private String topicName;

    @Column(name = "stem", nullable = false, columnDefinition = "text")
    private String stem;

    @Column(name = "option_a", nullable = false, columnDefinition = "text")
    private String optionA;

    @Column(name = "option_b", nullable = false, columnDefinition = "text")
    private String optionB;

    @Column(name = "option_c", nullable = false, columnDefinition = "text")
    private String optionC;

    @Column(name = "option_d", nullable = false, columnDefinition = "text")
    private String optionD;

    /** Đáp án được đánh dấu trên câu hỏi (0..3) — với gold='logic' đây là đáp án SAI cố tình (lỗi key). */
    @Column(name = "correct_idx", nullable = false, columnDefinition = "tinyint")
    private Integer correctIdx;

    /** Đáp án đúng thực sự (0..3) — khác correct_idx khi gold='logic'. */
    @Column(name = "true_correct_idx", nullable = false, columnDefinition = "tinyint")
    private Integer trueCorrectIdx;

    @Column(name = "correct_label", nullable = false, length = 1, columnDefinition = "varchar(1)")
    private String correctLabel;

    @Column(name = "difficulty", nullable = false, columnDefinition = "tinyint")
    private Integer difficulty;

    @Column(name = "explanation", columnDefinition = "text")
    private String explanation;

    @Column(name = "stem_en", columnDefinition = "text")
    private String stemEn;

    /** Gold label: none | spelling | grammar | logic | distractor */
    @Column(name = "gold_issue_type", nullable = false, length = 20, columnDefinition = "varchar(20)")
    private String goldIssueType;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    protected DemoQuestion() {
    }

    @PrePersist
    void applyDefaults() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public String getExtId() { return extId; }
    public void setExtId(String v) { this.extId = v; }
    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String v) { this.subjectCode = v; }
    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String v) { this.subjectName = v; }
    public String getTopicName() { return topicName; }
    public void setTopicName(String v) { this.topicName = v; }
    public String getStem() { return stem; }
    public void setStem(String v) { this.stem = v; }
    public String getOptionA() { return optionA; }
    public void setOptionA(String v) { this.optionA = v; }
    public String getOptionB() { return optionB; }
    public void setOptionB(String v) { this.optionB = v; }
    public String getOptionC() { return optionC; }
    public void setOptionC(String v) { this.optionC = v; }
    public String getOptionD() { return optionD; }
    public void setOptionD(String v) { this.optionD = v; }
    public Integer getCorrectIdx() { return correctIdx; }
    public void setCorrectIdx(Integer v) { this.correctIdx = v; }
    public Integer getTrueCorrectIdx() { return trueCorrectIdx; }
    public void setTrueCorrectIdx(Integer v) { this.trueCorrectIdx = v; }
    public String getCorrectLabel() { return correctLabel; }
    public void setCorrectLabel(String v) { this.correctLabel = v; }
    public Integer getDifficulty() { return difficulty; }
    public void setDifficulty(Integer v) { this.difficulty = v; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String v) { this.explanation = v; }
    public String getStemEn() { return stemEn; }
    public void setStemEn(String v) { this.stemEn = v; }
    public String getGoldIssueType() { return goldIssueType; }
    public void setGoldIssueType(String v) { this.goldIssueType = v; }
    public Instant getCreatedAt() { return createdAt; }
}
