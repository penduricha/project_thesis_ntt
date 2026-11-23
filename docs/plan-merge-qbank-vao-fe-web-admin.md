# Kế hoạch gộp web demo (Vue 3) vào `fe_web_admin`

- **Ngày**: 2026-11-22 · **Nền**: `feature/thesis-qbank-v9` (`88e40f4`) + patch ORM/Redis (`project-thesis-ntt-qbank-orm-redis-v2-2026-11-22.patch`)
- **Bối cảnh**: thư mục `qbank-console-vue3/` chỉ là **bản demo** để chốt UI/luồng; sản phẩm thật là **một** app quản trị duy nhất `fe_web_admin` (Vue 3 + Vite + vue-router + axios), dùng chung phiên đăng nhập JWT với các trang hiện có.
- **Nguyên tắc**: không copy nguyên app demo vào repo; chỉ **chuyển giao diện + luồng**, còn tầng gọi API viết lại theo kiến trúc sẵn có của `fe_web_admin`.

---

## 1. Hiện trạng hai phía

| Hạng mục | `fe_web_admin` (đích) | `qbank-console-vue3` (demo, nguồn) |
|---|---|---|
| Stack | Vue 3.5, Vite 8, vue-router 4, axios 1.18, Vuetify 3, Bootstrap 5, `@phosphor-icons/vue` | Vue 3 + Vite, tự dựng CSS, dữ liệu giả `src/data/mock.js` |
| Tổ chức | `src/pages/*`, `src/components/*`, `src/repository/*` (nghiệp vụ), `src/services/spring/*` (HTTP), `src/model/*`, `src/local_storage/*` | một tầng `views/` + `data/mock.js`, gọi thẳng mock |
| Router | `src/router/routers_before_login.js` + `routers_after_login.js` (mảng phẳng, cờ `allow`), dựng trong `main.js` | router riêng của app demo |
| Xác thực | JWT access/refresh + `SilentTokenRefresher`; `SpringConfigHeader.getAuthorizedAPIClient(tokenLogin)` tự gắn `Authorization` và tự làm mới khi 401 | không có (demo tĩnh) |
| Cấu hình | `src/services/NetworkConfiguration.js` đọc `VITE_SPRING_*`, tiền tố `/api/web`; `.env.example` (không commit `.env`) | hằng số trong mã |
| Trạng thái | không Pinia: `localStorage` + lớp `model` | `ref()` cục bộ trong view |

**Kết luận**: phần dùng lại được từ demo là **template/CSS/luồng tương tác**; phần phải viết lại là **router entry, lớp service HTTP, model, xử lý lỗi và trạng thái**.

---

## 2. Bản đồ màn hình → route → API (theo NAV của `UI_Accept_v9/v10`)

| Màn (NAV) | Route đề xuất trong `fe_web_admin` | API dùng (đã có sau patch v2) | Vai |
|---|---|---|---|
| `dash` — Tổng quan | `/dashboard` | `GET /web/admin/dashboard/summary`, `GET /web/admin/dashboard/activity` | ADMIN, LECTURER |
| `bank` — Tất cả câu hỏi | `/qbank/questions` | `GET /web/admin/questions` (phân trang, lọc), `POST`, `PATCH /{id}`, `POST /{id}/archive` | ADMIN, LECTURER |
| `courses` — Môn học | `/qbank/subjects` | `GET /web/admin/catalog/subjects` | ADMIN, LECTURER |
| `course` — Câu hỏi theo môn | `/qbank/subjects/:subjectId` | `GET /catalog/subjects/{id}/topics`, `GET /questions?subjectId=…` | ADMIN, LECTURER |
| `search` — Tìm kiếm thông minh | `/qbank/search` | *(chưa có API)* → dùng tạm `GET /questions?q=…`; API 3 bước là việc của Server 2 | ADMIN, LECTURER |
| `review` — Xem xét đề xuất AI | `/qbank/review` | `GET /ai/jobs/{taskId}/suggestions`, `POST /ai/suggestions/{id}/decision` | ADMIN, LECTURER |
| `tasks` — Tác vụ AI | `/qbank/tasks` | `POST /questions/{id}/ai-check` → 202, `GET /ai/jobs/{taskId}` (poll 1,5 s) | ADMIN, LECTURER |
| `moodle` — Moodle XML | `/qbank/moodle` | *(chưa có API)* — cần `GET /questions/export/moodle` + `POST /questions/import/moodle` | ADMIN |
| `admin` — Quản trị hệ thống | `/system/accounts` | *(ngoài phạm vi patch; `AdminControllerTest` chỉ có ở profile dev)* | ADMIN |
| `llm` — Cài đặt LLM | `/system/llm` | *(thuộc Server 2 `server-ai-worker`)* | ADMIN |
| `account` — Thông tin tài khoản | `/information-admin` (đã có) | dùng trang `InformationPage` hiện tại | mọi vai |
| `settings` — Cài đặt | `/settings` | *(chưa có API)* | mọi vai |

Chi tiết hợp đồng API (19 endpoint, mã lỗi, Redis) xem `docs/api-qbank-redis.md`.

---

## 3. Bản đồ file khi gộp

```
fe_web_admin/src/
├── pages/qbank/
│   ├── DashboardPage.vue            # từ demo: dash
│   ├── QuestionListPage.vue         # bank
│   ├── QuestionEditorPage.vue       # bank → dialog/màn soạn + AI check
│   ├── SubjectListPage.vue          # courses
│   ├── SubjectQuestionsPage.vue     # course (dùng route param subjectId)
│   ├── SuggestionReviewPage.vue     # review
│   └── AiTaskPage.vue               # tasks
├── components/qbank/                # tách từ demo: bảng câu hỏi, badge mức độ, khung so sánh gốc/đề xuất,
│                                    #   ô chọn đáp án A–D, thanh tiến trình AI, toast lỗi
├── services/spring/QbankService.js  # gọi HTTP, dùng SpringConfigHeader.getAuthorizedAPIClient(...)
├── repository/QbankRepository.js    # nghiệp vụ + chuẩn hoá lỗi (code → thông điệp tiếng Việt)
├── model/qbank/                     # QuestionSummary, QuestionDetail, AiSuggestion, DashboardSummary…
├── local_storage/QbankUiStorage.js  # (tuỳ chọn) chỉ lưu thiên hướng UI: cột đang xem, filter gần nhất
└── router/routers_after_login.js    # thêm 7 route ở §2 (routers_before_login: chỉ /login, /404)
```

Quy ước: `pages/` chỉ hiển thị + bắt sự kiện; **không** gọi `axios` trực tiếp. Mọi lời gọi đi qua `repository → service`, đúng như `AdminRepository` đang làm.

---

## 4. Trình tự thực hiện

1. **Chuyển giao diện**: copy từng view demo thành `pages/qbank/*.vue`, đổi tên class CSS sang tiền tố `qb-` để không đụng `theme-color.scss` / `tag-style.scss`; dùng lại `ButtonBlue/ButtonRed/ButtonOrange` và component bảng sẵn có thay vì viết mới.
2. **Cắt mock**: xoá `mock.js` khỏi app thật. Nếu cần chạy offline, giữ một file `pages/qbank/__mock__.js` sau cờ `VITE_USE_QBANK_MOCK=true` (mặc định `false`).
3. **Viết `QbankService`**: mỗi hàm = 1 endpoint, nhận/trả plain object; lấy client bằng `tokenLogin` đang có trong `AdminStorage` — **không** tạo axios instance riêng, để dùng chung `SilentTokenRefresher`.
4. **Chuẩn hoá lỗi**: backend trả `{success,code,message,at}`; `QbankRepository` map `code` → thông điệp tiếng Việt và hành vi:
   - `VERSION_CONFLICT` (409) → báo "Câu hỏi vừa được người khác sửa" + nút tải lại;
   - `QUESTION_ARCHIVED` (409) → chuyển sang chế độ chỉ đọc;
   - `AI_WORKER_UNAVAILABLE` (503) → "Máy chủ AI không phản hồi, thử lại sau";
   - `CHECK_IN_PROGRESS` (409) → hiện đồng hồ chờ theo job đang chạy (`ui:check:lock` 15 s).
5. **Router + menu**: thêm route vào `routers_after_login.js`; sidebar đọc danh sách route được phép theo vai (giải mã claim `role` trong access token đã lưu: `ADMIN` thấy cả `moodle`/`admin`, `LECTURER` ẩn 2 mục đó).
6. **Đồng bộ trạng thái soạn thảo**: form nháp gọi `GET/PUT/DELETE /questions/draft` (Redis `ui:draft:{adminId}:{new|qid}`, TTL 24 h) khi debounce 800 ms; rời trang không mất nội dung.
7. **Kiểm thử**: `npm run build` phải sạch cảnh báo; kiểm 5 luồng — đăng nhập → dashboard; tạo câu hỏi; sửa có xung đột phiên bản; chạy AI check rồi poll; duyệt/từ chối đề xuất.

---

## 5. Đối chiếu dữ liệu: mock demo ↔ DTO backend

| Khoá trong mock demo | DTO/field backend | Ghi chú |
|---|---|---|
| `ENUM_DB` (nhãn enum) | `enums/*` + `@JsonValue` | BE trả **giá trị DB viết thường** (`easy`, `medium`, `hard`); nhãn hiển thị để FE giữ (không dịch ở BE) |
| `ENUM_DB.sev` | `QuestionDetail.severity` | cùng tập giá trị |
| `stB` (trạng thái câu hỏi) | `QuestionSummary.status` | `draft`/`pending`/`approved`/`archived` |
| danh sách lựa chọn A–D | `QuestionDetail.options[]` với `label` = `A`–`D` | nhãn đáp án dùng `OptionLabelD` (chữ hoa) |
| `contentHash` (demo không có) | `QuestionDetail.contentHash` | FE gửi lại khi PATCH để BE phát hiện sửa đồng thời |
| thẻ dashboard | `DashboardSummary` (tổng câu hỏi, theo độ khó, chờ duyệt, tác vụ AI đang chạy) | Redis cache 30 s, có thể cũ tối đa 30 s |

---

## 6. Rủi ro và cách xử lý

1. **Hai lớp xác thực**: demo không có token. Khi gộp phải bỏ hẳn client riêng, nếu không sẽ có 2 cơ chế refresh chạy song song → 401 dây chuyền.
2. **Timeout AI**: `VITE_REQUEST_TIMEOUT_MS=15000` nhưng `POST /questions/{id}/ai-check` chỉ trả 202 ngay; **không** đợi kết quả trong một request — phải poll `ai/jobs/{taskId}`.
3. **CORS/origin**: Vite dev chạy cổng 5173, Spring chỉ cho `app.cors.allowed-origins` khai báo trong `application.yaml`; thêm cổng đang dùng trước khi test.
4. **Enum chữ thường**: đừng `toUpperCase()` giá trị DB để hiển thị — dùng bảng nhãn ở FE, tránh lệch khi BE đổi tập giá trị.
5. **Phiên bản ghi đè**: PATCH bắt buộc gửi `version`; thiếu thì BE trả 409 `VERSION_CONFLICT`.
6. **Màn chưa có API** (search NLP, Moodle XML, LLM, quản trị tài khoản): gộp UI nhưng phải để **trạng thái "chưa hỗ trợ"** hoặc ẩn sau cờ, không giả lập dữ liệu trên môi trường thật.
7. **.env**: nhánh v9 đã xoá `fe_web_admin/.env` khỏi Git — chỉ dùng `.env.example`, không commit `.env`.

---

## 7. Checklist nghiệm thu

- [ ] `fe_web_admin` chỉ còn **một** app, không có thư mục `qbank-console-vue3/`.
- [ ] Đăng nhập một lần, vào được 7 route ngân hàng câu hỏi mà không phải đăng nhập lại.
- [ ] Access token hết hạn giữa phiên: request qbank vẫn thành công (silent refresh), không nhảy về `/login`.
- [ ] Giảng viên không thấy mục Moodle/Quản trị; gọi thẳng URL bằng tài khoản giảng viên bị chặn ở FE và BE trả 403.
- [ ] Sửa câu hỏi ở 2 tab: tab sau nhận 409 `VERSION_CONFLICT` và có nút tải lại.
- [ ] AI check: nút chuyển sang "đang chạy", poll dừng khi job xong, đề xuất hiện ở màn Duyệt.
- [ ] `npm run build` thành công; không còn import nào trỏ tới `mock.js`.
