# Bảng màu Moodle NTTU

Nguồn: `project-design/UI_Accept_v2.html`. Chỉ lấy ngôn ngữ thị giác, không dùng lại HTML, JavaScript hay các selector toàn cục của mẫu.

| Vai trò | Giá trị trong mẫu |
| --- | --- |
| Thương hiệu / hover / nền nhạt | `#004b87` / `#003a69` / `#e8f1fa` |
| Chữ chính / phụ | `#1b2a41` / `#5b6b82` |
| Nền trang / bề mặt / hover | `#f8faff` / `#ffffff` / `#f1f5fa` |
| Viền nhẹ / mạnh | `#e3ebf5` / `#8596ad` |
| Thành công: chữ / nền | `#166534` / `#e8f6ee` |
| Cảnh báo: chữ / nền | `#9a5b00` / `#fff8e1` |
| Nguy hiểm: chữ / nền | `#a51818` / `#fdeeee` |

Mẫu dùng Be Vietnam Pro, chữ cơ bản 1rem, giãn dòng 1.55; khoảng cách theo nhịp 0.5rem; bo góc điều khiển 8px, thẻ 12px; bóng rất nhẹ. Đây là tham chiếu, không phải tất cả đều được áp dụng.

## Phạm vi áp dụng

Preset `default.scss` của Boost dùng xanh thương hiệu làm màu primary mặc định. Giữ cấu hình màu của quản trị viên nếu đã đặt. Trong giao diện sáng, nền xanh trắng, khung nội dung trắng bo góc và tiêu đề xanh mực phân biệt nội dung với vùng điều hướng. Không đổi font, spacing của form, màu thông báo hay kích thước drawer. Preset `plain.scss` không đổi.

Để kiểm tra trên site: chọn Boost với preset `default.scss`, xóa cache giao diện nếu còn CSS cũ. Mở khóa học ở desktop và mobile, đóng/mở drawer, dùng Tab tới các nút, rồi chuyển sang chế độ tối để kiểm tra tương phản.
