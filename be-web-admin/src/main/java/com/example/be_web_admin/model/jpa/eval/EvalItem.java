package com.example.be_web_admin.model.jpa.eval;

import com.example.be_web_admin.converter.EvalGoldStatusDConverter;
import com.example.be_web_admin.converter.EvalOriginDConverter;
import com.example.be_web_admin.converter.EvalSplitDConverter;
import com.example.be_web_admin.enums.eval.EvalGoldStatusD;
import com.example.be_web_admin.enums.eval.EvalOriginD;
import com.example.be_web_admin.enums.eval.EvalSplitD;
import com.example.be_web_admin.model.jpa.qbank.Question;
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
 * Bảng ntt_eval_items — một mẫu của bộ dữ liệu thực nghiệm (300 câu, DEV/TEST).
 *
 * <p>CẢNH BÁO thiết kế (D1/D2 trong docs/review-csdl-v4-chot-vs-code.md): bảng chỉ trỏ
 * question_id, KHÔNG trỏ revision và question_id là NOT NULL. Nghĩa là mẫu eval buộc phải
 * nằm trong ngân hàng thật và có thể bị "trôi" nếu câu hỏi bị sửa sau khi gán nhãn. Khi bổ
 * sung cột question_revision_id/snapshot_json theo đề xuất, chỉ cần thêm field vào entity này.</p>
 */
@Entity
@Table(name = "ntt_eval_items")
@Getter
@Setter
@NoArgsConstructor
public class EvalItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id", columnDefinition = "bigint", nullable = false, updatable = false)
    private Long itemId;

    @Column(name = "dataset_version", length = 20, nullable = false, updatable = false)
    private String datasetVersion;

    @Column(name = "question_id", columnDefinition = "bigint", nullable = false, updatable = false)
    private Long questionId;

    @Convert(converter = EvalSplitDConverter.class)
    @Column(name = "split", columnDefinition = EvalSplitD.SQL_TYPE, nullable = false, updatable = false)
    private EvalSplitD split;

    @Convert(converter = EvalOriginDConverter.class)
    @Column(name = "origin", columnDefinition = EvalOriginD.SQL_TYPE, nullable = false)
    private EvalOriginD origin = EvalOriginD.REAL;

    /** Mẫu gốc đã dùng để chèn lỗi; FK ghép (split, injected_from_item_id) chống rò rỉ DEV/TEST. */
    @Column(name = "injected_from_item_id", columnDefinition = "bigint")
    private Long injectedFromItemId;

    @Convert(converter = EvalGoldStatusDConverter.class)
    @Column(name = "gold_status", columnDefinition = EvalGoldStatusD.SQL_TYPE, nullable = false)
    private EvalGoldStatusD goldStatus = EvalGoldStatusD.UNLABELED;

    @CreationTimestamp
    @Column(name = "created_at", columnDefinition = "datetime(6)", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", insertable = false, updatable = false)
    private Question question;
}
