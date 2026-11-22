# ORM (Spring JPA) cho CSDL v4 + trạng thái giao diện trên Redis

- **Ngày**: 2026-11-22 · **Nhánh**: `arena/aa735601-project-thesis-ntt` · **Nền**: commit `8bc435e`
- **Phạm vi**: hiện thực tầng ánh xạ ORM cho **14 bảng mới** của CSDL v4 trong `be-web-admin` (Server 1) và tầng Redis cho các tác vụ giao diện mà `project-design/UI_Accept_v9_Thesis.html` mô tả.
- **Không sửa** file migration SQL nào: DDL vẫn là nguồn chân lý, entity bám theo đúng cột/kiểu/NOT NULL/ENUM của DDL (đã kiểm bằng script, xem §7).
- **Tài liệu liên quan**: `docs/review-csdl-v4-chot-vs-code.md` (đối chiếu 3 tầng + 17 rủi ro), `docs/review-latest-commit-2026-11-22.md` (rà mã nguồn).

---

## 1. Ánh xạ 3 tầng thiết kế

Ba tầng của đề án được nối bằng đúng một chuỗi, mỗi mắt xích nằm ở một nơi:

```
Class diagram v4        ER/SQL diagram v4          DDL (sql/*.sql)            Entity JPA                  Repository            Service / Controller
Subject              →  ntt_subjects             →  CREATE TABLE ...        →  qbank/Subject           →  SubjectRepository     →  CatalogService
Question             →  ntt_questions            →  ...                     →  qbank/Question          →  QuestionRepository    →  QuestionService
QuestionRevision     →  ntt_question_revisions   →  ...                     →  qbank/QuestionRevision  →  ...RevisionRepository →  QuestionService.appendRevision
Topic                →  ntt_topics               →  ...                     →  qbank/Topic             →  TopicRepository       →  CatalogService
IssueType            →  ntt_issue_types          →  ...                     →  ai/IssueType            →  IssueTypeRepository   →  CatalogService
AiTask               →  ntt_ai_tasks             →  ...                     →  ai/AiTask               →  AiTaskRepository      →  AiCheckService
AiSuggestion         →  ntt_ai_suggestions       →  ...                     →  ai/AiSuggestion         →  AiSuggestionRepository →  SuggestionReviewService
MoodleProfile        →  ntt_moodle_profiles      →  ...                     →  moodle/MoodleProfile    →  ...ProfileRepository
MoodleExchange       →  ntt_moodle_exchanges     →  ...                     →  moodle/MoodleExchange   →  ...ExchangeRepository
MoodleExchangeItem   →  ntt_moodle_exchange_items→  ...                     →  moodle/...ExchangeItem  →  ...ItemRepository
EvalRun              →  ntt_eval_runs            →  ...                     →  eval/EvalRun            →  EvalRunRepository
EvalItem             →  ntt_eval_items           →  ...                     →  eval/EvalItem           →  EvalItemRepository
EvalLabel            →  ntt_eval_labels          →  ...                     →  eval/EvalLabel          →  EvalLabelRepository
EvalPrediction       →  ntt_eval_predictions     →  ...                     →  eval/EvalPrediction     →  EvalPredictionRepository
```

7 bảng xác thực (`ntt_admins`, `ntt_courses`, `ntt_web_sessions`, …) đã có entity từ trước, không đụng tới.

### 1.1 Enum: chỗ 3 tầng lệch nhau nhiều nhất — đã xử lý dứt điểm

CSDL v4 dùng **chữ thường** (`enum('not_checked','clean',...)`) còn hằng số Java viết HOA, nên
`@Enumerated(STRING)` sẽ ghi `CLEAN` vào cột `enum('clean',...)`. Cách làm ở đây:

| Thành phần | Vai trò |
|---|---|
| `enums/qbank/*`, `enums/ai/*`, `enums/moodle/*`, `enums/eval/*` | 22 enum, mỗi enum giữ `dbValue()` (giá trị thật trong CSDL) và `SQL_TYPE` (chuỗi `enum('a','b')`) |
| `converter/*Converter` | 22 `AttributeConverter` chuyển enum ⇄ `dbValue`; entity dùng `@Convert(converter = …)` |
| `@Column(columnDefinition = ValidationStatusD.SQL_TYPE)` | Kiểu cột trong entity **không thể** lệch danh sách giá trị, vì cả hai đọc từ một hằng số |
| `@JsonValue` / `@JsonCreator` | JSON của API dùng đúng mã CSDL (`"not_checked"`, `"A"`, `"plain"`), không dùng tên hằng số |

Ba enum có giá trị **không** theo quy tắc "lowercase của tên": `OptionLabelD` (`A`–`D`),
`EvalScenarioD` (`E1`–`E8`), `AnswerNumberingD` (`none`, `abc`, `ABCD`, `123` — có giá trị bắt
đầu bằng chữ số nên không thể là tên hằng số).

### 1.2 Cột sinh (GENERATED) cố ý KHÔNG map

`parent_key`, `active_flag`, `default_key`, `annotator_key` do MariaDB sinh. Map vào entity sẽ
làm INSERT gửi giá trị vào cột không cho ghi ⇒ lỗi. Script kiểm tra ở §7 báo lỗi nếu ai map nhầm.

### 1.3 Quan hệ: scalar-first + 3 liên kết chỉ-đọc

Khoá ngoại **ghép** (`(question_id, revision_id)`, `(split, item_id)`, `(subject_id, parent_id)`)
được giữ ở dạng cột scalar (`questionRevisionId`, `itemId`…) thay vì `@ManyToOne`, vì:

- JPA cần đích FK là duy nhất; các cặp này chỉ duy nhất nhờ UNIQUE phụ, dễ vỡ khi Hibernate đổi chiến lược join;
- `open-in-view: false` ⇒ không có lazy load ngoài transaction, nên graph chỉ nên dựng bằng query;
- luồng AI gắn với **revision cụ thể**, không phải "câu hỏi hiện tại" — scalar thể hiện đúng ý đó.

Ba liên kết chỉ-đọc được giữ để minh hoạ graph (không ghi, không ảnh hưởng INSERT/UPDATE):
`QuestionRevision.question`, `AiSuggestion.task`, `MoodleExchangeItem.exchange`, `EvalLabel.item`,
`EvalPrediction.run`, `MoodleExchange.profile`, `EvalItem.question`.

---

## 2. API mới (context-path `/api` đã có sẵn)

Tất cả nằm dưới `/api/web/admin/**` nên **bắt buộc có access token** (SecurityConfig hiện chỉ
phân biệt đăng nhập/không, chưa phân biệt ADMIN/LECTURER — xem §8).

| # | Method | Đường dẫn | Việc |
|---|---|---|---|
| 1 | GET | `/api/web/admin/catalog/subjects?includeInactive=false` | Danh sách môn cho bộ lọc |
| 2 | GET | `/api/web/admin/catalog/subjects/{id}/topics` | Cây chủ đề của 1 môn |
| 3 | GET | `/api/web/admin/catalog/issue-types` | Danh mục loại lỗi (`isEvaluated` để biết loại nào được chấm) |
| 4 | GET | `/api/web/admin/dashboard/summary?subjectId=` | Thẻ KPI + biểu đồ trạng thái (cache Redis 30s) |
| 5 | GET | `/api/web/admin/dashboard/activity?limit=10` | "Hoạt động gần đây" của chính người đăng nhập |
| 6 | GET | `/api/web/admin/questions?subjectId&topicId&status&validationStatus&q&page&size` | Bảng ngân hàng câu hỏi (phân trang, mặc định `updated_at DESC`) |
| 7 | GET | `/api/web/admin/questions/{id}` | Chi tiết 1 câu (kèm `revisionNo`, `pendingSuggestionCount`, `version`) |
| 8 | GET | `/api/web/admin/questions/{id}/revisions` | Lịch sử sửa (append-only) |
| 9 | POST | `/api/web/admin/questions` | Tạo câu hỏi → **201**, tự sinh revision 1 |
| 10 | PUT | `/api/web/admin/questions/{id}` | Sửa (bắt buộc `expectedVersion`) → sinh revision mới |
| 11 | DELETE | `/api/web/admin/questions/{id}` | Xoá mềm = `status='archived'` + revision `archive` |
| 12 | GET | `/api/web/admin/questions/{id}/ai-suggestions` | Đề xuất của lần kiểm định gần nhất |
| 13 | POST | `/api/web/admin/questions/{id}/ai-check` | Gửi kiểm định → **202** + `taskId` + `pollAfterMs` |
| 14 | GET | `/api/web/admin/ai/jobs/{taskId}` | Poll trạng thái (Redis trước, DB sau) |
| 15 | GET | `/api/web/admin/ai/jobs/{taskId}/suggestions` | Đề xuất của đúng task đó |
| 16 | POST | `/api/web/admin/ai/suggestions/{suggestionId}/decision` | Duyệt/từ chối → accepted sinh revision + trả câu hỏi mới |
| 17 | GET/PUT/DELETE | `/api/web/admin/questions/draft?questionId=` | Nháp form (Redis, TTL 24h) |

Định dạng lỗi (chỉ áp cho 4 controller của module, không đổi lỗi của auth/chat):

```json
{ "success": false, "code": "VERSION_CONFLICT", "message": "...", "at": "2026-11-22T07:31:02Z" }
```

Mã lỗi chính: `SUBJECT_NOT_FOUND`, `SUBJECT_INACTIVE`, `TOPIC_NOT_IN_SUBJECT`,
`QUESTION_NOT_FOUND`, `QUESTION_ARCHIVED`, `QUESTION_WITHOUT_REVISION`, `VERSION_CONFLICT`,
`CHECK_IN_PROGRESS`, `AI_WORKER_NOT_CONFIGURED`, `AI_WORKER_UNAVAILABLE`, `AI_WORKER_REJECTED`,
`TASK_NOT_FOUND`, `SUGGESTION_ALREADY_DECIDED`, `SUGGESTION_STALE`, `TARGET_NOT_APPLICABLE`,
`DB_CONSTRAINT`, `VALIDATION_FAILED`.

---

## 3. Redis cho tác vụ giao diện

Nguyên tắc: **Redis không bao giờ là nguồn sự thật**. Mất Redis ⇒ UI chậm hơn, dữ liệu vẫn đúng.
Mọi thao tác Redis đều fail-open (ghi log, cho qua) như `ChatRateLimiter` đang làm.

| Khoá | Kiểu | TTL | Ghi ở đâu | Đọc ở đâu |
|---|---|---|---|---|
| `ui:dashboard:all` | String (JSON `DashboardSummary`) | 30s | `DashboardService.summary` | `GET /dashboard/summary` |
| `ui:dashboard:subject:{id}` | String (JSON) | 30s | như trên | `GET /dashboard/summary?subjectId` |
| `ui:activity:{adminId}` | List (LPUSH + LTRIM 20) | 7 ngày | tạo/sửa/lưu trữ câu hỏi, gửi kiểm định, duyệt đề xuất | `GET /dashboard/activity` |
| `ui:draft:{adminId}:{new\|questionId}` | String (JSON) | 24h | `PUT /questions/draft` | `GET /questions/draft` |
| `ui:check:lock:{questionId}` | String `"1"` (SET NX) | 15s | `AiCheckService.submit` | cùng chỗ (chống bấm liên tục) |
| `ui:ai_job:{taskId}` | Hash (`@RedisHash`) | 1h | submit + mỗi lần poll làm mới | `GET /ai/jobs/{taskId}` |

### 3.1 Bản đồ màn hình → Redis (theo `UI_Accept_v9_Thesis.html`)

| Màn | Dùng Redis gì | Ghi chú |
|---|---|---|
| 1. Dashboard | `ui:dashboard:*` + `ui:activity:*` | 1 request `/summary` thay cho 6 lần COUNT; hoạt động gần đây đọc list 20 dòng |
| 2. Danh sách câu hỏi | không | Dữ liệu phân trang luôn đọc DB (đúng, không cache danh sách) |
| 3. Form tạo/sửa | `ui:draft:*` | Tự lưu nháp 24h, xoá khi lưu thành công |
| 4. Modal "Kiểm định AI" | `ui:check:lock:*` + `ui:ai_job:*` | Chống double-submit 15s; poll 1,5s/lần, kết thúc thì `pollAfterMs = 0` |
| 5. Duyệt đề xuất | không (ghi DB) | Áp dụng đề xuất là thay đổi nghiệp vụ: transaction + revision |
| 6–7. Xuất/nhập Moodle | không (chưa làm) | Nhật ký nằm ở `ntt_moodle_exchanges` |
| 8–9. Người dùng / phân quyền | không (chưa làm) | — |
| 10. Thống kê Chương 4 | không (chưa làm) | Nên đọc `ntt_eval_*` và tính ở service (view `vw_eval_confusion` đang sai FN) |
| 11. Cài đặt | không | — |
| 12. Trang đăng nhập | đã có từ trước | `enroll_session`, `login_challenge`, … |

### 3.2 Vì sao job view nằm ở Redis mà trạng thái vẫn ở DB

Worker (Server 2) tự ghi `ntt_ai_tasks` và tự quản Redis của **nó**. Server 1 không thấy thay đổi
của Redis bên kia, nên `GET /ai/jobs/{taskId}` làm như sau: đọc `ui:ai_job:{taskId}`; nếu trạng
thái còn `pending|running` **và** ảnh chụp cũ hơn 5 giây thì đọc lại MariaDB rồi làm mới Redis.
Nhờ vậy: nhịp poll bình thường là 1 lệnh Redis, còn khi worker chạy xong thì UI vẫn thấy `succeeded`
trong tối đa 5 giây.

---

## 4. Ba luồng chính

### 4.1 Tạo câu hỏi (đóng D3)

```
POST /questions
  ├─ validate subject active, topic thuộc đúng subject (FK ghép (subject_id, topic_id))
  ├─ correctLabel/contentFormat parse theo mã CSDL; difficulty 1..5
  ├─ content_hash = SHA-256(NFC+trim+1 dấu cách, phân tách bằng U+0001)   ← QuestionContentHasher
  ├─ INSERT ntt_questions (version = 0, status = active, validation_status = not_checked)
  └─ INSERT ntt_question_revisions (rev_no = 1, change_source = 'create', snapshot_json)
        snapshot CHÍNH LÀ input_json gửi Server 2 (cùng bộ khoá stem/option_a..d/correct_label)
```

### 4.2 Kiểm định AI (đóng A2 một nửa: Server 1 gọi Server 2 thật)

```
POST /questions/{id}/ai-check
  ├─ câu phải active, phải có revision mới nhất (nếu không: 409 QUESTION_WITHOUT_REVISION)
  ├─ đã có task pending/running? → trả luôn task đó (idempotent)
  ├─ SET NX ui:check:lock:{id} (15s)  → không giành được: 429 CHECK_IN_PROGRESS
  ├─ POST {worker}/web/ai/tasks  (header X-Internal-Token, body: taskType + question/revision id
  │     + createdBy + inputJson = snapshot_json của ĐÚNG revision)
  ├─ nhận taskId → ghi ui:ai_job:{taskId} + activity + xoá cache dashboard
  └─ 202 { taskId, status, pollAfterMs: 1500 }
        lỗi mạng/timeout → 503 AI_WORKER_UNAVAILABLE + MỞ khoá để bấm lại
```

### 4.3 Duyệt đề xuất (đóng D6/D8)

```
POST /ai/suggestions/{id}/decision { decision: accepted, finalText?, expectedVersion? }
  ├─ đề xuất phải còn pending (không: 409 SUGGESTION_ALREADY_DECIDED)
  ├─ revision AI đã đọc phải là revision mới nhất (không: đánh dấu stale + 409 SUGGESTION_STALE)
  ├─ target 'question' → 400 TARGET_NOT_APPLICABLE (người duyệt tự sửa; AI không đổi cấu trúc)
  ├─ áp finalText (mặc định proposed_text) vào stem/option_*, suggested_difficulty nếu có
  ├─ validation_status = stale; content_hash tính lại
  ├─ INSERT revision (change_source = 'ai_suggestion')  ← applied_revision_id trỏ vào đây
  ├─ đề xuất: decision = accepted, decided_by/at, final_text, applied_revision_id
  ├─ các đề xuất pending khác của câu → stale (nội dung cũ không còn đúng)
  └─ trả QuestionDetail mới (version đã tăng) để UI không phải tải lại
```

---

## 5. Cấu hình

`application.yaml` (đã thêm):

```yaml
app:
  ai-worker:
    base-url: ${AI_WORKER_BASE_URL:}       # vd http://127.0.0.1:8082/api
    internal-token: ${AI_INTERNAL_TOKEN:}  # trùng app.ai.security.internal-token của worker
    connect-timeout-ms: 3000
    read-timeout-ms: 20000
```

`.env.example` đã thêm 2 biến trên. Để trống `AI_WORKER_BASE_URL` ⇒ nút kiểm định trả 503 rõ
ràng, **không** tạo task "treo" trong `ntt_ai_tasks`.

---

## 6. Chạy thử (demo luồng đầy đủ)

```bash
# 1. DB: chạy DDL (theo thứ tự 00-baseline → 01 → 02 → 03 nếu đã bổ sung baseline)
# 2. Server 2 (Colab/tunnel) + biến AI_INTERNAL_TOKEN
# 3. Server 1
./gradlew bootRun

# 4. Đăng nhập lấy access token (endpoint cũ)
TOKEN=...

# 5. Tạo câu hỏi
curl -s -X POST localhost:8080/api/web/admin/questions \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"subjectId":1,"stem":"Thủ đô của Việt Nam là gì?",
       "optionA":"Hà Nội","optionB":"Huế","optionC":"Đà Nẵng","optionD":"TP. Hồ Chí Minh",
       "correctLabel":"A","difficulty":2}'

# 6. Gửi kiểm định → nhận taskId
curl -s -X POST localhost:8080/api/web/admin/questions/1/ai-check \
  -H "Authorization: Bearer $TOKEN"

# 7. Poll (1 lệnh Redis khi chưa quá 5s)
curl -s localhost:8080/api/web/admin/ai/jobs/$TASK_ID -H "Authorization: Bearer $TOKEN"

# 8. Duyệt đề xuất
curl -s -X POST localhost:8080/api/web/admin/ai/suggestions/1/decision \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"decision":"accepted","finalText":"Thủ đô của Việt Nam là thành phố nào?","expectedVersion":1}'
```

---

## 7. Kiểm chứng đã chạy trong môi trường này

| Việc | Kết quả |
|---|---|
| `python3 be-web-admin/scripts/check-orm-mapping.py` | **PASS** — 14 entity ↔ 17 bảng DDL: mọi `@Column` có thật, đủ cột NOT NULL, ENUM khớp, không map cột sinh |
| Kiểm tra script có bắt được lỗi (thêm 1 entity sai cột) | **PASS** — báo 15 lỗi, exit code 1 |
| `QuestionContentHasher` test vector | hash `46e00b1b…f3ef727` tính độc lập bằng Python `hashlib` |
| Cân bằng ngoặc/khai báo package của 107 file Java mới | **PASS** |
| Biên dịch / chạy Spring | **KHÔNG CHẠY ĐƯỢC**: sandbox không có JDK, MariaDB, Redis (xem §8) |

Vì không có JDK, các điểm sau cần bạn chạy `./gradlew test` ở máy có JDK để xác nhận:

- `EnumDbValueTest` (22 enum: `SQL_TYPE` khớp `dbValue`, round-trip);
- `QuestionContentHasherTest` (6 test, gồm vector chuẩn);
- Spring context khởi động được với `ddl-auto=validate` (khuyến nghị đặt `JPA_DDL_AUTO=validate` sau khi đã chạy đủ DDL).

---

## 8. Giới hạn đã biết / việc tiếp theo

1. **Chưa có test tích hợp** cho `QuestionService` (cần MariaDB). Nên thêm Testcontainers MariaDB + Redis, hoặc chạy H2 không tương thích ENUM nên phải là MariaDB thật.
2. **Phân quyền theo vai**: mọi endpoint mới chỉ yêu cầu *đã đăng nhập*. UC03 (LECTURER chỉ thấy môn được phân công) cần bảng `ntt_admin_subjects` — chưa có trong CSDL v4 (rủi ro D9).
3. **Ứng dụng đề xuất**: hiện chỉ áp cho `stem`/`option_*` + `suggested_difficulty`. Đề xuất mức `question` phải sửa tay (đúng chủ trương "AI không đổi cấu trúc/đáp án").
4. **Nhập/xuất Moodle** mới có entity + repository, chưa có parser/serializer XML (đề cương yêu cầu round-trip 100 câu).
5. **Module thực nghiệm**: mới có entity; service tính P/R/F1 đa nhãn (micro/macro) chưa viết — và **đừng dùng** `vw_eval_confusion` vì FN luôn 0.
6. **`AiTask.createdBy`**: entity để nullable cho khớp DDL hiện tại; nếu chốt policy "task luôn có admin" (khuyến nghị trong tài liệu đối chiếu) thì phải `ALTER` cột trước rồi mới siết `nullable = false` — **đừng đổi annotation trước khi đổi DB**, nếu không `ddl-auto=validate` sẽ lệch.
7. **`spring-boot-starter-data-rest` đang bật**: Spring Data REST mặc định export MỌI repository
   public thành endpoint `/api/<tên-miền>` (kể cả `AdminRepository` có từ trước), chỉ được chặn bởi
   yêu cầu đăng nhập. 14 repository mới đã gắn `@RepositoryRestResource(exported = false)`;
   nên xử lý triệt để cho phần còn lại bằng `spring.data.rest.detection-strategy=annotated`
   (hoặc bỏ hẳn starter nếu không dùng) — nếu không, bề mặt tấn công lớn hơn API có kiểm soát.
8. **Job view của task huỷ/treo**: nếu worker chết giữa đường, task kẹt `running`; đã có `AiTaskRepository.findByStatusAndStartedAtBefore` để viết job quét (chưa bật scheduler).
