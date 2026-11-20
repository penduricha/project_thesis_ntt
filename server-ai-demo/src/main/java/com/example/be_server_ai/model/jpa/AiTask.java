package com.example.be_server_ai.model.jpa;

import com.example.be_server_ai.enums.AiTaskStatus;
import com.example.be_server_ai.enums.AiTaskType;
import com.example.be_server_ai.model.jpa.converter.AiTaskStatusConverter;
import com.example.be_server_ai.model.jpa.converter.AiTaskTypeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.Objects;

/**
 * Bảng ntt_ai_tasks (ER v4, 25 cột): một tác vụ AI, hoặc kiểm tra một revision câu hỏi (question_check),
 * hoặc phân tích câu tìm kiếm tự nhiên (nl_search).
 *
 * <p>Quy ước một bảng một bên ghi: Server 1 INSERT (nhóm cột updatable = false) và hủy task; Server 2 chỉ
 * UPDATE 16 cột thực thi (status, attempt_count, started_at, finished_at, error_code, error_message,
 * result_json, raw_response, is_json_valid, model_name, model_digest, runtime_version, prompt_version,
 * params_json, usage_json, inference_ms). Các cột còn lại được khóa bằng updatable = false nên save() cũng
 * không thể ghi đè. Chuyển trạng thái nên dùng UPDATE có điều kiện (WHERE status = ...) trong repository.
 *
 * <p>@DynamicUpdate: chỉ đưa các cột đã đổi vào câu UPDATE, khớp với quyền UPDATE theo cột của user DB.
 * Khóa chính do ứng dụng gán (UUID dạng chuỗi 36 ký tự): Persistable tránh SELECT thừa khi save().
 */
@Entity
@Table(name = "ntt_ai_tasks")
@DynamicUpdate
public class AiTask implements Persistable<String> {

    @Id
    @Column(name = "task_id", nullable = false, updatable = false, length = 36, columnDefinition = "char(36)")
    private String taskId;

    @Convert(converter = AiTaskTypeConverter.class)
    @Column(name = "task_type", nullable = false, updatable = false, length = 20, columnDefinition = "enum('question_check','nl_search')")
    private AiTaskType taskType;

    @Column(name = "question_id", updatable = false, columnDefinition = "bigint")
    private Long questionId;

    @Column(name = "question_revision_id", updatable = false, columnDefinition = "bigint")
    private Long questionRevisionId;

    @Convert(converter = AiTaskStatusConverter.class)
    @Column(name = "status", nullable = false, length = 20, columnDefinition = "enum('pending','running','succeeded','failed','cancelled')")
    private AiTaskStatus status;

    // Cột sinh (STORED) của DB: chỉ đọc, không bao giờ ghi.
    @Column(name = "active_flag", insertable = false, updatable = false, columnDefinition = "tinyint")
    private Integer activeFlag;

    @Column(name = "attempt_count", nullable = false, columnDefinition = "tinyint")
    private Integer attemptCount;

    @Column(name = "error_code", length = 50, columnDefinition = "varchar(50)")
    private String errorCode;

    @Column(name = "error_message", length = 500, columnDefinition = "varchar(500)")
    private String errorMessage;

    @Column(name = "input_json", nullable = false, updatable = false, columnDefinition = "json")
    private String inputJson;

    @Column(name = "result_json", columnDefinition = "json")
    private String resultJson;

    // longtext: các truy vấn danh sách dùng projection để không kéo cột này.
    @Column(name = "raw_response", columnDefinition = "longtext")
    private String rawResponse;

    @Column(name = "is_json_valid", columnDefinition = "bit(1)")
    private Boolean jsonValid;

    @Column(name = "model_name", length = 100, columnDefinition = "varchar(100)")
    private String modelName;

    @Column(name = "model_digest", length = 64, columnDefinition = "char(64)")
    private String modelDigest;

    @Column(name = "runtime_version", length = 30, columnDefinition = "varchar(30)")
    private String runtimeVersion;

    @Column(name = "prompt_version", length = 30, columnDefinition = "varchar(30)")
    private String promptVersion;

    @Column(name = "params_json", columnDefinition = "json")
    private String paramsJson;

    @Column(name = "usage_json", columnDefinition = "json")
    private String usageJson;

    @Column(name = "inference_ms", columnDefinition = "int")
    private Integer inferenceMs;

    @Column(name = "created_by", nullable = false, updatable = false, columnDefinition = "bigint")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    @Column(name = "started_at", columnDefinition = "datetime(6)")
    private Instant startedAt;

    @Column(name = "finished_at", columnDefinition = "datetime(6)")
    private Instant finishedAt;

    @Column(name = "eval_run_id", updatable = false, columnDefinition = "bigint")
    private Long evalRunId;

    @Transient
    private boolean newEntity = true;

    protected AiTask() {
        // JPA
    }

    /** Dùng cho INSERT: gán task_id (UUID), loại task, người tạo và dữ liệu đầu vào. */
    public AiTask(String taskId, AiTaskType taskType, Long questionId, Long questionRevisionId,
                  String inputJson, Long createdBy) {
        this.taskId = taskId;
        this.taskType = taskType;
        this.questionId = questionId;
        this.questionRevisionId = questionRevisionId;
        this.inputJson = inputJson;
        this.createdBy = createdBy;
    }

    @PrePersist
    void applyDefaults() {
        if (status == null) {
            status = AiTaskStatus.PENDING;
        }
        if (attemptCount == null) {
            attemptCount = 1;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.newEntity = false;
    }

    @Override
    public String getId() {
        return taskId;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    public String getTaskId() {
        return taskId;
    }

    public AiTaskType getTaskType() {
        return taskType;
    }

    public void setTaskType(AiTaskType taskType) {
        this.taskType = taskType;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public Long getQuestionRevisionId() {
        return questionRevisionId;
    }

    public void setQuestionRevisionId(Long questionRevisionId) {
        this.questionRevisionId = questionRevisionId;
    }

    public AiTaskStatus getStatus() {
        return status;
    }

    public void setStatus(AiTaskStatus status) {
        this.status = status;
    }

    public Integer getActiveFlag() {
        return activeFlag;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getInputJson() {
        return inputJson;
    }

    public void setInputJson(String inputJson) {
        this.inputJson = inputJson;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    public Boolean getJsonValid() {
        return jsonValid;
    }

    public void setJsonValid(Boolean jsonValid) {
        this.jsonValid = jsonValid;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getModelDigest() {
        return modelDigest;
    }

    public void setModelDigest(String modelDigest) {
        this.modelDigest = modelDigest;
    }

    public String getRuntimeVersion() {
        return runtimeVersion;
    }

    public void setRuntimeVersion(String runtimeVersion) {
        this.runtimeVersion = runtimeVersion;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public String getParamsJson() {
        return paramsJson;
    }

    public void setParamsJson(String paramsJson) {
        this.paramsJson = paramsJson;
    }

    public String getUsageJson() {
        return usageJson;
    }

    public void setUsageJson(String usageJson) {
        this.usageJson = usageJson;
    }

    public Integer getInferenceMs() {
        return inferenceMs;
    }

    public void setInferenceMs(Integer inferenceMs) {
        this.inferenceMs = inferenceMs;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Long getEvalRunId() {
        return evalRunId;
    }

    public void setEvalRunId(Long evalRunId) {
        this.evalRunId = evalRunId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AiTask other)) {
            return false;
        }
        return taskId != null && Objects.equals(taskId, other.taskId);
    }

    @Override
    public int hashCode() {
        return AiTask.class.hashCode();
    }

    /** Không in input_json, raw_response: dữ liệu lớn và có nội dung câu hỏi. */
    @Override
    public String toString() {
        return "AiTask{taskId=" + taskId + ", type=" + taskType + ", status=" + status
                + ", attempt=" + attemptCount + "}";
    }
}
