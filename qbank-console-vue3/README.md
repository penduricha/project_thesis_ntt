# QBank Console – bản chuyển Vue 3 (demo)

```bash
npm install
npm run dev      # http://localhost:5173
```

- `src/assets/main.css`: CSS trích nguyên văn từ `UI_Accept_v9_Thesis.html` (cùng class/id/cấu trúc DOM).
- `src/data/mock.js`: dữ liệu mẫu (môn học, câu hỏi, tài khoản).
- `src/repositories/questionRepository.js`: repository mock (async) – thay thân hàm bằng gọi Spring Boot.
- `src/store.js`: state + vòng đời API (`idle → loading → ready | error`), tác vụ AI mô phỏng, xóa có hoàn tác.
- Đã chuyển: khung (topbar, sidebar, menu tài khoản, thanh chọn, toast), Tổng quan, Tất cả câu hỏi, Môn học (thẻ + tìm không dấu), trang môn học `/#/course/0`, Xem xét đề xuất (so sánh từng từ, chấp nhận/từ chối, hoàn tác), Tác vụ AI (lọc, thử lại).
- Hộp thoại (`src/components/`): `BaseModal` (không cần Bootstrap JS), thêm/sửa câu hỏi (validation: câu dẫn/4 phương án/môn/chương không rỗng, cảnh báo trùng phương án và trùng câu dẫn, đếm ký tự, xem DTO), thêm/sửa môn học (mã/tên rỗng + trùng), quản lý chương (rỗng + trùng), xác nhận xóa.
- Chưa chuyển: Tìm kiếm thông minh, Moodle XML, Quản trị, Cài đặt LLM, Tài khoản, Cài đặt (đang là trang tạm), bảng phím tắt; app vẫn mở ở Tổng quan.
