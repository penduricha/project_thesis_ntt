-- ============================================================================================
-- 02 - 14 BẢNG NGHIỆP VỤ MỚI (ngân hàng câu hỏi + AI + Moodle + thực nghiệm)
--
-- Nguồn: CSDL_ER_Diagram_v4 (drawio v4, 21 bảng = 7 bảng xác thực giữ nguyên + 14 bảng mới).
-- File này là BẢN HIỆN THỰC HOÁ của sơ đồ đó, đã sửa các điểm sơ đồ còn để trống:
--
--   (a) Cột sinh (STORED GENERATED) có biểu thức cụ thể:
--       parent_key, active_flag, default_key, annotator_key
--   (b) Trigger đặt validation_status = 'stale' khi nội dung câu hỏi đổi
--   (c) CHECK constraint (MariaDB >= 10.2 mới enforce; 10.6 dùng được)
--   (d) Enum đã điền đủ thay cho "..." trong sơ đồ
--
-- QUY ƯỚC THỐNG NHẤT (áp dụng cho cả file 03):
--   - PK/FK bigint  = BIGINT (không phải bigint(20) - độ rộng hiển thị đã bỏ từ MariaDB 11)
--   - Thời gian     = datetime(6), LUÔN theo UTC (hibernate.jdbc.time_zone=UTC)
--   - Chuỗi tiếng Việt = utf8mb4_unicode_ci
--   - ON DELETE RESTRICT mặc định; chỉ 2 chỗ CASCADE (suggestions->tasks, exchange_items->exchanges)
--   - status/nghiệp vụ lưu CHỮ THƯỜNG (khác 7 bảng cũ đang dùng chữ HOA - xem ghi chú cuối file)
--   - Không có cột updated_at tự động; ứng dụng ghi tường minh (dễ kiểm soát hơn ON UPDATE)
--
-- THỨ TỰ TẠO BẢNG: cha trước, con sau (FK phụ thuộc).
-- ============================================================================================

SET NAMES utf8mb4;

-- --------------------------------------------------------------------------------------------
-- NHÓM 1: DANH MỤC (môn học, chủ đề, loại lỗi)
-- --------------------------------------------------------------------------------------------

-- 2.1 Môn học / học phần
CREATE TABLE IF NOT EXISTS ntt_subjects
(
    subject_id   BIGINT       NOT NULL AUTO_INCREMENT,
    subject_code VARCHAR(30)  NOT NULL COMMENT 'Mã môn, vd: ITE302c',
    subject_name VARCHAR(200) NOT NULL,
    description  VARCHAR(500) NULL,
    is_active    BIT(1)       NOT NULL DEFAULT b'1',
    created_by   BIGINT       NULL COMMENT 'FK -> ntt_admins.admin_id (không khai báo FK cứng: xóa admin không được kéo sập môn học)',
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (subject_id),
    UNIQUE KEY uq_subject_code (subject_code),
    KEY ix_subject_active (is_active, subject_name),
    CONSTRAINT chk_subject_not_blank CHECK (TRIM(subject_code) <> '' AND TRIM(subject_name) <> '')
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 2.2 Chủ đề phân cấp (cây) trong phạm vi 1 môn
--     parent_key: cột sinh để mô phỏng UNIQUE có điều kiện ở cấp gốc
--     (parent_id NULL -> parent_key = 0, nhờ vậy UNIQUE (subject_id, parent_key, topic_name)
--      vẫn chặn được 2 chủ đề gốc trùng tên trong cùng môn)
CREATE TABLE IF NOT EXISTS ntt_topics
(
    topic_id    BIGINT       NOT NULL AUTO_INCREMENT,
    subject_id  BIGINT       NOT NULL,
    parent_id   BIGINT       NULL,
    topic_name  VARCHAR(150) NOT NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    parent_key  BIGINT AS (IFNULL(parent_id, 0)) STORED,
    PRIMARY KEY (topic_id),
    UNIQUE KEY uq_topic (subject_id, parent_key, topic_name),
    -- đích của FK ghép (subject_id, parent_id) ở bảng con: ép cha và con cùng môn
    UNIQUE KEY uq_topic_scope (subject_id, topic_id),
    KEY ix_topic_subject (subject_id, sort_order),
    CONSTRAINT fk_topic_subject FOREIGN KEY (subject_id) REFERENCES ntt_subjects (subject_id),
    CONSTRAINT fk_topic_parent FOREIGN KEY (subject_id, parent_id)
        REFERENCES ntt_topics (subject_id, topic_id),
    CONSTRAINT chk_topic_not_blank CHECK (TRIM(topic_name) <> ''),
    CONSTRAINT chk_topic_no_self_parent CHECK (parent_id IS NULL OR parent_id <> topic_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
-- Lưu ý: chống CHU TRÌNH (A->B->C->A) không làm được bằng CHECK/FK. Kiểm ở TopicService
-- bằng CTE đệ quy khi đổi parent_id (xem docs/nghiep-vu/02-ke-hoach-mo-rong-csdl.md mục 7).

-- 2.3 Loại lỗi (danh mục dùng chung cho AI và nhãn thực nghiệm)
--     Đồng bộ với enum error_type trong notebook Qwen: spelling / logic / presentation / none
--     (+ 2 giá trị mở rộng: distractor = phương án nhiễu kém, style = diễn đạt chưa rõ)
CREATE TABLE IF NOT EXISTS ntt_issue_types
(
    issue_type   VARCHAR(20)  NOT NULL,
    label_vi     VARCHAR(100) NOT NULL,
    is_evaluated BIT(1)       NOT NULL DEFAULT b'1' COMMENT 'Có nằm trong bộ nhãn dùng để tính P/R/F1 hay không',
    sort_order   INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (issue_type)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

INSERT INTO ntt_issue_types (issue_type, label_vi, is_evaluated, sort_order)
VALUES ('spelling', 'Lỗi chính tả', 1, 10),
       ('grammar', 'Lỗi ngữ pháp', 1, 20),
       ('presentation', 'Trình bày / diễn đạt chưa rõ', 1, 30),
       ('logic', 'Logic câu dẫn - đáp án không khớp', 1, 40),
       ('distractor', 'Phương án nhiễu kém', 1, 50),
       ('style', 'Văn phong / nhất quán thuật ngữ', 1, 60),
       ('key_doubt', 'Nghi ngờ đáp án đúng bị gán nhầm', 0, 70),
       ('none', 'Không phát hiện lỗi (đã duyệt mẫu)', 0, 900)
ON DUPLICATE KEY UPDATE label_vi = VALUES(label_vi),
                        is_evaluated = VALUES(is_evaluated),
                        sort_order = VALUES(sort_order);
-- 'none' có is_evaluated = 0: dùng để ghi nhận "người gán đã xem và kết luận không lỗi",
-- không được tính như một nhãn lỗi khi tính Precision/Recall.

-- --------------------------------------------------------------------------------------------
-- NHÓM 2: CÂU HỎI VÀ LỊCH SỬ SỬA
-- --------------------------------------------------------------------------------------------

-- 2.4 Câu hỏi trắc nghiệm 1 đáp án đúng, 4 phương án A-D (đúng phạm vi đề cương 1.6)
CREATE TABLE IF NOT EXISTS ntt_questions
(
    question_id       BIGINT       NOT NULL AUTO_INCREMENT,
    subject_id        BIGINT       NOT NULL,
    topic_id          BIGINT       NULL,
    title             VARCHAR(255) NULL COMMENT 'Tiêu đề ngắn để tra cứu, không bắt buộc',
    stem              TEXT         NOT NULL COMMENT 'Nội dung câu dẫn, có thể chứa "_____"',
    option_a          TEXT         NOT NULL,
    option_b          TEXT         NOT NULL,
    option_c          TEXT         NOT NULL,
    option_d          TEXT         NOT NULL,
    correct_label     ENUM ('A','B','C','D') NOT NULL,
    content_format    ENUM ('plain','html') NOT NULL DEFAULT 'plain',
    general_feedback  TEXT         NULL COMMENT 'Lời giải / nhận xét chung hiển thị cho người học',
    difficulty        TINYINT      NULL COMMENT '1..5; NULL = chưa đánh giá',
    status            ENUM ('active','archived') NOT NULL DEFAULT 'active',
    validation_status ENUM ('not_checked','clean','has_issues','failed','stale') NOT NULL DEFAULT 'not_checked',
    last_validated_at DATETIME(6)  NULL,
    source            ENUM ('manual','moodle_import','eval') NOT NULL DEFAULT 'manual',
    content_hash      CHAR(64)     NOT NULL COMMENT 'SHA-256 của nội dung chuẩn hoá; dùng để cảnh báo trùng',
    version           INT          NOT NULL DEFAULT 0 COMMENT 'Khoá lạc quan @Version của Hibernate',
    created_by        BIGINT       NULL,
    updated_by        BIGINT       NULL,
    created_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (question_id),
    -- đích của FK ghép (subject_id, topic_id) ở bảng con: ép câu hỏi và chủ đề cùng môn
    UNIQUE KEY uq_question_scope (subject_id, question_id),
    KEY ix_q_scope (subject_id, topic_id, status),
    KEY ix_q_hash (subject_id, content_hash),
    KEY ix_q_validation (subject_id, validation_status),
    FULLTEXT KEY ft_q_text (stem, option_a, option_b, option_c, option_d),
    CONSTRAINT fk_q_subject FOREIGN KEY (subject_id) REFERENCES ntt_subjects (subject_id),
    CONSTRAINT fk_q_topic FOREIGN KEY (subject_id, topic_id)
        REFERENCES ntt_topics (subject_id, topic_id),
    CONSTRAINT chk_q_difficulty CHECK (difficulty IS NULL OR difficulty BETWEEN 1 AND 5),
    CONSTRAINT chk_q_not_blank CHECK (
        TRIM(stem) <> '' AND TRIM(option_a) <> '' AND TRIM(option_b) <> ''
            AND TRIM(option_c) <> '' AND TRIM(option_d) <> ''),
    CONSTRAINT chk_q_hash_len CHECK (CHAR_LENGTH(content_hash) = 64)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
-- content_hash CHỈ index, KHÔNG unique: trùng nội dung là cảnh báo cho người biên soạn
-- (UC05/UC06 hiển thị "câu hỏi tương tự"), không phải lỗi chặn lưu.

-- 2.5 Lịch sử phiên bản của câu hỏi (append-only, mỗi lần sửa 1 dòng)
CREATE TABLE IF NOT EXISTS ntt_question_revisions
(
    revision_id   BIGINT       NOT NULL AUTO_INCREMENT,
    question_id   BIGINT       NOT NULL,
    rev_no        INT          NOT NULL COMMENT 'Bắt đầu từ 1, tăng dần theo từng câu hỏi',
    snapshot_json JSON         NOT NULL COMMENT 'Toàn bộ nội dung câu hỏi tại thời điểm rev_no',
    change_source ENUM ('create','manual','ai_suggestion','moodle_import','archive','restore') NOT NULL,
    change_note   VARCHAR(255) NULL,
    changed_by    BIGINT       NULL,
    changed_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (revision_id),
    UNIQUE KEY uq_rev (question_id, rev_no),
    -- đích của FK ghép (question_id, question_revision_id) ở bảng AI/Moodle/đề xuất
    UNIQUE KEY uq_rev_scope (question_id, revision_id),
    KEY ix_rev_question_time (question_id, changed_at),
    CONSTRAINT fk_rev_question FOREIGN KEY (question_id) REFERENCES ntt_questions (question_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- --------------------------------------------------------------------------------------------
-- NHÓM 3: AI (task bất đồng bộ + đề xuất do AI sinh ra)
-- --------------------------------------------------------------------------------------------

-- 2.6 Tác vụ AI (hàng đợi bền, trạng thái sống ở đây - Redis chỉ là hàng đợi tạm)
CREATE TABLE IF NOT EXISTS ntt_ai_tasks
(
    task_id              CHAR(36)     NOT NULL COMMENT 'UUID do Server 1 sinh',
    task_type            ENUM ('question_check','nl_search') NOT NULL,
    question_id          BIGINT       NULL,
    question_revision_id BIGINT       NULL COMMENT 'Task gắn với 1 revision cụ thể, không gắn "câu hỏi hiện tại"',
    status               ENUM ('pending','running','succeeded','failed','cancelled') NOT NULL DEFAULT 'pending',
    attempt_count        TINYINT      NOT NULL DEFAULT 1,
    error_code           VARCHAR(50)  NULL,
    error_message        VARCHAR(500) NULL,
    input_json           JSON         NOT NULL COMMENT 'Bản sao nội dung câu hỏi lấy từ snapshot_json của đúng revision',
    result_json          JSON         NULL COMMENT 'Chỉ có khi JSON hợp lệ',
    raw_response         LONGTEXT     NULL COMMENT 'Phản hồi thô, lưu cả khi JSON hỏng; projection không kéo cột này',
    is_json_valid        BIT(1)       NULL,
    model_name           VARCHAR(100) NULL,
    model_digest         CHAR(64)     NULL,
    runtime_version      VARCHAR(30)  NULL,
    prompt_version       VARCHAR(30)  NULL,
    params_json          JSON         NULL COMMENT 'num_ctx, temperature, seed, num_predict...',
    usage_json           JSON         NULL,
    inference_ms         INT          NULL,
    eval_run_id          BIGINT       NULL COMMENT 'NULL = task chạy thật; khác NULL = thuộc module thực nghiệm',
    created_by           BIGINT       NULL,
    created_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    started_at           DATETIME(6)  NULL,
    finished_at          DATETIME(6)  NULL,
    -- Cột SINH: = 1 khi task còn sống (pending/running), NULL khi đã kết thúc.
    -- Nhờ NULL mà UNIQUE bên dưới chỉ chặn "2 task sống cùng lúc cho 1 câu + 1 loại",
    -- còn các task đã kết thúc (NULL) được phép trùng nhau vô hạn => chạy lại bao nhiêu lần cũng được.
    active_flag          TINYINT AS (IF(status IN ('pending','running'), 1, NULL)) STORED,
    PRIMARY KEY (task_id),
    UNIQUE KEY uq_one_active_task (question_id, task_type, active_flag),
    KEY ix_task_status_created (status, created_at) COMMENT 'Job quét task kẹt running',
    KEY ix_task_question (question_id, created_at),
    KEY ix_task_eval_run (eval_run_id),
    CONSTRAINT fk_task_question FOREIGN KEY (question_id) REFERENCES ntt_questions (question_id),
    CONSTRAINT fk_task_revision FOREIGN KEY (question_id, question_revision_id)
        REFERENCES ntt_question_revisions (question_id, revision_id),
    CONSTRAINT chk_task_attempt CHECK (attempt_count BETWEEN 1 AND 5),
    CONSTRAINT chk_task_failed_needs_code CHECK (status <> 'failed' OR error_code IS NOT NULL),
    CONSTRAINT chk_task_finished CHECK (
        status IN ('pending','running') OR finished_at IS NOT NULL),
    CONSTRAINT chk_task_question_check CHECK (
        (task_type = 'question_check' AND question_id IS NOT NULL AND question_revision_id IS NOT NULL)
            OR (task_type = 'nl_search' AND question_id IS NULL AND question_revision_id IS NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
-- active_flag là CỘT SINH (insertable=false, updatable=false ở entity): ứng dụng KHÔNG ghi cột này.
-- MariaDB không cho CHECK tham chiếu cột sinh => mọi ràng buộc về status phải viết ở service.
-- Task 'nl_search' có question_id NULL nên UNIQUE không chặn được (UNIQUE cho phép nhiều NULL);
-- chống bấm liên tục bằng khoá Redis ai:rl:{admin_id}, đúng như tài liệu Spring Boot mục 11.5.

-- 2.7 Đề xuất của AI cho 1 task (mỗi đề xuất = 1 dòng, bất biến sau khi chèn)
CREATE TABLE IF NOT EXISTS ntt_ai_suggestions
(
    suggestion_id       BIGINT       NOT NULL AUTO_INCREMENT,
    task_id             CHAR(36)     NOT NULL,
    seq_no              SMALLINT     NOT NULL COMMENT 'Bắt đầu từ 1',
    issue_type          VARCHAR(20)  NOT NULL,
    severity            ENUM ('low','medium','high') NOT NULL DEFAULT 'medium',
    target_field        ENUM ('question','stem','option_a','option_b','option_c','option_d') NOT NULL,
    original_text       TEXT         NULL COMMENT 'Đoạn văn bản gốc mà AI chỉ ra',
    span_start          INT          NULL COMMENT 'Vị trí tính bằng indexOf ở server, NULL nếu không tìm thấy',
    span_end            INT          NULL,
    explanation         TEXT         NOT NULL,
    proposed_text       TEXT         NULL,
    suggested_difficulty TINYINT     NULL,
    decision            ENUM ('pending','accepted','rejected','stale') NOT NULL DEFAULT 'pending',
    decided_by          BIGINT       NULL,
    decided_at          DATETIME(6)  NULL,
    reject_reason       VARCHAR(255) NULL,
    final_text          TEXT         NULL COMMENT 'Nội dung người duyệt chốt trước khi áp dụng',
    applied_revision_id BIGINT       NULL,
    created_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (suggestion_id),
    UNIQUE KEY uq_sugg_seq (task_id, seq_no),
    KEY ix_sugg_task_decision (task_id, decision),
    KEY ix_sugg_issue (issue_type),
    CONSTRAINT fk_sugg_task FOREIGN KEY (task_id) REFERENCES ntt_ai_tasks (task_id) ON DELETE CASCADE,
    CONSTRAINT fk_sugg_issue FOREIGN KEY (issue_type) REFERENCES ntt_issue_types (issue_type),
    CONSTRAINT fk_sugg_revision FOREIGN KEY (applied_revision_id)
        REFERENCES ntt_question_revisions (revision_id),
    CONSTRAINT chk_sugg_span CHECK (
        (span_start IS NULL AND span_end IS NULL)
            OR (span_start >= 0 AND span_end > span_start)),
    CONSTRAINT chk_sugg_difficulty CHECK (suggested_difficulty IS NULL OR suggested_difficulty BETWEEN 1 AND 5),
    CONSTRAINT chk_sugg_decided CHECK (
        decision IN ('pending','stale') OR (decided_by IS NOT NULL AND decided_at IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
-- target_field KHÔNG có 'correct_label': theo đề cương 3.3, AI không được đổi đáp án đúng.
-- Bất biến này được thực thi ngay ở tầng dữ liệu, không chỉ ở service.

-- --------------------------------------------------------------------------------------------
-- NHÓM 4: MOODLE (cấu hình kết nối + nhật ký xuất/nhập)
-- --------------------------------------------------------------------------------------------

-- 2.8 Hồ sơ cấu hình Moodle (mỗi lần xuất/nhập dùng 1 hồ sơ)
CREATE TABLE IF NOT EXISTS ntt_moodle_profiles
(
    profile_id      BIGINT       NOT NULL AUTO_INCREMENT,
    profile_name    VARCHAR(100) NOT NULL,
    moodle_base_url VARCHAR(255) NULL,
    category_prefix VARCHAR(255) NULL,
    answer_numbering ENUM ('none','abc','ABCD','123') NOT NULL DEFAULT 'none',
    shuffle_answers BIT(1)       NOT NULL DEFAULT b'1',
    default_grade   DECIMAL(6,3) NOT NULL DEFAULT 1.000,
    is_default      BIT(1)       NOT NULL DEFAULT b'0',
    default_key     TINYINT AS (IF(is_default = 1, 1, NULL)) STORED,
    created_by      BIGINT       NULL,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (profile_id),
    UNIQUE KEY uq_profile_name (profile_name),
    UNIQUE KEY uq_single_default_profile (default_key) COMMENT 'Tối đa 1 hồ sơ mặc định',
    CONSTRAINT chk_profile_grade CHECK (default_grade > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 2.9 Nhật ký 1 lần xuất hoặc nhập tệp Moodle XML
CREATE TABLE IF NOT EXISTS ntt_moodle_exchanges
(
    exchange_id   BIGINT       NOT NULL AUTO_INCREMENT,
    direction     ENUM ('export','import') NOT NULL,
    status        ENUM ('pending','succeeded','partial','failed') NOT NULL DEFAULT 'pending',
    profile_id    BIGINT       NULL,
    file_name     VARCHAR(255) NULL,
    file_size     BIGINT       NULL,
    file_sha256   CHAR(64)     NULL,
    storage_ref   VARCHAR(500) NULL COMMENT 'Đường dẫn/khóa lưu tệp, KHÔNG lưu nội dung vào DB',
    moodle_check  ENUM ('unknown','ok','failed') NOT NULL DEFAULT 'unknown' COMMENT 'Kết quả nhập thử vào Moodle thật',
    error_message VARCHAR(500) NULL,
    created_by    BIGINT       NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    finished_at   DATETIME(6)  NULL,
    PRIMARY KEY (exchange_id),
    KEY ix_exchange_created (created_at),
    KEY ix_exchange_status (status, created_at),
    CONSTRAINT fk_exchange_profile FOREIGN KEY (profile_id) REFERENCES ntt_moodle_profiles (profile_id),
    CONSTRAINT chk_exchange_finished CHECK (status = 'pending' OR finished_at IS NOT NULL)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 2.10 Chi tiết từng câu hỏi trong 1 lần xuất/nhập
CREATE TABLE IF NOT EXISTS ntt_moodle_exchange_items
(
    item_id              BIGINT       NOT NULL AUTO_INCREMENT,
    exchange_id          BIGINT       NOT NULL,
    seq_no               INT          NOT NULL,
    question_id          BIGINT       NULL,
    question_revision_id BIGINT       NULL,
    source_name          VARCHAR(255) NULL COMMENT 'Tên câu hỏi trong tệp Moodle (khi nhập)',
    item_status          ENUM ('ok','skipped','failed') NOT NULL DEFAULT 'ok',
    error_code           VARCHAR(50)  NULL,
    error_message        VARCHAR(500) NULL,
    PRIMARY KEY (item_id),
    UNIQUE KEY uq_exchange_item_seq (exchange_id, seq_no),
    UNIQUE KEY uq_exchange_question (exchange_id, question_id),
    KEY ix_exchange_item_question (question_id),
    CONSTRAINT fk_item_exchange FOREIGN KEY (exchange_id) REFERENCES ntt_moodle_exchanges (exchange_id) ON DELETE CASCADE,
    CONSTRAINT fk_item_question FOREIGN KEY (question_id) REFERENCES ntt_questions (question_id),
    CONSTRAINT fk_item_revision FOREIGN KEY (question_id, question_revision_id)
        REFERENCES ntt_question_revisions (question_id, revision_id),
    CONSTRAINT chk_item_revision_needs_question CHECK (
        question_revision_id IS NULL OR question_id IS NOT NULL)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- --------------------------------------------------------------------------------------------
-- TRIGGER: khi nội dung câu hỏi đổi, kết quả kiểm định cũ không còn giá trị
--
-- Đây là phần ER v4 ghi "TRG trg_question_bu_stale" nhưng CHƯA có thân trigger (đề án
-- mục 11.7 cũng ghi "thân trigger chưa thấy"). Bản dưới đây là đề xuất cụ thể.
--
-- TẠI SAO KHÔNG dùng ON UPDATE CURRENT_TIMESTAMP: để trigger tự quyết định "đổi nội dung"
-- (so sánh từng cột văn bản), không dựa vào updated_at - vì chính việc AI ghi
-- validation_status cũng làm updated_at đổi, sẽ tạo vòng lặp stale vĩnh viễn.
-- --------------------------------------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_question_bu_stale;

DELIMITER $$
CREATE TRIGGER trg_question_bu_stale
    BEFORE UPDATE
    ON ntt_questions
    FOR EACH ROW
BEGIN
    IF (NOT (NEW.stem <=> OLD.stem)
        OR NOT (NEW.option_a <=> OLD.option_a)
        OR NOT (NEW.option_b <=> OLD.option_b)
        OR NOT (NEW.option_c <=> OLD.option_c)
        OR NOT (NEW.option_d <=> OLD.option_d)
        OR NOT (NEW.correct_label <=> OLD.correct_label)
        OR NOT (NEW.difficulty <=> OLD.difficulty))
        AND NEW.validation_status = OLD.validation_status THEN
        -- Người/AI sửa nội dung mà không chủ động đổi trạng thái => kết quả cũ (clean/has_issues)
        -- trở thành stale. Nếu caller CỐ Ý đặt not_checked/clean mới, tôn trọng ý caller.
        SET NEW.validation_status = 'stale';
    END IF;
END$$
DELIMITER ;

-- Đánh dấu các đề xuất còn pending của câu hỏi đó thành stale.
-- Chạy AFTER UPDATE của ntt_questions nên nhìn thấy trạng thái cuối cùng.
DROP TRIGGER IF EXISTS trg_question_au_stale_suggestions;

DELIMITER $$
CREATE TRIGGER trg_question_au_stale_suggestions
    AFTER UPDATE
    ON ntt_questions
    FOR EACH ROW
BEGIN
    IF NEW.validation_status = 'stale' AND OLD.validation_status <> 'stale' THEN
        UPDATE ntt_ai_suggestions s
            JOIN ntt_ai_tasks t ON t.task_id = s.task_id
        SET s.decision = 'stale'
        WHERE t.question_id = NEW.question_id
          AND s.decision = 'pending';
    END IF;
END$$
DELIMITER ;

-- --------------------------------------------------------------------------------------------
-- GHI CHÚ QUAN TRỌNG: CHỮ HOA / CHỮ THƯỜNG
--
-- 7 bảng cũ dùng chữ HOA (ADMIN, ACTIVE, ...) vì @Enumerated(STRING) trên enum Java viết hoa.
-- 14 bảng mới ở trên dùng chữ THƯỜNG (active, clean, ...) theo đúng ER v4.
-- Hai quy ước cùng tồn tại trong 1 schema là chấp nhận được NHƯNG phải thống nhất ở tầng
-- Java: enum nghiệp vụ mới viết thường (hoặc dùng AttributeConverter).
-- Khuyến nghị: viết hoa toàn bộ enum ở Java rồi để CHECK/ENUM trong DB theo chữ thường →
-- SAI. Chọn MỘT: hoặc sửa ER thành chữ HOA, hoặc thêm converter.
-- Xem docs/nghiep-vu/02-ke-hoach-mo-rong-csdl.md mục 7 "quy ước enum".
-- --------------------------------------------------------------------------------------------
