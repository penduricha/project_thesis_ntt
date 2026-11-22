package com.example.be_web_admin.model.jpa.eval;

import com.example.be_web_admin.converter.EvalStageDConverter;
import com.example.be_web_admin.enums.eval.EvalStageD;
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
 * Bảng ntt_eval_labels — nhãn của người gán (stage = annotator, 2 người A1/A2 để tính kappa)
 * và nhãn chốt cuối (stage = gold, annotator_code phải NULL).
 *
 * <p>Cột sinh {@code annotator_key = IFNULL(annotator_code, 'gold')} không map; nó tồn tại chỉ để
 * UNIQUE (item_id, stage, annotator_key, issue_type) chặn trùng nhãn.</p>
 */
@Entity
@Table(name = "ntt_eval_labels")
@Getter
@Setter
@NoArgsConstructor
public class EvalLabel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "label_id", columnDefinition = "bigint", nullable = false, updatable = false)
    private Long labelId;

    @Column(name = "item_id", columnDefinition = "bigint", nullable = false, updatable = false)
    private Long itemId;

    @Convert(converter = EvalStageDConverter.class)
    @Column(name = "stage", columnDefinition = EvalStageD.SQL_TYPE, nullable = false, updatable = false)
    private EvalStageD stage;

    /** Mã người gán (A1, A2) — KHÔNG lưu danh tính thật. */
    @Column(name = "annotator_code", length = 30)
    private String annotatorCode;

    @Column(name = "issue_type", length = 20, nullable = false, updatable = false)
    private String issueType;

    @Column(name = "note", length = 500)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", columnDefinition = "datetime(6)", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", insertable = false, updatable = false)
    private EvalItem item;
}
