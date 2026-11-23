# Đối chiếu `UI_Accept_v11.html` (bản chốt) + `qbank-console-vue3` với backend

- **Ngày**: 2026-11-22 · **Nền**: `feature/apply-ui-project-thesis` @ `36a697583` (UI_v11 chốt) · đã gồm patch ORM.
- **Trạng thái**: phần giao diện đã được ghép vào `fe_web_admin` — xem mục 7.
- **Mục đích**: chỉ ra chính xác giao diện mới cần những API nào, cái nào đã có, cái nào thiếu — để khi ghép demo vào `fe_web_admin` không phải đoán.

---

## 1. `UI_Accept_v10.html` mới hơn v9 ở đâu

So với `v9` (1.992 dòng) bản `v10` (2.102 dòng) **không thêm màn hình** — vẫn 9 mục menu + 3 màn phụ:

```
Tổng quan · Câu hỏi (Tất cả / Môn học / Tìm kiếm thông minh) ·
Kiểm định AI (Xem xét đề xuất / Tác vụ) · Tích hợp (Moodle XML) ·
Quản trị (Quản trị hệ thống / Cài đặt LLM) + Tài khoản / Cài đặt / Câu hỏi theo môn
```

Phần mới là **hành vi giao diện**:

| Thêm trong v10 | Ý nghĩa với backend |
|---|---|
| `routeHash` / `syncHash` / `DEFAULT_PAGE='dash'` — điều hướng `#/trang`, `#/course/:i` | Mỗi màn phải **deep-link được**. Route FE nên trùng ý nghĩa: `/dashboard`, `/qbank/questions`, `/qbank/subjects/:subjectId`… |
| `fcap` / `frest` / `fkey` — giữ focus sau khi vẽ lại, Esc đóng drawer | Không ảnh hưởng backend |
| `toastArm` — toast tạm dừng khi rê chuột, **Ctrl+Z hoàn tác xoá** | **Xoá phải hoàn tác được** → cần API khôi phục câu hỏi đã lưu trữ (đã bổ sung, xem §3) |
| Bảng Tác vụ thành thẻ trên điện thoại, thanh chọn nhiều không che hàng cuối | Chọn nhiều dòng → cần **API hàng loạt** thay vì N request rời |

Ngoài ra v10 **thay thế** `UI_Accept_v9_Thesis.html` (file v9 không còn trong repo) — tài liệu `docs/api-qbank-redis.md` đang dẫn chiếu tên cũ, cần sửa khi rảnh.

---

## 2. Vue demo `qbank-console-vue3` đang gọi gì

**13 route** (`src/router/index.js`), đúng bằng 12 màn của mockup + `/settings`:
`/dash`, `/bank`, `/course/:ci`, `/courses`, `/review`, `/tasks`, `/search`, `/moodle`, `/admin`, `/llm`, `/account`, `/settings`.
Router đã chặn sẵn: `admin`/`llm` chỉ vào được khi `S.role === 'admin'` → khớp với `@PreAuthorize("hasRole('ADMIN')")` sẽ có sau khi merge v9.

**Tầng dữ liệu hiện tại** (`src/repositories/questionRepository.js`) — chỉ 4 hàm mock:

```js
findAll()            // -> mảng câu hỏi
upsert(q)            // tạo nếu chưa có q.id, sửa nếu có
removeMany(ids)      // xoá nhiều
restoreMany(items)   // hoàn tác xoá
```

**Hành vi nghiệp vụ trong `src/store.js`** (đây mới là danh sách thật cần API):

| Hàm trong store | Việc nó làm |
|---|---|
| `loadQuestions()` | nạp toàn bộ ngân hàng câu hỏi |
| `saveQuestion(d, eid)` | tạo mới / cập nhật theo `eid` |
| `removeQuestions(ids)` | xoá nhiều + toast "Hoàn tác" gọi lại `restoreMany` |
| `deleteQuestion(id)` | xoá 1 + hoàn tác |
| `runAI(ids)` | gửi **nhiều** câu hỏi đi kiểm định cùng lúc |
| `decide(id, ok)` | duyệt / từ chối đề xuất AI |
| `addCourse / updateCourse / deleteCourse` | CRUD môn học; xoá môn xoá luôn câu hỏi thuộc môn |
| `log(...)`, `toast(...)` | chỉ giao diện |

---

## 3. Ma trận đối chiếu và phần đã bổ sung

| Việc FE cần | API backend | Trạng thái |
|---|---|---|
| `loadQuestions()` | `GET /api/web/admin/questions` (trả `PageResponse`) | ✅ có, **FE phải bóc `content`** (`PageResponse` của Spring) thay vì coi là mảng |
| `saveQuestion` khi tạo | `POST /api/web/admin/questions` | ✅ có |
| `saveQuestion` khi sửa | `PUT /api/web/admin/questions/{id}` + `expectedVersion` | ✅ có; FE gửi `version` → đã thêm `@JsonAlias` |
| `removeQuestions(ids)` | **`POST /questions/bulk-archive`** `{ids:[...]}` | 🆕 **thêm trong patch này** |
| `deleteQuestion(id)` | `DELETE /questions/{id}` (lưu trữ mềm) | ✅ có |
| `restoreMany` / Ctrl+Z | **`POST /questions/{id}/restore`**, **`POST /questions/bulk-restore`** | 🆕 **thêm trong patch này** |
| `runAI(ids)` | `POST /questions/{id}/ai-check` (mỗi câu một request) | ⚠️ chưa có bản hàng loạt — FE gọi song song tối đa ~5 request, hoặc bổ sung sau |
| `decide(id, ok)` | `POST /ai/suggestions/{id}/decision` | ✅ có |
| Danh sách môn/chương | `GET /catalog/subjects`, `GET /catalog/subjects/{id}/topics` | ✅ có (chỉ đọc) |
| `addCourse/updateCourse/deleteCourse` | — | ❌ **thiếu**: chưa có API ghi cho `ntt_subjects`/`ntt_topics` |
| `search` (tìm ngữ nghĩa) | — | ❌ thiếu (Server 2) |
| `moodle` xuất/nhập XML | — | ❌ thiếu |
| `llm` cấu hình node Colab | — | ❌ thiếu (Server 2) |
| `admin` quản trị tài khoản | chỉ có API `dev/local/test` | ❌ thiếu bản production |
| `settings` | — | ❌ thiếu |

### Ba endpoint mới trong patch này

```
POST /api/web/admin/questions/bulk-archive   { "ids": [12, 13, 14] }
POST /api/web/admin/questions/bulk-restore   { "ids": [12, 13, 14] }
POST /api/web/admin/questions/{id}/restore
```

Trả về (2 endpoint hàng loạt):

```json
{ "requested": 3, "done": [12, 13], "skipped": [
  { "questionId": 14, "code": "ALREADY_ARCHIVED", "message": "Câu hỏi đã ở trạng thái lưu trữ." } ] }
```

Quy tắc đã cài:
- Khôi phục chỉ chạy khi câu hỏi đang `archived`, nếu không trả **409 `NOT_ARCHIVED`**.
- Mỗi lần lưu trữ / khôi phục đều sinh **revision** (`change_source = archive` / `restore` — giá trị `RESTORE` vốn đã có trong CSDL v4 nhưng chưa dùng) và một bản ghi hoạt động (`QUESTION_ARCHIVED` / `QUESTION_RESTORED`).
- Hàng loạt: **tối đa 100 id/lượt**, một id lỗi **không** làm hỏng cả lô (không rollback), id nào xong ghi vào `done`.
- Ghi revision cho từng câu nên 100 id = 100 revision + 100 hoạt động — chấp nhận được, nhưng FE không nên gọi với cả ngân hàng câu hỏi.

---

## 4. Ba chỗ lệch hợp đồng phải sửa, nếu không sẽ 400/404 ngay khi ghép

1. **Đường dẫn**: `QuestionDialog.vue` ghi "gửi Server 1 (`POST /api/questions`)" nhưng thật là `POST /api/web/admin/questions`. Sửa phía FE, **không** tạo alias ở backend.
2. **Thang độ khó**: demo `LV_DB = [1,2,3]`, DDL `CHECK (1..5)` và DTO `@Min(1) @Max(5)`. Backend đúng theo CSDL → **sửa demo**. Nếu nhóm muốn chốt 3 mức thì phải sửa DDL (thay đổi CSDL, không phải vá code).
3. **Dạng dữ liệu danh sách**: `findAll()` của demo trả **mảng**, backend trả `{content, page, size, totalElements, totalPages}`. Khi thay thân hàm mock bằng `fetch`, phải map `res.content` — nếu không giao diện sẽ trắng bảng.

---

## 5. Việc còn lại, ước lượng

| Việc | Khối lượng | Ghi chú |
|---|---|---|
| CRUD môn học + chương (`addCourse/updateCourse/deleteCourse`) | ~1 ngày | Cần chốt: xoá môn là **lưu trữ mềm** hay chặn khi còn câu hỏi (DDL `ntt_questions.subject_id` FK RESTRICT → hiện xoá cứng sẽ lỗi) |
| Bulk AI check | ~2 giờ | Mỗi câu một job; nên trả `[{questionId, taskId|error}]` |
| Tìm kiếm ngữ nghĩa | Server 2 | 3 bước: phân tích → truy vấn → xếp hạng |
| Moodle XML xuất/nhập | ~1 ngày | Xuất trước, nhập sau (nhập cần kiểm định trùng) |
| Quản trị tài khoản (production) | ~1 ngày | Bỏ `@Profile` dev, thêm phân trang + `@PreAuthorize('ADMIN')` |

---

## 6. Kiểm chứng sau khi áp patch

```bash
# lưu trữ hàng loạt
curl -X POST localhost:8080/api/web/admin/questions/bulk-archive \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"ids":[1,2,3]}'
# hoàn tác
curl -X POST localhost:8080/api/web/admin/questions/bulk-restore \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"ids":[1,2,3]}'
# khôi phục câu chưa từng lưu trữ -> 409 NOT_ARCHIVED
curl -i -X POST localhost:8080/api/web/admin/questions/1/restore -H "Authorization: Bearer $TOKEN"
# vượt 100 id -> 400 VALIDATION_FAILED
```

Sau khi áp, `ntt_question_revisions` phải có bản ghi mới với `change_source = 'archive'` / `'restore'`, và `GET /questions/{id}/revisions` hiển thị đúng hai loại này.

---

## 7. Trạng thái ghép giao diện vào `fe_web_admin` (đã làm)

Đã hiện thực trong `fe_web_admin/src` (build `vite build` chạy qua, 1.770 module):

| File | Nội dung |
|---|---|
| `services/spring/QbankService.js` | 1 hàm / endpoint, dùng chung `getAuthorizedAPIClient` (Bearer + silent refresh) |
| `repository/QbankRepository.js` | nghiệp vụ + chuẩn hoá `PageResponse.content` → `items` + map `code` lỗi sang thông điệp tiếng Việt |
| `model/qbank/constants.js` | nhãn enum (1..5 độ khó, trạng thái kiểm định, mức nghiêm trọng, task status) |
| `model/qbank/sessionRole.js` | đọc claim `role` của access token để ẩn/hiện menu (backend vẫn là nơi chặn thật) |
| `components/qbank/QbankLayout.vue` | khung sidebar theo NAV của v11 + ẩn mục quản trị với LECTURER |
| `components/qbank/QuestionFormDialog.vue` | tạo/sửa câu hỏi, gửi `version` khi sửa, tự lưu nháp Redis sau 800ms |
| `pages/qbank/DashboardPage.vue` | thẻ số liệu + trạng thái kiểm định + hoạt động gần đây |
| `pages/qbank/QuestionListPage.vue` | lọc, phân trang, chọn nhiều, lưu trữ/khôi phục, chạy kiểm định AI |
| `pages/qbank/SubjectListPage.vue` | danh sách môn + chương (chỉ đọc) |
| `pages/qbank/AiReviewPage.vue` | poll tác vụ AI (dùng `pollAfterMs`), xem đề xuất, nhận/từ chối |
| `pages/qbank/PlaceholderPage.vue` | 5 màn chưa có API — hiển thị rõ phần còn thiếu, không dùng dữ liệu giả |
| `router/routers_after_login.js` / `routers_before_login.js` | 9 route (`/dashboard`, `/qbank/*`), bản trước đăng nhập để `allow:false` |
| `assets/style/qbank.scss` | style tiền tố `qb-`, nạp trong `main.js` |

Còn lại: CRUD môn/chương, tìm kiếm ngữ nghĩa, Moodle XML, cài đặt LLM, quản trị tài khoản (mục 5),
và dọn `qbank-console-vue3/` sau khi PR giao diện được chấp nhận.
