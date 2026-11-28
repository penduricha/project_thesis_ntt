# server-ai-worker (Server 2 – Java Spring Boot AI Worker)

Module worker AI độc lập được xây dựng sạch sẽ từ `server-ai-demo`, giữ tương thích test trực tiếp trên MariaDB `ntt_demo_llm` (`ddl-auto: update`), đồng thời nâng cấp toàn diện pipeline AI, độ tin cậy, kiểm soát ảo giác và chuẩn hóa dữ liệu theo ER v4.

## 1. Những nâng cấp chính so với `server-ai-demo`

1. **Chuẩn hóa hệ 5 nhãn lỗi (`ntt_issue_types`)**
   - `spelling` (is_evaluated=true), `grammar` (is_evaluated=true), `logic` (is_evaluated=true), `distractor` (is_evaluated=true), `key_doubt` (is_evaluated=false) và `none` (is_evaluated=false).
2. **Nâng cấp `question_check` (`analysis + suggestions[]`)**
   - Hỗ trợ **nhiều gợi ý trên một câu hỏi**, trải trên `question | stem | option_a | option_b | option_c | option_d` (không bao giờ cho phép AI tự sửa `correct_label`).
   - Hỗ trợ quy trình tự giải độc lập **Blind-Solve CoT** (`few_cot` mặc định; có thể đổi `zero` / `few` / `few_cot` qua cấu hình `ai.llm.question-check-mode`) và tiếp nhận ngữ cảnh môn học/chủ đề/độ khó.
   - **Tiền kiểm tất định (`RulePreflightChecker`)**: bắt ngay lỗi trống trường, trùng phương án, lộ đáp án trong câu dẫn trước/gộp với LLM.
   - **Định vị văn bản 4 tầng (`TextLocator`)**: khớp chính xác → NFC/khoảng trắng → bỏ dấu tiếng Việt giữ ánh xạ ngược về chỉ số UTF-16 gốc → Levenshtein ≤ 1; đảm bảo thỏa ràng buộc DB `chk_sugg_span` (`(span_start >= 0 AND span_end > span_start)` hoặc đều `NULL`).
   - **Hậu kiểm chống ảo giác (`AiResultParser`)**: tự sửa `target_field` nếu trích dẫn nằm ở trường khác, loại đề xuất vô nghĩa (`original == proposed`), loại trích dẫn ảo giác ở lỗi `spelling`/`grammar`.
3. **Nâng cấp `nl_search` với danh mục động**
   - `SubjectCatalogResolver` đọc `subjects[]` từ `input_json` (khi tích hợp Server 1) hoặc `ai.nl-search.default-subjects` (khi chạy demo).
   - Sinh JSON Schema động giới hạn `subject_code` và `topic_name` theo đúng danh mục, kiểm tra/hoán đổi ngày `created_from` / `created_to`, và đưa `catalogHash` vào khóa cache Redis để không bị trả bộ lọc cũ khi danh mục thay đổi.
4. **Sửa các lỗi kỹ thuật của worker**
   - Truyền `seed` vào Ollama `options` và đồng bộ `num_ctx: 4096` giữa hai loại task để tránh nạp lại model.
   - Phân biệt `LLM_TRUNCATED` (`done_reason = "length"`) với `LLM_BAD_JSON`.
   - Thu thập cơ hội `model_digest` (64 hex) & `runtime_version` (tối đa 30 ký tự) và ghi xuống `ntt_ai_tasks`.
   - `requeueOnShutdown` khi worker tắt êm (`SmartLifecycle` / `InterruptedException`) để không đốt nhầm `attempt_count`.
   - Thay toàn bộ `@RedisHash` repository bằng `StringRedisTemplate` với JSON + TTL (`ai:task:*`, `ai:cache:nl:*`, `ai:node:*`), loại bỏ hoàn toàn rác keyspace `Set` trong Redis.
   - Có sẵn bộ JUnit 5 test cho `TextLocator`, `RulePreflightChecker`, `AiResultParser`, `SubjectCatalogResolver` và `NodePool`.

## 2. Chạy thử độc lập với `ntt_demo_llm`

1. Sao chép `.env.example` thành `.env` ngay trong `server-ai-worker/` và điền thông số MariaDB + Redis + `AI_INTERNAL_TOKEN`:
   ```bash
   cp .env.example .env
   ```
   (Mặc định `JPA_DDL_AUTO=update` sẽ tự tạo bảng trên `ntt_demo_llm` khi chạy độc lập.)
2. Đảm bảo MariaDB đang chạy (database `ntt_demo_llm` đã được tạo: `CREATE DATABASE IF NOT EXISTS ntt_demo_llm CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`) và Redis đang chạy.
3. Chạy project bằng JDK 17+:
   ```bash
   ./gradlew bootRun
   ```
   Và chạy unit test:
   ```bash
   ./gradlew test
   ```
4. Mở trang test giao diện:
   - `http://localhost:8082/api/node-colab.html` hoặc `http://localhost:8082/api/demo-eval.html`
   - Hai trang này chỉ dành cho môi trường thử nghiệm cô lập với token thử nghiệm riêng. Không nhập token nội bộ production vào trình duyệt và không công khai cổng worker. Worker không chấp nhận JWT người dùng.

## 3. Chuyển sang DB chính ER v4 sau này

Khi ghép với `be-web-admin` (Server 1):
1. Đặt `JPA_DDL_AUTO=validate` và `AI_DEMO_SEED_ISSUE_TYPES=false` trong `.env` (vì SQL migration đã tạo bảng và seed sẵn `ntt_issue_types`).
2. Đặt `AI_DEMO_CREATED_BY` bằng một `admin_id` thật trong `ntt_admins` (nếu còn dùng endpoint test).
3. Cấu hình `AI_INTERNAL_TOKEN` ngẫu nhiên, riêng và giống nhau cho backend và worker qua biến môi trường an toàn. Không đưa token vào client. API worker chỉ dùng nội bộ; backend phải xác thực, phân quyền và kiểm tra quyền sở hữu trước khi gọi. Các trang demo không thay thế một giao diện quản trị có phân quyền.
