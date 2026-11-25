-- =============================================================================
--  Tạo CSDL cho từng thành phần
-- =============================================================================
--  File này chạy TỰ ĐỘNG DUY NHẤT MỘT LẦN, khi volume mariadb_data còn trống
--  (lần đầu `docker compose up`). Chạy lại trên volume đã có dữ liệu thì
--  MariaDB BỎ QUA hoàn toàn — đây là hành vi của image chính thức, không phải
--  do file này. Nhờ vậy dữ liệu thật không bao giờ bị ghi đè.
--
--  Muốn chạy lại từ đầu:  docker compose down -v   (XOÁ TOÀN BỘ DỮ LIỆU)
--
--  LƯU Ý VỀ USER: image MariaDB đã tự tạo 'root'@'%' với toàn quyền nhờ biến
--  MARIADB_ROOT_HOST='%' trong docker-compose.yml. Vì vậy file này KHÔNG cần
--  tạo user hay cấp quyền — root@'%' đã có GRANT ALL ON *.* rồi.
--  Đây là cấu hình cho đồ án chạy nội bộ, xem ghi chú least-privilege trong
--  docker-compose.yml.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1) ntt_university — CSDL chính của be-web-admin (Spring điều phối)
-- -----------------------------------------------------------------------------
-- Ứng dụng tự tạo bảng nhờ JPA (hibernate.ddl-auto: update), nên ở đây chỉ
-- cần tạo database với charset đúng.
-- utf8mb4: cần cho tiếng Việt có dấu.
-- utf8mb4_unicode_ci: so sánh/sắp xếp đúng thứ tự chữ Việt
-- (khác utf8mb4_general_ci ở chỗ phân biệt a/ă/â đúng).
CREATE DATABASE IF NOT EXISTS `ntt_university`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 2) moodle_503 — CSDL của Moodle
-- -----------------------------------------------------------------------------
-- Tên này PHẢI khớp với:
--   - $CFG->dbname trong php_moodle_503/.../config.php
--   - MOODLE_DB_NAME trong deploy/.env
-- Moodle tự tạo ~400 bảng trong quá trình cài đặt.
-- Charset BẮT BUỘC utf8mb4: nếu không, tên khoá học và bài làm tiếng Việt
-- sẽ bị lỗi hiển thị hoặc mất dấu ngay từ bước cài.
CREATE DATABASE IF NOT EXISTS `moodle_503`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 3) ai_worker — CSDL riêng của server-ai-worker
-- -----------------------------------------------------------------------------
-- Chỉ chứa 2 bảng demo (demo_eval_run, demo_eval_item) do JPA tự tạo.
-- Tách khỏi ntt_university để khi thử nghiệm dữ liệu AI không làm ảnh hưởng
-- ngân hàng câu hỏi thật.
--
-- Đổi tên được qua AI_MARIADB_DBS trong deploy/.env — nhưng nếu đổi thì phải
-- sửa cả tên ở đây, nếu không CSDL sẽ không được tạo và ứng dụng báo lỗi
-- "Unknown database".
CREATE DATABASE IF NOT EXISTS `ai_worker`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;
