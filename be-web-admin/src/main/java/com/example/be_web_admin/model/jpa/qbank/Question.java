package com.example.be_web_admin.model.jpa.qbank;

import com.example.be_web_admin.converter.ContentFormatDConverter;
import com.example.be_web_admin.converter.OptionLabelDConverter;
import com.example.be_web_admin.converter.QuestionSourceDConverter;
import com.example.be_web_admin.converter.QuestionStatusDConverter;
import com.example.be_web_admin.converter.ValidationStatusDConverter;
import com.example.be_web_admin.enums.qbank.ContentFormatD;
import com.example.be_web_admin.enums.qbank.OptionLabelD;
import com.example.be_web_admin.enums.qbank.QuestionSourceD;
import com.example.be_web_admin.enums.qbank.QuestionStatusD;
import com.example.be_web_admin.enums.qbank.ValidationStatusD;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Bảng ntt_questions — câu hỏi trắc nghiệm 1 đáp án đúng, 4 phương án A-D.
 *
 * <p>Ba điểm cần nhớ khi dùng entity này:</p>
 * <ul>
 *   <li>{@code version} là khoá lạc quan: mọi UPDATE qua API phải so phiên bản, lệch thì trả 409.</li>
 *   <li>{@code contentHash} do {@code QuestionContentHasher} tính (SHA-256 nội dung chuẩn hoá),
 *       KHÔNG unique trong DB — trùng chỉ là cảnh báo cho người biên soạn.</li>
 *   <li>Trigger {@code trg_question_bu_stale} tự đặt {@code validation_status = 'stale'} khi nội dung
 *       đổi; service vẫn phải xử lý tường minh để không phụ thuộc môi trường có trigger hay không.</li>
 * </ul>
 */
@Entity
@Table(name = "ntt_questions")
@Getter
@Setter
@NoArgsConstructor
public class Question implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "question_id", columnDefinition = "bigint", nullable = false, updatable = false)
    private Long questionId;

    /** FK ghép (subject_id, topic_id) ở DDL bảo đảm topic thuộc đúng môn. */
    @Column(name = "subject_id", columnDefinition = "bigint", nullable = false, updatable = false)
    private Long subjectId;

    @Column(name = "topic_id", columnDefinition = "bigint")
    private Long topicId;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "stem", columnDefinition = "text", nullable = false)
    private String stem;

    @Column(name = "option_a", columnDefinition = "text", nullable = false)
    private String optionA;

    @Column(name = "option_b", columnDefinition = "text", nullable = false)
    private String optionB;

    @Column(name = "option_c", columnDefinition = "text", nullable = false)
    private String optionC;

    @Column(name = "option_d", columnDefinition = "text", nullable = false)
    private String optionD;

    @Convert(converter = OptionLabelDConverter.class)
    @Column(name = "correct_label", columnDefinition = OptionLabelD.SQL_TYPE, nullable = false)
    private OptionLabelD correctLabel;

    @Convert(converter = ContentFormatDConverter.class)
    @Column(name = "content_format", columnDefinition = ContentFormatD.SQL_TYPE, nullable = false)
    private ContentFormatD contentFormat = ContentFormatD.PLAIN;

    @Column(name = "general_feedback", columnDefinition = "text")
    private String generalFeedback;

    /** 1..5; NULL = chưa đánh giá (CHECK chk_q_difficulty chỉ cho phép NULL hoặc 1..5). */
    @Column(name = "difficulty", columnDefinition = "tinyint")
    private Integer difficulty;

    @Convert(converter = QuestionStatusDConverter.class)
    @Column(name = "status", columnDefinition = QuestionStatusD.SQL_TYPE, nullable = false)
    private QuestionStatusD status = QuestionStatusD.ACTIVE;

    @Convert(converter = ValidationStatusDConverter.class)
    @Column(name = "validation_status", columnDefinition = ValidationStatusD.SQL_TYPE, nullable = false)
    private ValidationStatusD validationStatus = ValidationStatusD.NOT_CHECKED;

    @Column(name = "last_validated_at", columnDefinition = "datetime(6)")
    private Instant lastValidatedAt;

    @Convert(converter = QuestionSourceDConverter.class)
    @Column(name = "source", columnDefinition = QuestionSourceD.SQL_TYPE, nullable = false)
    private QuestionSourceD source = QuestionSourceD.MANUAL;

    @Column(name = "content_hash", length = 64, columnDefinition = "char(64)", nullable = false)
    private String contentHash;

    @Version
    @Column(name = "version", columnDefinition = "int", nullable = false)
    private Integer version;

    @Column(name = "created_by", columnDefinition = "bigint")
    private Long createdBy;

    @Column(name = "updated_by", columnDefinition = "bigint")
    private Long updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", columnDefinition = "datetime(6)", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", columnDefinition = "datetime(6)", nullable = false)
    private Instant updatedAt;
}
