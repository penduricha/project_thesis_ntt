# QBank Console – bản chuyển Vue 3 (demo)

```bash
npm install
npm run dev      # http://localhost:5173
```

- `src/assets/main.css`: CSS trích nguyên văn từ `UI_Accept_v9_Thesis.html` (cùng class/id/cấu trúc DOM).
- `src/data/mock.js`: dữ liệu mẫu (môn học, câu hỏi, tài khoản).
- `src/repositories/questionRepository.js`: repository mock (async) – thay thân hàm bằng gọi Spring Boot.
- `src/store.js`: state + vòng đời API (`idle → loading → ready | error`), tác vụ AI mô phỏng, xóa có hoàn tác.
- Đã chuyển: khung (topbar, sidebar, menu tài khoản, thanh chọn, toast), Tổng quan, Tất cả câu hỏi, Môn học (thẻ + tìm không dấu), trang môn học `/#/course/0`, Xem xét đề xuất (so sánh từng từ, chấp nhận/từ chối, hoàn tác), Tác vụ AI (lọc, thử lại, dạng thẻ trên điện thoại), Tìm kiếm thông minh (JSON → SQL tham số hóa → kết quả), Moodle XML (xuất, tải, sao chép, nhập có kiểm tra), bảng phím tắt.
- Hộp thoại (`src/components/`): `BaseModal` (không cần Bootstrap JS), thêm/sửa câu hỏi (validation: câu dẫn/4 phương án/môn/chương không rỗng, cảnh báo trùng phương án và trùng câu dẫn, đếm ký tự, xem DTO), thêm/sửa môn học (mã/tên rỗng + trùng), quản lý chương (rỗng + trùng), xác nhận xóa.
- Tài khoản và quản trị: Tài khoản (thẻ hồ sơ, skeleton 600 ms, ẩn/hiện CCCD tự ẩn sau 30 s), Cài đặt (xác thực hai yếu tố NOT_ENABLED → PENDING 5 phút đếm ngược → ENABLED, vai trò xem thử Giảng viên có lọc theo phạm vi môn, tùy chọn thông báo và xác nhận xóa), Quản trị (lọc, chi tiết, đổi vai trò kèm phạm vi môn, thêm tài khoản có validation, cấp lại mật khẩu, xóa hoặc vô hiệu hóa), Cài đặt LLM (Server 2, dán kết quả Colab, 5 node, kiểm tra, lưu, xóa có hoàn tác, sao chép .env). Hộp thoại khóa (khóa 2FA, mật khẩu tạm) không đóng bằng Esc, nút X hay bấm nền.
- Mock API: `src/repositories/questionRepository.js` (async) và `setTimeout` trong store; thay bằng gọi Spring Boot khi có backend.
