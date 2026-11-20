# Server AI (demo): gọi LLM Qwen trên 5 tài khoản Colab

Đây là dự án Gradle đầy đủ (Spring Boot 3.3, Java 17+), có sẵn file main `BeServerAIApplication`.

## 1. Chuẩn bị
1. JDK 17 trở lên.
2. MariaDB đang chạy. Tạo database với bảng mã utf8mb4 (bắt buộc để lưu tiếng Việt; Hibernate tạo bảng theo bảng mã mặc định của database):
   ```sql
   CREATE DATABASE ntt_demo_llm CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
3. Redis đang chạy (cổng 6379, user `default`, mật khẩu như trong `.env`).
4. Chép `.env` thành `.env` (cùng thư mục với `build.gradle`) rồi sửa nếu cần. Chạy từ IntelliJ thì thư mục làm việc phải là thư mục dự án, mặc định đã đúng.

## 2. Mở trong IntelliJ
1. Giải nén vào một thư mục mới. Nếu dùng lại thư mục cũ thì xóa `.idea`, các file `*.iml` và thư mục `optional` trước.
2. File > Open, chọn thư mục chứa `build.gradle`, bấm Trust Project và chờ Gradle tải thư viện (cần Internet lần đầu).
3. Chạy lớp `BeServerAIApplication` (cổng 8082).
4. Lần chạy đầu Hibernate tự tạo 3 bảng `ntt_ai_tasks`, `ntt_ai_suggestions`, `ntt_issue_types` và nạp 4 loại lỗi mẫu vào `ntt_issue_types`.

## 3. Demo
1. Mở http://localhost:8082/api/node-colab.html
2. Chạy ô server trên mỗi notebook Colab, copy hai dòng `URL` và `TOKEN`, dán vào trang. Bấm Kiểm tra rồi Lưu.
3. Mục "Thử gọi AI": gửi 1 câu, hoặc 20 câu cùng lúc để xem việc được chia cho các node. Tắt một node hoặc dán sai token để xem việc chuyển sang node khác.

## Luồng xử lý
Trang HTML (đóng vai Server 1) → INSERT `ntt_ai_tasks` (pending) → commit → LPUSH `ai:queue` →
`AiDispatcher` BRPOP, chỉ lấy việc khi còn node rảnh → chọn node ít việc nhất → `OllamaClient` gọi `/api/chat` với Bearer token →
`AiResultParser` kiểm tra JSON → một transaction ngắn: UPDATE task (succeeded) + INSERT `ntt_ai_suggestions` → PUBLISH `ai:done`.
Lỗi token, lỗi 5xx, treo hoặc node tắt: task thử lại trên node khác (tối đa 5 lượt), node lỗi liên tiếp vào chế độ nghỉ.
Cả 5 node đều hỏng thì task thành `failed` với mã `MAX_ATTEMPTS`.

## Endpoint (tiền tố /api)
| Phương thức | Đường dẫn | Việc làm |
|---|---|---|
| GET | /web/ai/nodes | 5 node và trạng thái (không có token) |
| PUT | /web/ai/nodes/{colab-1..5} | đặt url, token, enabled, maxConcurrency |
| DELETE | /web/ai/nodes/{name} | xóa URL và token của node |
| POST | /web/ai/nodes/check | thử một cặp url + token |
| POST | /web/ai/tasks | tạo task `nl_search` hoặc `question_check` |
| POST | /web/ai/tasks/burst | tạo nhanh nhiều task nl_search |
| GET | /web/ai/tasks, /web/ai/tasks/{id} | xem task và kết quả |
| GET | /web/ai/overview | độ dài hàng đợi và trạng thái node |

```bash
curl -X PUT localhost:8082/api/web/ai/nodes/colab-1 -H "Content-Type: application/json" \
  -d '{"url":"https://xxx.trycloudflare.com","token":"<TOKEN>","enabled":true,"maxConcurrency":3}'
curl -X POST localhost:8082/api/web/ai/tasks -H "Content-Type: application/json" \
  -d '{"taskType":"nl_search","query":"Bộ đề kiểm tra TOEIC cấp độ khó ở dòng câu hỏi part 5."}'
```

## Lưu ý
- URL và token chỉ nằm trong bộ nhớ Server AI. Không ghi MariaDB hay Redis, không trả về trình duyệt. Khởi động lại Server AI thì nhập lại.
- Server AI chỉ nhận URL dạng `https://<tên>.trycloudflare.com` (chặn bị lợi dụng gọi địa chỉ nội bộ).
- Mọi endpoint đang mở, không cần đăng nhập (`security/SecurityConfig`): chỉ dùng trên máy bạn khi demo.
- Task `question_check` ở bản demo nhận `questionId` và `questionRevisionId` bất kỳ, vì bảng do Hibernate tạo chưa có khóa ngoại.
- Khi ghép với Server 1 (dùng CSDL chạy script ER v4): đổi `ddl-auto` thành `none`, đặt `ai.demo.seed-issue-types=false`, dùng cùng `MARIADB_DBS` và `REDIS_DBS` với Server 1 (hàng đợi `ai:queue` nằm trong Redis DB đó), và thay `SecurityConfig` bằng xác thực thật.
- Notebook Colab giữ nguyên, không sửa dòng nào.
