# QBank Console – bản chuyển Vue 3 (demo)

```bash
npm install
npm run dev      # http://localhost:5173
```

- `src/assets/main.css`: CSS trích nguyên văn từ `UI_Accept_v9_Thesis.html` (cùng class/id/cấu trúc DOM).
- `src/data/mock.js`: dữ liệu mẫu (môn học, câu hỏi, tài khoản).
- `src/repositories/questionRepository.js`: repository mock (async) – thay thân hàm bằng gọi Spring Boot.
- `src/store.js`: state + vòng đời API (`idle → loading → ready | error`), tác vụ AI mô phỏng, xóa có hoàn tác.
- Đã chuyển: khung (topbar, sidebar, menu tài khoản, thanh chọn, toast), Tổng quan, Tất cả câu hỏi (lọc, tìm, sắp xếp, chọn, phân trang), trang môn học `/#/course/0`.
- Chưa chuyển (hiện toast/placeholder): các hộp thoại, Môn học, Tìm kiếm thông minh, Xem xét đề xuất, Tác vụ AI, Moodle XML, Quản trị, Cài đặt LLM, Tài khoản, Cài đặt.
