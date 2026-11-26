# Nhập câu hỏi theo môn

Trong **Môn học → chọn môn → Thêm câu hỏi → Nhập từ file**, chọn chương và
mức độ áp dụng cho tất cả câu trong file. Chỉ thực hiện khi đã đăng nhập và
kết nối dữ liệu Spring Boot thật; không lưu thay thế vào dữ liệu mẫu. Mỗi file
tối đa 2 MB, 200 câu; toàn bộ file phải hợp lệ trước khi bắt đầu lưu.

## CSV và XLSX

CSV UTF-8 dùng dấu phẩy và quy tắc dấu ngoặc kép thông thường; XLSX gồm đúng
một sheet, các ô là văn bản, không có công thức. Dòng đầu bắt buộc có đúng tên
cột (phân biệt hoa thường) `stem,optionA,optionB,optionC,optionD,correctLabel`;
có thể thêm `title,generalFeedback`, không có cột khác. `correctLabel` là A,
B, C hoặc D. Ví dụ CSV:

```csv
stem,optionA,optionB,optionC,optionD,correctLabel,title,generalFeedback
"Khóa chính có đặc điểm nào?",Duy nhất,Có thể trùng,Có thể NULL,Không có giá trị,A,Khóa chính,Khóa chính phải duy nhất.
```

## JSON

Một mảng các đối tượng với cùng tên trường như trên; tất cả giá trị là chuỗi.
`title` và `generalFeedback` có thể bỏ qua hoặc để trống.

```json
[
  {
    "stem": "Khóa chính có đặc điểm nào?",
    "optionA": "Duy nhất",
    "optionB": "Có thể trùng",
    "optionC": "Có thể NULL",
    "optionD": "Không có giá trị",
    "correctLabel": "A",
    "title": "Khóa chính",
    "generalFeedback": "Khóa chính phải duy nhất."
  }
]
```

## Moodle XML

Chỉ chấp nhận `multichoice` chọn một đáp án, đúng 4 lựa chọn, một đáp án
`fraction="100"` và ba đáp án `fraction="0"`; các mục danh mục được bỏ qua.
HTML cơ bản được chuyển thành văn bản. Không hỗ trợ ảnh, tệp, nội dung nhúng,
các định dạng văn bản khác, hoặc phản hồi riêng cho từng đáp án. Phản hồi chung
được giữ lại. Không dùng DOCTYPE/ENTITY.

Tiêu đề tối đa 255 ký tự; câu dẫn và mỗi phương án tối đa 20.000 ký tự;
phản hồi chung tối đa 4.000 ký tự. Tiêu đề không rỗng phải duy nhất trong môn.
Máy chủ vẫn kiểm tra quyền và dữ liệu khi tạo từng câu.

## Xử lý lỗi

Vue đọc file và gửi dữ liệu đã chuẩn hóa đến `POST /api/web/admin/questions/import`; Spring xác thực lại dữ liệu, sau đó dùng dịch vụ tạo câu hiện có để ghi từng câu vào CSDL. File gốc không được tải lên máy chủ. Mỗi câu có transaction riêng, không có giao dịch chung cho cả file.
Khi gặp lỗi, hệ thống dừng và hiển thị số câu đã lưu; các câu sau chưa được gửi.
Nếu mất kết nối trước khi nhận kết quả cả lô, một số hoặc tất cả câu có thể đã được lưu:
kiểm tra danh sách trên máy chủ trước khi tạo file mới để tránh nhập trùng.
Không tự động gửi lại các câu đã xử lý.
