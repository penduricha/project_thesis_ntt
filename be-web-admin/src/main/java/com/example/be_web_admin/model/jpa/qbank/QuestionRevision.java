package com.example.be_web_admin.model.jpa.qbank;

import com.example.be_web_admin.converter.ChangeSourceDConverter;
import com.example.be_web_admin.enums.qbank.ChangeSourceD;
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
 * Bảng ntt_question_revisions — append-only, mỗi lần sửa thêm 1 dòng.
 *
 * <p>UNIQUE (question_id, revision_id) là đích của các FK ghép từ ntt_ai_tasks,
 * ntt_moodle_exchange_items và ntt_ai_suggestions.applied_revision_id. Đây cũng là lý do
 * luồng tạo câu hỏi BẮT BUỘC sinh rev_no = 1 ngay trong cùng transaction, nếu không mọi
 * task question_check sẽ vi phạm chk_task_question_check (xem D3 trong tài liệu đối chiếu).</p>
 */
@Entity
@Table(name = "ntt_question_revisions")
@Getter
@Setter
@NoArgsConstructor
public class QuestionRevision implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "revision_id", columnDefinition = "bigint", nullable = false, updatable = false)
    private Long revisionId;

    @Column(name = "question_id", columnDefinition = "bigint", nullable = false, updatable = false)
    private Long questionId;

    @Column(name = "rev_no", columnDefinition = "int", nullable = false)
    private Integer revNo;

    /** Ảnh chụp toàn bộ nội dung câu hỏi tại rev_no (JSON), nguồn của ntt_ai_tasks.input_json. */
    @Column(name = "snapshot_json", columnDefinition = "json", nullable = false)
    private String snapshotJson;

    @Convert(converter = ChangeSourceDConverter.class)
    @Column(name = "change_source", columnDefinition = ChangeSourceD.SQL_TYPE, nullable = false)
    private ChangeSourceD changeSource;

    @Column(name = "change_note", length = 255)
    private String changeNote;

    /** FK mềm tới ntt_admins (NULL = job hệ thống/import). */
    @Column(name = "changed_by", columnDefinition = "bigint")
    private Long changedBy;

    @CreationTimestamp
    @Column(name = "changed_at", columnDefinition = "datetime(6)", nullable = false, updatable = false)
    private Instant changedAt;

    /** Liên kết chỉ-đọc để lấy câu hỏi cha khi cần; cột question_id vẫn do questionId quản lý. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", insertable = false, updatable = false)
    private Question question;
}
