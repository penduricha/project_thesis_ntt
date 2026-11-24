-- =====================================================================================
--  Vá enum answer_numbering của ntt_moodle_profiles
--  Ngày:    2026-11-23
--  Lý do:   Moodle hỗ trợ 6 kiểu đánh số đáp án, không phải 4.
--           question/type/multichoice/questiontype.php:324
--               array('abc', 'ABCD', '123', 'iii', 'IIII', 'none')
--           CSDL v4 chỉ khai báo ENUM('none','abc','ABCD','123') nên profile đọc từ
--           Moodle có answer_numbering = 'iii' hoặc 'IIII' sẽ:
--             · MySQL từ chối ghi giá trị (strict mode)  -> lỗi khi import
--             · hoặc lưu thành '' (non-strict)           -> mất dữ liệu âm thầm
--           Phía Java còn ném IllegalArgumentException trong AnswerNumberingD.fromDbValue().
--
--  Áp dụng: chạy MỘT LẦN trên CSDL đã tạo bằng 2026-11-19-02-business-tables.sql.
--           Với CSDL mới tinh thì KHÔNG cần chạy: file 2026-11-19-02 đã được sửa sẵn.
--
--  An toàn: chỉ MỞ RỘNG tập giá trị hợp lệ. Dữ liệu cũ ('none','abc','ABCD','123')
--           vẫn hợp lệ, không dòng nào bị đổi nghĩa. Không cần sao lưu bắt buộc,
--           nhưng nên chạy trên bản dump thử trước nếu CSDL đang có dữ liệu thật.
-- =====================================================================================

ALTER TABLE ntt_moodle_profiles
    MODIFY COLUMN answer_numbering
        ENUM ('none', 'abc', 'ABCD', '123', 'iii', 'IIII')
        NOT NULL DEFAULT 'none'
        COMMENT 'Kiểu đánh số đáp án khi đẩy sang Moodle; 6 giá trị khớp qtype_multichoice';

-- -------------------------------------------------------------------------------------
--  Kiểm chứng sau khi chạy (kết quả mong đợi: 6 dòng)
-- -------------------------------------------------------------------------------------
-- SELECT COLUMN_TYPE
--   FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND TABLE_NAME   = 'ntt_moodle_profiles'
--    AND COLUMN_NAME  = 'answer_numbering';
--  -> enum('none','abc','ABCD','123','iii','IIII')

-- -------------------------------------------------------------------------------------
--  Hoàn tác (chỉ chạy được khi KHÔNG còn dòng nào dùng 'iii' / 'IIII')
-- -------------------------------------------------------------------------------------
-- ALTER TABLE ntt_moodle_profiles
--     MODIFY COLUMN answer_numbering ENUM ('none','abc','ABCD','123') NOT NULL DEFAULT 'none';
