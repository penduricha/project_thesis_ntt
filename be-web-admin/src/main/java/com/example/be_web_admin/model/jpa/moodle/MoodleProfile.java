package com.example.be_web_admin.model.jpa.moodle;

import com.example.be_web_admin.converter.AnswerNumberingDConverter;
import com.example.be_web_admin.enums.moodle.AnswerNumberingD;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Bảng ntt_moodle_profiles — hồ sơ cấu hình cho mỗi lần xuất/nhập Moodle XML.
 *
 * <p>Cột sinh {@code default_key} không map; UNIQUE (default_key) bảo đảm tối đa 1 hồ sơ mặc định.
 * Muốn đổi hồ sơ mặc định: bỏ cờ hồ sơ cũ TRƯỚC rồi bật hồ sơ mới trong cùng transaction,
 * nếu không MariaDB báo trùng khoá.</p>
 */
@Entity
@Table(name = "ntt_moodle_profiles")
@Getter
@Setter
@NoArgsConstructor
public class MoodleProfile implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "profile_id", columnDefinition = "bigint", nullable = false, updatable = false)
    private Long profileId;

    @Column(name = "profile_name", length = 100, nullable = false)
    private String profileName;

    @Column(name = "moodle_base_url", length = 255)
    private String moodleBaseUrl;

    @Column(name = "category_prefix", length = 255)
    private String categoryPrefix;

    @Convert(converter = AnswerNumberingDConverter.class)
    @Column(name = "answer_numbering", columnDefinition = AnswerNumberingD.SQL_TYPE, nullable = false)
    private AnswerNumberingD answerNumbering = AnswerNumberingD.NONE;

    @Column(name = "shuffle_answers", columnDefinition = "bit(1)", nullable = false)
    private Boolean shuffleAnswers = Boolean.TRUE;

    @Column(name = "default_grade", precision = 6, scale = 3, columnDefinition = "decimal(6,3)", nullable = false)
    private BigDecimal defaultGrade = BigDecimal.ONE;

    @Column(name = "is_default", columnDefinition = "bit(1)", nullable = false)
    private Boolean isDefault = Boolean.FALSE;

    @Column(name = "created_by", columnDefinition = "bigint")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", columnDefinition = "datetime(6)", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", columnDefinition = "datetime(6)", nullable = false)
    private Instant updatedAt;
}
