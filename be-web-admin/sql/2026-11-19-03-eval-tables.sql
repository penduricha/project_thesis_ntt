-- ============================================================================================
-- 03 - MODULE THỰC NGHIỆM (Chương 4.3) - TÙY CHỌN
--
-- 4 bảng này chỉ phục vụ đo chất lượng AI (Precision / Recall / F1 / kappa). Chạy script này
-- TRƯỚC khi chạy các kịch bản E1-E8; hệ thống chạy thật hoàn toàn không cần (eval_run_id = NULL).
--
-- Điểm khác biệt so với 02: mọi bảng đều có "split" DEV/TEST và FK ghép ép item, run,
-- prediction không được lẫn split với nhau. Đây là cơ chế chống rò rỉ dữ liệu
-- (train/test leakage) - nếu vô tình trộn, mọi con số ở Chương 4 mất giá trị.
-- ============================================================================================

SET NAMES utf8mb4;

-- 3.1 Một lần chạy thực nghiệm (tương ứng 1 kịch bản E1..E8)
CREATE TABLE IF NOT EXISTS ntt_eval_runs
(
    run_id          BIGINT       NOT NULL AUTO_INCREMENT,
    run_label       VARCHAR(100) NOT NULL COMMENT 'vd: E4_fewshot_cot_k5_seed0',
    scenario        ENUM ('E1','E2','E3','E4','E5','E6','E7','E8') NOT NULL,
    dataset_version VARCHAR(20)  NOT NULL COMMENT 'vd: 2026-11-ds300',
    split           ENUM ('dev','test') NOT NULL,
    method          ENUM ('rule_based','llm') NOT NULL,
    model_name      VARCHAR(100) NULL,
    model_digest    CHAR(64)     NULL COMMENT 'SHA-256 tệp GGUF, ghi lại để tái lập',
    runtime_version VARCHAR(30)  NULL COMMENT 'Phiên bản Ollama',
    gpu_type        VARCHAR(50)  NULL COMMENT 'vd: Tesla T4 16GB',
    prompt_version  VARCHAR(30)  NULL,
    params_json     JSON         NULL COMMENT 'temperature, seed, num_ctx, num_predict',
    seed            INT          NULL,
    started_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    finished_at     DATETIME(6)  NULL,
    note            VARCHAR(500) NULL,
    created_by      BIGINT       NULL,
    PRIMARY KEY (run_id),
    UNIQUE KEY uq_run_label (run_label),
    -- đích của FK ghép (run_id, split) từ eval_predictions
    UNIQUE KEY uq_run_scope (run_id, split),
    KEY ix_run_scenario (scenario, split),
    CONSTRAINT chk_run_llm_fields CHECK (
        method = 'rule_based'
            OR (model_name IS NOT NULL AND model_digest IS NOT NULL
            AND runtime_version IS NOT NULL AND gpu_type IS NOT NULL)),
    CONSTRAINT chk_run_finished CHECK (finished_at IS NULL OR finished_at >= started_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 3.2 Một mẫu trong bộ dữ liệu 300 câu (real = lấy từ giảng viên, injected = chèn lỗi có kiểm soát)
CREATE TABLE IF NOT EXISTS ntt_eval_items
(
    item_id               BIGINT       NOT NULL AUTO_INCREMENT,
    dataset_version       VARCHAR(20)  NOT NULL,
    question_id           BIGINT       NOT NULL,
    split                 ENUM ('dev','test') NOT NULL,
    origin                ENUM ('real','injected') NOT NULL DEFAULT 'real',
    injected_from_item_id BIGINT       NULL COMMENT 'Mẫu gốc đã dùng để chèn lỗi (nếu origin = injected)',
    gold_status           ENUM ('unlabeled','clean','has_errors') NOT NULL DEFAULT 'unlabeled',
    created_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (item_id),
    UNIQUE KEY uq_eval_dataset_question (dataset_version, question_id),
    -- đích của FK ghép (split, item_id) từ eval_predictions và (split, injected_from_item_id)
    UNIQUE KEY uq_eval_scope (split, item_id),
    KEY ix_eval_split_gold (split, gold_status),
    CONSTRAINT fk_eval_item_question FOREIGN KEY (question_id) REFERENCES ntt_questions (question_id),
    CONSTRAINT fk_eval_item_injected FOREIGN KEY (split, injected_from_item_id)
        REFERENCES ntt_eval_items (split, item_id)
        COMMENT 'Chống rò rỉ: mẫu chèn lỗi ở TEST không được sinh từ mẫu DEV (và ngược lại)',
    CONSTRAINT chk_eval_injected CHECK (
        (origin = 'injected' AND injected_from_item_id IS NOT NULL)
            OR (origin = 'real' AND injected_from_item_id IS NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 3.3 Nhãn do người gán (stage = annotator) và nhãn chốt cuối (stage = gold)
CREATE TABLE IF NOT EXISTS ntt_eval_labels
(
    label_id        BIGINT       NOT NULL AUTO_INCREMENT,
    item_id         BIGINT       NOT NULL,
    stage           ENUM ('annotator','gold') NOT NULL,
    annotator_code  VARCHAR(30)  NULL COMMENT 'Mã người gán (A1, A2) - KHÔNG lưu danh tính thật',
    issue_type      VARCHAR(20)  NOT NULL,
    note            VARCHAR(500) NULL,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    annotator_key   VARCHAR(30)  AS (IFNULL(annotator_code, 'gold')) STORED,
    PRIMARY KEY (label_id),
    UNIQUE KEY uq_label (item_id, stage, annotator_key, issue_type),
    KEY ix_label_item (item_id),
    CONSTRAINT fk_label_item FOREIGN KEY (item_id) REFERENCES ntt_eval_items (item_id),
    CONSTRAINT fk_label_issue FOREIGN KEY (issue_type) REFERENCES ntt_issue_types (issue_type),
    CONSTRAINT chk_label_gold_no_annotator CHECK (stage <> 'gold' OR annotator_code IS NULL),
    CONSTRAINT chk_label_none_only_annotator CHECK (issue_type <> 'none' OR stage = 'annotator')
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 3.4 Dự đoán của hệ thống cho 1 mẫu trong 1 run (đơn vị để tính TP/FP/FN)
CREATE TABLE IF NOT EXISTS ntt_eval_predictions
(
    prediction_id BIGINT      NOT NULL AUTO_INCREMENT,
    run_id        BIGINT      NOT NULL,
    item_id       BIGINT      NOT NULL,
    split         ENUM ('dev','test') NOT NULL,
    issue_type    VARCHAR(20) NOT NULL,
    suggestion_id BIGINT      NULL COMMENT 'Trỏ về đề xuất thật nếu run chạy qua hệ thống, không phải qua script',
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (prediction_id),
    UNIQUE KEY uq_pred (run_id, item_id, issue_type),
    KEY ix_pred_item (item_id),
    CONSTRAINT fk_pred_run FOREIGN KEY (run_id, split) REFERENCES ntt_eval_runs (run_id, split)
        COMMENT 'FK ghép: run TEST chỉ nhận item TEST',
    CONSTRAINT fk_pred_item FOREIGN KEY (split, item_id) REFERENCES ntt_eval_items (split, item_id),
    CONSTRAINT fk_pred_issue FOREIGN KEY (issue_type) REFERENCES ntt_issue_types (issue_type),
    CONSTRAINT fk_pred_suggestion FOREIGN KEY (suggestion_id) REFERENCES ntt_ai_suggestions (suggestion_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- --------------------------------------------------------------------------------------------
-- LIÊN KẾT CHÉO giữa module thực nghiệm và module chạy thật
-- (file 02 đã tạo ntt_ai_tasks.eval_run_id nhưng chưa khai báo FK vì eval_runs tạo sau)
-- --------------------------------------------------------------------------------------------
ALTER TABLE ntt_ai_tasks
    ADD CONSTRAINT fk_task_eval_run FOREIGN KEY (eval_run_id) REFERENCES ntt_eval_runs (run_id);

-- --------------------------------------------------------------------------------------------
-- VIEW hỗ trợ tính chỉ số (tuỳ chọn, dùng cho phần phân tích Chương 4)
-- Đếm TP/FP/FN theo nhóm lỗi dựa trên nhãn gold và dự đoán.
-- --------------------------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_eval_confusion AS
SELECT r.run_id,
       r.run_label,
       r.split,
       it.issue_type,
       SUM(CASE WHEN l.item_id IS NOT NULL AND p.item_id IS NOT NULL THEN 1 ELSE 0 END) AS tp,
       SUM(CASE WHEN l.item_id IS NULL AND p.item_id IS NOT NULL THEN 1 ELSE 0 END)     AS fp,
       SUM(CASE WHEN l.item_id IS NOT NULL AND p.item_id IS NULL THEN 1 ELSE 0 END)     AS fn
FROM ntt_eval_runs r
         CROSS JOIN ntt_issue_types it
         LEFT JOIN ntt_eval_predictions p
                   ON p.run_id = r.run_id AND p.issue_type = it.issue_type
         LEFT JOIN ntt_eval_labels l
                   ON l.item_id = p.item_id AND l.issue_type = it.issue_type AND l.stage = 'gold'
WHERE it.is_evaluated = b'1'
GROUP BY r.run_id, r.run_label, r.split, it.issue_type;
-- Lưu ý: view này chỉ đếm trên các cặp (item, issue_type) CÓ xuất hiện ở predictions.
-- Để tính FN đầy đủ (nhãn có nhưng không dự đoán gì) cần thêm nhánh UNION hoặc tính ở service
-- - xem docs/nghiep-vu/03-luong-nghiep-vu.md, mục "Công thức chỉ số".

-- --------------------------------------------------------------------------------------------
-- KIỂM TRA
-- --------------------------------------------------------------------------------------------
-- SELECT COUNT(*) FROM information_schema.tables
--  WHERE table_schema = DATABASE() AND table_name LIKE 'ntt_%';   -- kỳ vọng: 22 (21 bảng + 1 view không tính)
-- SELECT * FROM ntt_issue_types ORDER BY sort_order;
