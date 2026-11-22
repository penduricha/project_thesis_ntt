package com.example.be_web_admin.model.jpa.moodle;

import com.example.be_web_admin.converter.ExchangeItemStatusDConverter;
import com.example.be_web_admin.enums.moodle.ExchangeItemStatusD;
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

import java.io.Serial;
import java.io.Serializable;

/**
 * Bảng ntt_moodle_exchange_items — từng câu trong 1 lần xuất/nhập.
 *
 * <p>question_revision_id để NULL được (item bị bỏ qua không có revision), nhưng nếu có thì
 * FK ghép (question_id, question_revision_id) bắt buộc revision thuộc đúng câu hỏi.</p>
 */
@Entity
@Table(name = "ntt_moodle_exchange_items")
@Getter
@Setter
@NoArgsConstructor
public class MoodleExchangeItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id", columnDefinition = "bigint", nullable = false, updatable = false)
    private Long itemId;

    @Column(name = "exchange_id", columnDefinition = "bigint", nullable = false, updatable = false)
    private Long exchangeId;

    @Column(name = "seq_no", columnDefinition = "int", nullable = false)
    private Integer seqNo;

    @Column(name = "question_id", columnDefinition = "bigint")
    private Long questionId;

    @Column(name = "question_revision_id", columnDefinition = "bigint")
    private Long questionRevisionId;

    /** Tên câu hỏi trong tệp Moodle (dùng khi nhập, trước khi map được sang câu hỏi nội bộ). */
    @Column(name = "source_name", length = 255)
    private String sourceName;

    @Convert(converter = ExchangeItemStatusDConverter.class)
    @Column(name = "item_status", columnDefinition = ExchangeItemStatusD.SQL_TYPE, nullable = false)
    private ExchangeItemStatusD itemStatus = ExchangeItemStatusD.OK;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exchange_id", insertable = false, updatable = false)
    private MoodleExchange exchange;
}
