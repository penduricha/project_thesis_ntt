package com.example.be_web_admin.model.redis;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Bản sao NHẸ của 1 task AI để giao diện polling mà không đụng MariaDB ở mỗi nhịp.
 *
 * <p>Key Redis: {@code ui:ai_job:{taskId}}, TTL 1 giờ (Redis tự xoá; DB vẫn giữ sự thật).
 * Trạng thái để dạng String (giá trị ENUM trong CSDL) thay vì enum Java: payload Redis không
 * đổi khi ta rename hằng số, và client Vue đọc trực tiếp được.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@RedisHash(value = "ui:ai_job", timeToLive = UiAiJobView.TTL_SECONDS)
public class UiAiJobView implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final int TTL_SECONDS = 3600;

    @Id
    private String taskId;

    private Long questionId;

    /** question_check | nl_search */
    private String taskType;

    /** pending | running | succeeded | failed | cancelled */
    private String status;

    private Integer attemptCount;

    private Integer suggestionCount;

    private String errorCode;

    private Instant createdAt;

    private Instant updatedAt;

    /** Gợi ý nhịp polling cho UI (ms): pending/running -> 1500, kết thúc -> 0. */
    private Integer pollAfterMs;
}
