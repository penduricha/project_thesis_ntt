-- Chat giữa các admin (chỉ văn bản): ba bảng ntt_chat_conversations, ntt_chat_members, ntt_chat_messages.
--
-- CHẠY FILE NÀY TRƯỚC LẦN KHỞI ĐỘNG ĐẦU CÓ CHAT. Lý do:
--  1) spring.jpa.hibernate.ddl-auto = update sẽ tự tạo bảng nếu chưa có, nhưng lấy charset mặc định của CSDL
--     (có thể không phải utf8mb4 => hỏng tiếng Việt có dấu và emoji). DataInitializer chỉ đổi charset cho bảng Admin và Course.
--  2) Cột type dùng VARCHAR(10) có CHECK, không dùng ENUM (bẫy ENUM do Hibernate sinh, xem 2026-11-14-totp-disable-events.sql).
--  3) Có khóa ngoại tới ntt_admins để không có tin/thành viên mồ côi.
--
-- Tên chỉ mục/ràng buộc khớp với @Table trong entity, nên Hibernate (update) thấy đã có và không tạo thêm bản trùng.
-- Chạy lại nhiều lần an toàn (CREATE TABLE IF NOT EXISTS).

SET NAMES utf8mb4;

-- 1) Cuộc trò chuyện: DIRECT (1-1) hoặc GROUP (nhóm).
--    DIRECT: name và owner_id NULL, direct_key = "idNhỏ_idLớn" của hai admin (UNIQUE => chỉ một cuộc cho mỗi cặp).
--    GROUP : name và owner_id có giá trị, direct_key NULL (UNIQUE cho phép nhiều NULL).
CREATE TABLE IF NOT EXISTS ntt_chat_conversations (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    type        VARCHAR(10)  NOT NULL,
    name        VARCHAR(100) NULL,
    direct_key  VARCHAR(40)  NULL,
    owner_id    BIGINT       NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_conv_direct_key (direct_key),
    CONSTRAINT ck_chat_conv_type CHECK (type IN ('DIRECT', 'GROUP')),
    CONSTRAINT fk_chat_conv_owner FOREIGN KEY (owner_id) REFERENCES ntt_admins (admin_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 2) Thành viên. Không có cột vai trò: chủ nhóm suy ra từ ntt_chat_conversations.owner_id.
--    last_read_message_id: mốc đã đọc của riêng thành viên (chỉ tăng, không bao giờ giảm).
CREATE TABLE IF NOT EXISTS ntt_chat_members (
    conversation_id       BIGINT      NOT NULL,
    admin_id              BIGINT      NOT NULL,
    joined_at             DATETIME(6) NOT NULL,
    last_read_message_id  BIGINT      NULL,
    PRIMARY KEY (conversation_id, admin_id),
    KEY idx_chat_member_admin (admin_id),
    CONSTRAINT fk_chat_member_conv  FOREIGN KEY (conversation_id) REFERENCES ntt_chat_conversations (id),
    CONSTRAINT fk_chat_member_admin FOREIGN KEY (admin_id)        REFERENCES ntt_admins (admin_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 3) Tin nhắn văn bản. Không có cột sửa / đã xóa / updated_at: không sửa tin, xóa là xóa cứng.
--    Thứ tự tin = thứ tự id. UNIQUE (conversation_id, sender_id, client_msg_id) để gửi lại cùng clientMsgId không tạo tin trùng.
--    Cố ý KHÔNG đặt ON DELETE CASCADE: service xóa theo thứ tự rõ ràng (tin -> thành viên -> cuộc).
CREATE TABLE IF NOT EXISTS ntt_chat_messages (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    conversation_id  BIGINT        NOT NULL,
    sender_id        BIGINT        NOT NULL,
    client_msg_id    CHAR(36)      NOT NULL,
    content          VARCHAR(2000) NOT NULL,
    created_at       DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_chat_msg_conv_id (conversation_id, id),
    UNIQUE KEY uk_chat_msg_client (conversation_id, sender_id, client_msg_id),
    CONSTRAINT fk_chat_msg_conv   FOREIGN KEY (conversation_id) REFERENCES ntt_chat_conversations (id),
    CONSTRAINT fk_chat_msg_sender FOREIGN KEY (sender_id)       REFERENCES ntt_admins (admin_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Kiểm tra sau khi chạy:
-- SHOW CREATE TABLE ntt_chat_messages;   -- phải thấy CHARSET=utf8mb4
-- SELECT type, COUNT(*) FROM ntt_chat_conversations GROUP BY type;
--
-- Gỡ toàn bộ dữ liệu chat (cẩn thận, không khôi phục được):
-- DROP TABLE ntt_chat_messages; DROP TABLE ntt_chat_members; DROP TABLE ntt_chat_conversations;
