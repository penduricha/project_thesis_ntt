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
Khi toàn bộ câu được máy chủ xác nhận đã lưu và danh sách được cập nhật, dialog tự đóng và hiển thị thông báo thành công.
Khi gặp lỗi, máy chủ dừng xử lý và hệ thống hiển thị số câu đã lưu; các câu sau chưa được xử lý.
Nếu mất kết nối trước khi nhận kết quả cả lô, một số hoặc tất cả câu có thể đã được lưu:
kiểm tra danh sách trên máy chủ trước khi tạo file mới để tránh nhập trùng.
Không tự động gửi lại các câu đã xử lý.

## File demo

Các file trong [`demo-import/`](demo-import/) chứa câu hỏi mẫu về Mạng máy tính,
2 câu mỗi file: `mang-may-tinh.csv`, `mang-may-tinh.xlsx`,
`mang-may-tinh.xml` (Moodle XML) và `mang-may-tinh.json`. Các file có nội dung
và tiêu đề khác nhau để có thể nhập lần lượt vào cùng môn. Trong dialog chọn
môn, chương và mức độ phù hợp, rồi chọn từng file, xem trước và nhấn **Nhập câu hỏi**.
Mỗi file được lưu vào môn/chương đang chọn, không phải thông tin nằm sẵn trong file.

Nhập lại cùng file trong cùng môn có thể bị từ chối vì trùng tiêu đề; hãy kiểm tra
hoặc xóa câu demo trước khi nhập lại. Đây là dữ liệu demo, được lưu thật khi import
thành công; nên dùng môn/CSDL thử nghiệm, không dùng dữ liệu production.
