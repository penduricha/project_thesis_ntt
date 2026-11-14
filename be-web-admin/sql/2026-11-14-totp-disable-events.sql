-- Thêm 2 sự kiện audit mới: TOTP_DISABLED, TOTP_DISABLE_FAILED (tính năng tắt 2FA từ app mobile).
--
-- VÌ SAO CẦN CHẠY TAY: spring.jpa.hibernate.ddl-auto = update chỉ THÊM bảng/cột, không sửa kiểu cột đã có.
-- Với MariaDB, Hibernate tạo cột ntt_auth_audit_logs.event_type (@Enumerated(STRING)) dạng ENUM(...) chỉ gồm
-- các giá trị lúc tạo bảng. Chưa sửa thì INSERT giá trị mới bị từ chối; AuthAuditService chỉ ghi cảnh báo
-- "Audit log failed ..." nên API vẫn chạy nhưng KHÔNG có dòng audit nào cho việc tắt 2FA.
--
-- Cách 1 (khuyên dùng): đổi sang VARCHAR(30), giữ nguyên dữ liệu cũ, các sự kiện thêm sau này không cần ALTER nữa.
-- (Tên dài nhất hiện tại là TOTP_ENROLL_CONFIRM_FAILED = 26 ký tự, vừa với length = 30 của @Column.)
ALTER TABLE ntt_auth_audit_logs
    MODIFY COLUMN event_type VARCHAR(30) NULL;

-- Cách 2 (nếu muốn giữ ENUM): bỏ Cách 1 và chạy câu dưới (danh sách khớp enums/AuthEvent.java).
-- ALTER TABLE ntt_auth_audit_logs
--     MODIFY COLUMN event_type ENUM(
--         'REGISTER_OK','LOGIN_OK','LOGIN_FAIL_PASSWORD','LOGIN_FAIL_TOTP','LOGOUT','REFRESH_TOKEN',
--         'ACCOUNT_LOCKED','DEVICE_BOUND','DEVICE_REVOKED','PASSWORD_RESET','BACKUP_CODE_USED',
--         'TOTP_ENROLL_ACTIVATED','TOTP_ENROLL_CONFIRM_FAILED','TOTP_ENROLL_CONFIRMED','TOTP_ENROLL_CANCELLED',
--         'TOTP_LOGIN_OK','TOTP_LOGIN_FAILED','TOTP_DISABLED','TOTP_DISABLE_FAILED') NULL;

-- Kiểm tra sau khi chạy:
-- SHOW COLUMNS FROM ntt_auth_audit_logs LIKE 'event_type';
