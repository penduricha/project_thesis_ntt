package com.example.be_web_admin.model.jpa.eval;

import com.example.be_web_admin.converter.EvalSplitDConverter;
import com.example.be_web_admin.enums.eval.EvalSplitD;
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
 * Bảng ntt_eval_predictions — dự đoán của hệ thống cho 1 mẫu trong 1 run; đơn vị tính TP/FP/FN.
 *
 * <p>Hai FK ghép (run_id, split) và (split, item_id) bảo đảm prediction không lẫn split —
 * chống rò rỉ train/test ở tầng dữ liệu. UNIQUE (run_id, item_id, issue_type) chặn dự đoán trùng.</p>
 */
@Entity
@Table(name = "ntt_eval_predictions")
@Getter
@Setter
@NoArgsConstructor
public class EvalPrediction implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "prediction_id", columnDefinition = "bigint", nullable = false, updatable = false)
    private Long predictionId;

    @Column(name = "run_id", columnDefinition = "bigint", nullable = false, updatable = false)
    private Long runId;

    @Column(name = "item_id", columnDefinition = "bigint", nullable = false, updatable = false)
    private Long itemId;

    @Convert(converter = EvalSplitDConverter.class)
    @Column(name = "split", columnDefinition = EvalSplitD.SQL_TYPE, nullable = false, updatable = false)
    private EvalSplitD split;

    @Column(name = "issue_type", length = 20, nullable = false, updatable = false)
    private String issueType;

    /** Trỏ về đề xuất thật nếu run chạy qua hệ thống; NULL nếu chạy bằng script. */
    @Column(name = "suggestion_id", columnDefinition = "bigint")
    private Long suggestionId;

    @CreationTimestamp
    @Column(name = "created_at", columnDefinition = "datetime(6)", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", insertable = false, updatable = false)
    private EvalRun run;
}
