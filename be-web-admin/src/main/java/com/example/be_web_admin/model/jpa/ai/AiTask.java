package com.example.be_web_admin.model.jpa.ai;

import com.example.be_web_admin.converter.TaskStatusDConverter;
import com.example.be_web_admin.converter.TaskTypeDConverter;
import com.example.be_web_admin.enums.ai.TaskStatusD;
import com.example.be_web_admin.enums.ai.TaskTypeD;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Bảng ntt_ai_tasks — hàng đợi bền của tác vụ AI. Trạng thái SỰ THẬT nằm ở đây; Redis chỉ giữ
 * bản sao nhẹ để giao diện polling (xem UiStateStore + docs/api-qbank-redis.md).
 *
 * <p>Cột sinh {@code active_flag = IF(status IN ('pending','running'), 1, NULL)} không được map.
 * Nhờ NULL mà UNIQUE (question_id, task_type, active_flag) chỉ chặn "2 task sống cùng lúc cho
 * 1 câu + 1 loại", còn task đã kết thúc được phép trùng vô hạn.</p>
 *
 * <p>taskId do Server 1 sinh (UUID 36 ký tự) nên không dùng @GeneratedValue.</p>
 */
@Entity
@Table(name = "ntt_ai_tasks")
@Getter
@Setter
@NoArgsConstructor
public class AiTask implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "task_id", length = 36, columnDefinition = "char(36)", nullable = false, updatable = false)
    private String taskId;

    @Convert(converter = TaskTypeDConverter.class)
    @Column(name = "task_type", columnDefinition = TaskTypeD.SQL_TYPE, nullable = false, updatable = false)
    private TaskTypeD taskType;

    @Column(name = "question_id", columnDefinition = "bigint", updatable = false)
    private Long questionId;

    /** Task gắn với 1 revision cụ thể, không gắn "câu hỏi hiện tại". */
    @Column(name = "question_revision_id", columnDefinition = "bigint", updatable = false)
    private Long questionRevisionId;

    @Convert(converter = TaskStatusDConverter.class)
    @Column(name = "status", columnDefinition = TaskStatusD.SQL_TYPE, nullable = false)
    private TaskStatusD status = TaskStatusD.PENDING;

    @Column(name = "attempt_count", columnDefinition = "tinyint", nullable = false)
    private Integer attemptCount = 1;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /** Bản sao nội dung câu hỏi lấy từ snapshot_json của đúng revision. */
    @Column(name = "input_json", columnDefinition = "json", nullable = false, updatable = false)
    private String inputJson;

    @Column(name = "result_json", columnDefinition = "json")
    private String resultJson;

    @Column(name = "raw_response", columnDefinition = "longtext")
    private String rawResponse;

    @Column(name = "is_json_valid", columnDefinition = "bit(1)")
    private Boolean isJsonValid;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "model_digest", length = 64, columnDefinition = "char(64)")
    private String modelDigest;

    @Column(name = "runtime_version", length = 30)
    private String runtimeVersion;

    @Column(name = "prompt_version", length = 30)
    private String promptVersion;

    @Column(name = "params_json", columnDefinition = "json")
    private String paramsJson;

    @Column(name = "usage_json", columnDefinition = "json")
    private String usageJson;

    @Column(name = "inference_ms", columnDefinition = "int")
    private Integer inferenceMs;

    /** NULL = task chạy thật; khác NULL = thuộc module thực nghiệm E1-E8. */
    @Column(name = "eval_run_id", columnDefinition = "bigint", updatable = false)
    private Long evalRunId;

    @Column(name = "created_by", columnDefinition = "bigint")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", columnDefinition = "datetime(6)", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at", columnDefinition = "datetime(6)")
    private Instant startedAt;

    @Column(name = "finished_at", columnDefinition = "datetime(6)")
    private Instant finishedAt;
}
