-- ============================================================================================
-- 04 - CẤP QUYỀN DB CHO 2 SERVER (TÙY CHỌN NHƯNG RẤT NÊN LÀM)
--
-- Mục tiêu: biến "Server 2 không được ghi bảng câu hỏi" từ QUY ƯỚC thành RÀNG BUỘC KỸ THUẬT.
-- Nếu worker viết sai code (hoặc bị lỗi), MariaDB sẽ từ chối thay vì ghi bẩn dữ liệu.
--
-- Đây là bằng chứng rất mạnh khi bảo vệ: có thể chạy câu lệnh cuối file để chứng minh
-- user ntt_s2 KHÔNG thể UPDATE ntt_questions.
-- ============================================================================================

-- --------------------------------------------------------------------------------------------
-- 1. Server 1 (be-web-admin): toàn quyền DML trên schema
-- --------------------------------------------------------------------------------------------
CREATE USER IF NOT EXISTS 'ntt_s1'@'%' IDENTIFIED BY 'CHANGE_ME_S1_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE ON ntt.* TO 'ntt_s1'@'%';

-- Lưu ý: KHÔNG cấp CREATE/ALTER/DROP. Schema chỉ được thay đổi bằng script trong
-- be-web-admin/sql/ chạy bởi tài khoản quản trị, không phải tài khoản ứng dụng.
-- Nhờ vậy spring.jpa.hibernate.ddl-auto = update sẽ THẤT BẠI (đúng ý muốn) và buộc phải
-- chuyển sang none/validate.

-- --------------------------------------------------------------------------------------------
-- 2. Server 2 (be-ai-worker): chỉ 3 bảng
-- --------------------------------------------------------------------------------------------
CREATE USER IF NOT EXISTS 'ntt_s2'@'%' IDENTIFIED BY 'CHANGE_ME_S2_PASSWORD';

-- 2.1 Đọc task (để claim + đọc input) và đọc danh mục loại lỗi
GRANT SELECT ON ntt.ntt_ai_tasks TO 'ntt_s2'@'%';
GRANT SELECT ON ntt.ntt_issue_types TO 'ntt_s2'@'%';

-- 2.2 Chỉ được UPDATE 16 CỘT THỰC THI của ntt_ai_tasks (tài liệu Spring Boot mục 9/11.2).
--     Nhờ cấp theo cột, worker không thể ghi question_id, input_json, created_by, eval_run_id.
GRANT UPDATE (status, attempt_count, started_at, finished_at, error_code, error_message,
              result_json, raw_response, is_json_valid, model_name, model_digest,
              runtime_version, prompt_version, params_json, usage_json, inference_ms)
    ON ntt.ntt_ai_tasks TO 'ntt_s2'@'%';

-- 2.3 Đề xuất: chỉ INSERT + SELECT (KHÔNG update — quyết định duyệt thuộc Server 1)
GRANT SELECT, INSERT ON ntt.ntt_ai_suggestions TO 'ntt_s2'@'%';

-- 2.4 Cố tình KHÔNG cấp gì cho: ntt_questions, ntt_question_revisions, ntt_subjects, ntt_topics,
--     ntt_moodle_*, ntt_eval_*, và 7 bảng xác thực.

FLUSH PRIVILEGES;

-- --------------------------------------------------------------------------------------------
-- 3. KIỂM CHỨNG (chạy khi bảo vệ — rất thuyết phục)
-- --------------------------------------------------------------------------------------------
-- 3.1 Xem quyền thực tế của worker:
--     SHOW GRANTS FOR 'ntt_s2'@'%';
--
-- 3.2 Thử ghi vào bảng câu hỏi bằng tài khoản worker (PHẢI bị từ chối):
--     mariadb -u ntt_s2 -p ntt -e "UPDATE ntt_questions SET stem='hack' WHERE question_id=1;"
--     -> ERROR 1142 (42000): UPDATE command denied to user 'ntt_s2'@'...' for table 'ntt_questions'
--
-- 3.3 Thử UPDATE một cột Server 1 sở hữu (PHẢI bị từ chối):
--     mariadb -u ntt_s2 -p ntt -e "UPDATE ntt_ai_tasks SET question_id=999 WHERE task_id='...';"
--     -> ERROR 1143: UPDATE command denied ... for column 'question_id'
--
-- 3.4 Thử UPDATE cột được phép (PHẢI thành công):
--     mariadb -u ntt_s2 -p ntt -e "UPDATE ntt_ai_tasks SET inference_ms=1234 WHERE task_id='...';"
--
-- --------------------------------------------------------------------------------------------
-- GHI CHÚ VỀ GRANT THEO CỘT
-- --------------------------------------------------------------------------------------------
-- MariaDB hỗ trợ GRANT UPDATE(col1, col2, ...) và đã hỗ trợ từ lâu. Nếu phiên bản đang dùng từ chối
-- cú pháp nhiều cột, dùng phương án B:
--
--   GRANT UPDATE ON ntt.ntt_ai_tasks TO 'ntt_s2'@'%';
--
-- và chấp nhận rằng việc giới hạn cột được bảo đảm bởi:
--   (a) entity AiTask khai báo updatable=false cho các cột của Server 1,
--   (b) @DynamicUpdate chỉ sinh UPDATE cho cột đã đổi,
--   (c) các @Modifying query trong AiTaskRepository chỉ liệt kê cột thực thi.
-- Tức là vẫn còn 3 lớp bảo vệ, chỉ mất lớp DB.
