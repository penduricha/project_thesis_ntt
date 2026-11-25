-- =============================================================================
--  Tạo 3 CSDL cho 3 thành phần
-- =============================================================================
--  File này chạy TỰ ĐỘNG DUY NHẤT MỘT LẦN, khi volume mariadb_data còn trống
--  (lần đầu `docker compose up`). Chạy lại trên volume đã có dữ liệu thì
--  MariaDB BỎ QUA hoàn toàn — đây là hành vi của image chính thức, không
--  phải do file này. Nhờ vậy dữ liệu thật không bao giờ bị ghi đè.
--
--  Muốn chạy lại từ đầu: docker compose down -v  (XOÁ TOÀN BỘ DỮ LIỆU)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1) ntt_thesis — CSDL chính của be-web-admin
-- -----------------------------------------------------------------------------
-- Ứng dụng đã tự tạo bảng nhờ JPA (hibernate.ddl-auto: update), nên ở đây
-- chỉ cần tạo database với charset đúng.
-- utf8mb4: cần cho tiếng Việt có dấu; utf8mb4_unicode_ci: so sánh/ sắp xếp
-- đúng thứ tự chữ Việt.
CREATE DATABASE IF NOT EXISTS `ntt_thesis`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 2) ntt_demo_llm — CSDL riêng của server-ai-worker
-- -----------------------------------------------------------------------------
-- Worker có 2 bảng demo (demo_eval_run, demo_eval_item) tự tạo bằng JPA.
-- Tách riêng DATABASE để khi xoá/thử nghiệm dữ liệu AI không làm ảnh hưởng
-- ngân hàng câu hỏi thật.
CREATE DATABASE IF NOT EXISTS `ntt_demo_llm`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 3) moodle_local — CSDL của Moodle
-- -----------------------------------------------------------------------------
-- Moodle tự tạo ~400 bảng trong quá trình cài đặt. Charset BẮT BUỘC là
-- utf8mb4, nếu không tiếng Việt trong tên khoá học và bài làm sẽ bị lỗi.
CREATE DATABASE IF NOT EXISTS `moodle_local`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- Cấp quyền
-- -----------------------------------------------------------------------------
-- User "${MARIADB_USERNAME}" (mặc định: ntt) đã được image MariaDB tạo sẵn
-- và cấp quyền trên MARIADB_DATABASE (ntt_thesis). Nhưng nó CHƯA có quyền
-- trên 2 CSDL vừa tạo ở trên, nên phải cấp thêm.
--
-- Lưu ý: KHÔNG cấp quyền cho user này trên `mysql` (CSDL hệ thống) — không
-- cần thiết và làm tăng thiệt hại nếu lộ mật khẩu.
GRANT ALL PRIVILEGES ON `ntt_demo_llm`.* TO 'ntt'@'%';
GRANT ALL PRIVILEGES ON `moodle_local`.* TO 'ntt'@'%';

-- Nếu đổi MARIADB_USERNAME trong .env sang tên khác thì phải sửa 2 dòng trên
-- cho khớp. Cách gọn hơn là giữ nguyên user "ntt".

FLUSH PRIVILEGES;
