-- ============================================================================================
-- 01 - ĐỒNG BỘ 7 BẢNG XÁC THỰC HIỆN CÓ TRƯỚC KHI THÊM 14 BẢNG NGHIỆP VỤ
--
-- Mục đích: đưa schema thực tế (do spring.jpa.hibernate.ddl-auto = update sinh ra, không
-- kiểm soát được) về đúng trạng thái mô tả trong ER v4, đồng thời vá vài điểm lệch đã phát
-- hiện khi đối chiếu code Java với sơ đồ.
--
-- Chạy 1 lần, theo thứ tự 01 -> 02 -> 03. Sau khi chạy xong cả 3 file:
--   đổi spring.jpa.hibernate.ddl-auto: update  ->  validate
-- (xem docs/nghiep-vu/02-ke-hoach-mo-rong-csdl.md, mục 6)
--
-- Toàn bộ dùng utf8mb4 + InnoDB, thời gian lưu UTC (datetime(6)).
-- ============================================================================================

SET NAMES utf8mb4;

-- --------------------------------------------------------------------------------------------
-- 1.1 ntt_admins: thêm cột role (có trong ER v4 và trong Admin.java, nhưng chưa có migration)
--
-- LƯU Ý KIỂU DỮ LIỆU:
--   ER v4 ghi  role enum('admin','lecturer')   (chữ thường)
--   Admin.java dùng @Enumerated(EnumType.STRING) + enum Role { ADMIN, LECTURER }
--   => Hibernate ghi chuỗi 'ADMIN' / 'LECTURER' (CHỮ HOA). Nếu cột là enum chữ thường thì
--      mọi INSERT/UPDATE admin sẽ lỗi "Data truncated for column 'role'".
--   Quyết định ở đây: GIỮ CHỮ HOA (theo code, không phải migrate dữ liệu đang chạy).
--   => Phải sửa lại ER v4 cho khớp, hoặc thêm AttributeConverter nếu muốn giữ chữ thường.
-- --------------------------------------------------------------------------------------------
ALTER TABLE ntt_admins
    ADD COLUMN IF NOT EXISTS role ENUM ('ADMIN', 'LECTURER') NOT NULL DEFAULT 'ADMIN';

-- Cùng vấn đề với account_status: ER ghi enum('active','disabled','locked','pending'),
-- code ghi AccountStatus.ACTIVE/LOCKED/DISABLED/PENDING (chữ hoa). Giữ chữ hoa.
ALTER TABLE ntt_admins
    MODIFY COLUMN account_status ENUM ('PENDING', 'ACTIVE', 'LOCKED', 'DISABLED') NOT NULL DEFAULT 'PENDING';

-- Đăng nhập / phân quyền tra cứu theo role + trạng thái
ALTER TABLE ntt_admins
    ADD INDEX IF NOT EXISTS ix_admin_role_status (role, account_status);

-- --------------------------------------------------------------------------------------------
-- 1.2 ntt_auth_audit_logs: nới event_type lên VARCHAR(40)
--
-- File 2026-11-14-totp-disable-events.sql đã đổi ENUM -> VARCHAR(30). Nếu CHƯA chạy file đó
-- thì câu dưới đây làm luôn. Để 40 ký tự vì sắp tới còn thêm sự kiện nghiệp vụ câu hỏi/AI.
-- --------------------------------------------------------------------------------------------
ALTER TABLE ntt_auth_audit_logs
    MODIFY COLUMN event_type VARCHAR(40) NULL;

-- Truy vấn "lịch sử đăng nhập của 1 admin" và "thống kê theo loại sự kiện"
ALTER TABLE ntt_auth_audit_logs
    ADD INDEX IF NOT EXISTS ix_audit_admin_time (admin_id, created_at);
ALTER TABLE ntt_auth_audit_logs
    ADD INDEX IF NOT EXISTS ix_audit_event_time (event_type, created_at);

-- --------------------------------------------------------------------------------------------
-- 1.3 ntt_totp_credentials: bỏ UNIQUE không cần thiết trên iv, thêm index phục vụ đăng nhập
--
-- iv là 12 byte ngẫu nhiên: trùng IV gần như không xảy ra, nhưng UNIQUE biến một va chạm
-- cực hiếm thành lỗi INSERT (500) thay vì chỉ là chuyện mật mã học. AES-GCM an toàn khi
-- IV trùng chỉ trong CÙNG một khóa; ở đây mỗi bản ghi có salt riêng nên khóa khác nhau.
-- (Nếu index không tồn tại, câu lệnh báo lỗi - khi đó bỏ qua, không ảnh hưởng gì.)
-- --------------------------------------------------------------------------------------------
-- ALTER TABLE ntt_totp_credentials DROP INDEX iv;

ALTER TABLE ntt_totp_credentials
    ADD INDEX IF NOT EXISTS ix_totp_admin_confirmed (admin_id, confirmed, confirmed_at);

-- --------------------------------------------------------------------------------------------
-- 1.4 ntt_backup_codes: mã dự phòng (bảng đã có từ trước nhưng CHƯA có luồng nghiệp vụ nào dùng)
--     Index để tra "mã chưa dùng của admin này".
-- --------------------------------------------------------------------------------------------
ALTER TABLE ntt_backup_codes
    ADD INDEX IF NOT EXISTS ix_backup_admin_used (admin_id, used);

-- --------------------------------------------------------------------------------------------
-- 1.5 ntt_mobile_devices: tra theo admin + trạng thái
-- --------------------------------------------------------------------------------------------
ALTER TABLE ntt_mobile_devices
    ADD INDEX IF NOT EXISTS ix_device_admin_status (admin_id, status);

-- --------------------------------------------------------------------------------------------
-- 1.6 ntt_web_sessions: tra phiên còn sống theo admin
-- --------------------------------------------------------------------------------------------
ALTER TABLE ntt_web_sessions
    ADD INDEX IF NOT EXISTS ix_session_admin_status (admin_id, status, expires_at);

-- --------------------------------------------------------------------------------------------
-- 1.7 ntt_courses: cột course_id hiện KHÔNG có AUTO_INCREMENT (do ứng dụng cấp mã) - giữ nguyên.
--     Chỉ thêm index tra cứu theo khoa/ngành phục vụ màn hình quản trị sau này.
-- --------------------------------------------------------------------------------------------
ALTER TABLE ntt_courses
    ADD INDEX IF NOT EXISTS ix_course_faculty_major (faculty, major);

-- --------------------------------------------------------------------------------------------
-- KIỂM TRA SAU KHI CHẠY
-- --------------------------------------------------------------------------------------------
-- SHOW COLUMNS FROM ntt_admins LIKE 'role';
-- SHOW COLUMNS FROM ntt_admins LIKE 'account_status';
-- SHOW COLUMNS FROM ntt_auth_audit_logs LIKE 'event_type';
-- SHOW INDEX FROM ntt_totp_credentials;
