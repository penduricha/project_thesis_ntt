package com.example.be_web_admin.model.jpa.moodle;

import com.example.be_web_admin.converter.ExchangeDirectionDConverter;
import com.example.be_web_admin.converter.ExchangeStatusDConverter;
import com.example.be_web_admin.converter.MoodleCheckDConverter;
import com.example.be_web_admin.enums.moodle.ExchangeDirectionD;
import com.example.be_web_admin.enums.moodle.ExchangeStatusD;
import com.example.be_web_admin.enums.moodle.MoodleCheckD;
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
 * Bảng ntt_moodle_exchanges — nhật ký 1 lần xuất hoặc nhập tệp Moodle XML.
 * Nội dung tệp KHÔNG lưu vào DB, chỉ lưu storage_ref + file_sha256.
 */
@Entity
@Table(name = "ntt_moodle_exchanges")
@Getter
@Setter
@NoArgsConstructor
public class MoodleExchange implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "exchange_id", columnDefinition = "bigint", nullable = false, updatable = false)
    private Long exchangeId;

    @Convert(converter = ExchangeDirectionDConverter.class)
    @Column(name = "direction", columnDefinition = ExchangeDirectionD.SQL_TYPE, nullable = false, updatable = false)
    private ExchangeDirectionD direction;

    @Convert(converter = ExchangeStatusDConverter.class)
    @Column(name = "status", columnDefinition = ExchangeStatusD.SQL_TYPE, nullable = false)
    private ExchangeStatusD status = ExchangeStatusD.PENDING;

    @Column(name = "profile_id", columnDefinition = "bigint", updatable = false)
    private Long profileId;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_size", columnDefinition = "bigint")
    private Long fileSize;

    @Column(name = "file_sha256", length = 64, columnDefinition = "char(64)")
    private String fileSha256;

    @Column(name = "storage_ref", length = 500)
    private String storageRef;

    @Convert(converter = MoodleCheckDConverter.class)
    @Column(name = "moodle_check", columnDefinition = MoodleCheckD.SQL_TYPE, nullable = false)
    private MoodleCheckD moodleCheck = MoodleCheckD.UNKNOWN;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_by", columnDefinition = "bigint")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", columnDefinition = "datetime(6)", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "finished_at", columnDefinition = "datetime(6)")
    private Instant finishedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", insertable = false, updatable = false)
    private MoodleProfile profile;
}
