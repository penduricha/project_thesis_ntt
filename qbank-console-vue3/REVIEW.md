# Rà soát & ghép `qbank-console-vue3` + `fe_web_admin` + `be-web-admin`

> Phạm vi: chỉ 3 nhánh Vue3 / Spring Boot (bỏ qua `php_moodle_503/`). CSDL chốt theo
> `CSDL_Class_Diagram_v4.png` / `CSDL_SQL_Diagram_v4.png` (Redis chỉ là cache, không tính là bảng).
> Người rà: góc nhìn fullstack Vue3 + Java.

## 1. Kết luận ngắn

Trước khi ghép, `qbank-console-vue3` là **prototype UI thuần mock**: `store.js` giữ toàn bộ dữ liệu
trong RAM, `questionRepository` là mảng in-memory, "kiểm định AI" chạy `setTimeout` + `llmSimulator`,
Moodle XML sinh ở client, không có token/không guard route. Trong khi đó `fe_web_admin` đã có **login +
TOTP + auto-login + silent refresh + SSE 2FA** rất hoàn chỉnh, và `be-web-admin` đã có **đủ API** cho cả
auth lẫn qbank (`/questions`, `/catalog`, `/ai/jobs`, `/dashboard`, `/2fa`).

Việc ghép đã làm: port nguyên tầng auth của `fe_web_admin` sang, **giữ đúng hợp đồng API của Spring Boot**,
và thay dần lớp mock bằng API thật, có **chế độ mẫu (mock) làm lưới an toàn** để UI vẫn xem được khi
backend chưa chạy.

## 2. Lỗi nghiêm trọng đã tìm & sửa

| # | Vấn đề | Hậu quả nếu không sửa | Đã xử lý |
|---|--------|------------------------|----------|
| 1 | `qbankApi` gọi `/questions`, `/catalog/...` trong khi `API_BASE = .../api/web` | **404 toàn bộ** (đúng phải là `/api/web/admin/questions`) — đây là lỗi chết người khi ghép | Thêm hằng `ADMIN = '/admin'` + helper `url()` cho mọi endpoint |
| 2 | `package.json` không có `axios`, lại ghim `vite ^8` với `@vitejs/plugin-vue ^5` | `npm install` **ERESOLVE** — không cài được, không build được | Thêm `axios ^1.7.9`, nâng `@vitejs/plugin-vue ^6.0.11` (peer của vite 8) |
| 3 | `main.js` mount ngay, không auto-login | Guard chạy khi chưa biết phiên → đá về `/login` dù còn refresh token | `bootstrapAuth()` trước khi `createApp().mount()` |
| 4 | Không có guard; menu admin/llm dựa `S.role` mock | Vào thẳng `/bank` khi chưa đăng nhập; phân quyền ảo | `beforeEach` + redirect `/login`; `auth.role` watch vào `S.role` |
| 5 | Đăng xuất chỉ là toast demo | Token còn nguyên trong localStorage | `logout()` thật (POST /auth/logout + BroadcastChannel + `clearLocalSession`) |
| 6 | Toàn bộ CRUD câu hỏi gọi repo mock | Sửa trên UI nhưng DB không đổi | `saveQuestion`/`removeQuestions`/`decide`/`runAI` gọi API thật, có xử lý 409 |
| 7 | `store.js` reset dữ liệu mỗi lần mount App | Mất cảnh báo/không tải đúng sau login | Chỉ tải sau khi `auth.isAuthenticated` chuyển `true`, một lần/phiên |

## 3. Ánh xạ nghiệp vụ UI ↔ CSDL v4 ↔ API

| UI (mock) | Cột/bảng CSDL v4 | API Spring Boot |
|---|---|---|
| `q.text`, `q.o[4]`, `q.a` | `ntt_questions.stem/option_a..d/correct_label` | `GET/POST/PUT/DELETE /questions` |
| `q.st` (draft/ok/flag/stale) | `validation_status` ENUM `not_checked/clean/has_issues/stale/failed` | `QuestionListItem.validationStatus` |
| `q.l` 3 mức (Dễ/TB/Khó) | `difficulty` 1..5, NULL = chưa đánh giá | `difficultyToUi/difficultyToDb` |
| `q.ver` | `version` (`@Version` lạc quan) | `UpdateQuestionRequest.expectedVersion` → 409 |
| `COURSES[i]` + `topics` | `ntt_subjects`, `ntt_topics` (cây `parent_id`) | `GET /catalog/subjects`, `.../topics` |
| `S.sug` (1 mục/câu) | `ntt_ai_suggestions` (**1 dòng/đề xuất**) | `GET /questions/{id}/ai-suggestions`, `POST /ai/suggestions/{id}/decision` |
| `S.tasks` | `ntt_ai_tasks` | `POST /questions/{id}/ai-check` (202) + poll `GET /ai/jobs/{taskId}` |
| `S.tfa` (NOT_ENABLED/PENDING/ENABLED) | `ntt_totp_credentials` (`status`, `totp_enabled`) | `/2fa/enrollment`, `/activate`, `/status`, `/pending`, SSE `/2fa/events` |
| `ME` | `ntt_admins` + `ntt_courses` | `GET /information/basic` + `/information/detail` |

Ba điểm **lệch mô hình** cần nhớ khi đọc code:

1. **Difficulty 1..5 vs 3 mức**: DB cho phép 1..5 và NULL; UI chỉ vẽ 3 mức. Mapper gom `>=3 -> Khó`,
   `null -> Trung bình`; khi ghi chỉ ghi 1/2/3 nên *không mất dữ liệu* nhưng *không tận dụng được 4–5*.
   Muốn đúng đề cương thì đổi `LV` thành 5 mức — sửa ở `mappers.js`, `mock.js`, `QuestionDialog.vue`.
2. **`xác nhận 2FA` không nằm ở web**: mobile (Flutter) mới là nơi xác nhận; web chỉ tạo khóa → `PENDING`
   → SSE báo `ENABLED`. Vì vậy nút "Mô phỏng ứng dụng đã xác nhận" bị **giấu ở chế độ thật**.
3. **AI không được đổi cấu trúc/đáp án**: `target_field = 'question'` bị backend chặn
   (`TARGET_NOT_APPLICABLE`). `decide()` ở chế độ thật **tách** đề xuất áp được (stem/option_*) và đề xuất
   phải sửa tay, thay vì áp cả cụm như bản mock.

## 4. Những gì còn mock (và vì sao)

| Màn | Trạng thái | Lý do |
|---|---|---|
| `CoursesView` (thêm/sửa/xóa môn + chương) | **mock**, chỉ *đọc* danh mục thật | Backend **chưa có** `POST/PUT/DELETE /catalog/subjects|topics`. Cần bổ sung controller trước |
| `AdminView` (tạo admin, cấp lại mật khẩu, đổi role, phạm vi môn) | **mock** | Chỉ có `AdminControllerTest` (`@Profile(dev/local/test)`); không có API quản trị tài khoản cho production |
| `MoodleView` (export/import XML) | **mock ở client** | Có bảng `ntt_moodle_*` + entity/repository nhưng **không có controller** |
| `SearchView` (NL → JSON/SQL) | **mock** | Backend có `TaskTypeD.NL_SEARCH` nhưng chưa có endpoint search ngôn ngữ tự nhiên |
| `LlmView` (cấu hình Server 2) | **mock** | Cấu hình worker nằm ở `server-ai-worker`, không thuộc DB nghiệp vụ |

Điểm cộng: mỗi màn mock đều **không làm sai dữ liệu thật** — chúng chỉ giữ trong RAM trình duyệt, và khi
`S.dataMode === 'real'` thì đường ghi thật không đi qua chúng.

## 5. Rủi ro kỹ thuật còn lại (nên xử lý tiếp)

1. **N+1 khi tải ngân hàng câu hỏi**: `loadQuestions()` gọi `GET /questions?size=100` rồi `GET /questions/{id}`
   cho **từng** câu để lấy đủ 4 phương án (vì `QuestionListItem` chỉ có `stemPreview`). Với 1.000 câu là 1.000
   request. → Nên thêm `include=detail` hoặc endpoint `GET /questions/details?...`, hoặc chấp nhận phân trang
   thật (mỗi trang 20 câu) như thiết kế ban đầu của backend.
2. **Không có endpoint "liệt kê đề xuất chờ duyệt"**: `loadSuggestions()` phải lọc `has_issues` rồi gọi
   `/questions/{id}/ai-suggestions`. → Nên thêm `GET /ai/suggestions?decision=pending&page=`.
3. **`runAI` poll bằng `while` + `sleep` trong store**: chạy được nhưng khi đóng tab giữa chừng thì mất theo dõi.
   Backend đã có Redis + task; nên chuyển sang `GET /ai/jobs` theo chu kỳ hoặc SSE.
4. **`S.dataMode` rớt về mock khi lỗi mạng**: đúng cho demo, **sai cho production** — người dùng có thể tưởng
   đang sửa dữ liệu thật. Đã có toast + log cảnh báo, nhưng nên thêm banner đỏ cố định trên header.
5. **`AdminStorage` lưu `adminId` vào localStorage** (remember-me của fe_web_admin): chấp nhận được (không
   lưu mật khẩu), nhưng nên ghi rõ trong chính sách bảo mật của đề án.
6. **`store.js` đang là "God object" ~600 dòng**: đủ dùng cho đồ án, nhưng nếu mở rộng nên tách
   `stores/auth`, `stores/catalog`, `stores/question`, `stores/ai` (Pinia hoặc composable).
7. **Chưa có kiểm thử frontend**: nên thêm Vitest cho `mappers.js` (difficulty, label, validation) và
   `LoginView` (các nhánh TOTP_REQUIRED / LOCKED).

## 6. Việc backend nên bổ sung để web "hoàn chỉnh"

```java
// 1) Danh mục ghi được (CoursesView)
POST   /web/admin/catalog/subjects          CreateSubjectRequest
PUT    /web/admin/catalog/subjects/{id}
DELETE /web/admin/catalog/subjects/{id}      // chặn khi còn câu hỏi
POST   /web/admin/catalog/subjects/{id}/topics
PATCH  /web/admin/catalog/topics/{id}        // đổi tên / đổi parent (kiểm chu trình bằng CTE)

// 2) Duyệt đề xuất theo lô (ReviewView)
GET    /web/admin/ai/suggestions?decision=pending&page=&size=
POST   /web/admin/ai/suggestions/bulk-decision   // { ids: [], decision, rejectReason }

// 3) Chi tiết câu hỏi kèm đủ phương án trong 1 request (bỏ N+1)
GET    /web/admin/questions?fields=detail

// 4) Quản trị tài khoản (AdminView) — tách khỏi AdminControllerTest
POST   /web/admin/accounts            // tạo + trả mật khẩu tạm 1 lần
PATCH  /web/admin/accounts/{id}/role
POST   /web/admin/accounts/{id}/reset-password
DELETE /web/admin/accounts/{id}        // chỉ disable, không xóa vì FK audit

// 5) Moodle (MoodleView có bảng nhưng chưa có API)
POST   /web/admin/moodle/export
POST   /web/admin/moodle/import
```

Ghi chú thiết kế cho từng cái: `subject_code` nên unique (đã có), xóa mềm `is_active`; tạo account phải
`BCrypt cost 12` như `AdminControllerTest`; bulk-decision phải chạy **tuần tự trong 1 transaction/câu** để
mỗi câu sinh đúng 1 revision (ràng buộc D8).

## 7. Cách chạy

```bash
# Backend (cần MariaDB + Redis theo compose.yaml)
cd be-web-admin && cp .env.example .env && ./gradlew bootRun

# Frontend
cd qbank-console-vue3 && cp .env.example .env && npm install && npm run dev
# http://localhost:5173  (#/login -> đăng nhập admin thật)
# Muốn xem UI khi chưa có backend: VITE_DEMO_MODE=true
```

Kiểm tra nhanh sau khi ghép: `npm run build` (đã pass — 137 module), đăng nhập sai 5 lần để thấy khóa TOTP,
bật 2FA để thấy luồng `NOT_ENABLED → PENDING → (mobile xác nhận) → ENABLED`.
