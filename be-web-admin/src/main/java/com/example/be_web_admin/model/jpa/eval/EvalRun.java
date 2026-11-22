package com.example.be_web_admin.model.jpa.eval;

import com.example.be_web_admin.converter.EvalMethodDConverter;
import com.example.be_web_admin.converter.EvalScenarioDConverter;
import com.example.be_web_admin.converter.EvalSplitDConverter;
import com.example.be_web_admin.enums.eval.EvalMethodD;
import com.example.be_web_admin.enums.eval.EvalScenarioD;
import com.example.be_web_admin.enums.eval.EvalSplitD;
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

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Bảng ntt_eval_runs — 1 lần chạy thực nghiệm (kịch bản E1-E8), module TÙY CHỌN của Chương 4.
 * UNIQUE (run_id, split) là đích của FK ghép từ ntt_eval_predictions: run TEST chỉ nhận item TEST.
 */
@Entity
@Table(name = "ntt_eval_runs")
@Getter
@Setter
@NoArgsConstructor
public class EvalRun implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "run_id", columnDefinition = "bigint", nullable = false, updatable = false)
    private Long runId;

    @Column(name = "run_label", length = 100, nullable = false)
    private String runLabel;

    @Convert(converter = EvalScenarioDConverter.class)
    @Column(name = "scenario", columnDefinition = EvalScenarioD.SQL_TYPE, nullable = false)
    private EvalScenarioD scenario;

    @Column(name = "dataset_version", length = 20, nullable = false)
    private String datasetVersion;

    @Convert(converter = EvalSplitDConverter.class)
    @Column(name = "split", columnDefinition = EvalSplitD.SQL_TYPE, nullable = false)
    private EvalSplitD split;

    @Convert(converter = EvalMethodDConverter.class)
    @Column(name = "method", columnDefinition = EvalMethodD.SQL_TYPE, nullable = false)
    private EvalMethodD method;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "model_digest", length = 64, columnDefinition = "char(64)")
    private String modelDigest;

    @Column(name = "runtime_version", length = 30)
    private String runtimeVersion;

    @Column(name = "gpu_type", length = 50)
    private String gpuType;

    @Column(name = "prompt_version", length = 30)
    private String promptVersion;

    @Column(name = "params_json", columnDefinition = "json")
    private String paramsJson;

    @Column(name = "seed", columnDefinition = "int")
    private Integer seed;

    @CreationTimestamp
    @Column(name = "started_at", columnDefinition = "datetime(6)", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at", columnDefinition = "datetime(6)")
    private Instant finishedAt;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_by", columnDefinition = "bigint")
    private Long createdBy;
}
