# Đối chiếu CSDL v4 “đã chốt” (SQL + Class diagram) với mã nguồn

- **Ngày**: 2026-11-22 · **Commit**: `8bc435e` · **Nhánh**: `arena/aa735601-project-thesis-ntt`
- **Đầu vào**: `CSDL_SQL_Diagram_v4.png` (9122×5454) + `CSDL_Class_Diagram_v4.png` (8504×4684) do bạn gửi, đối chiếu với:
  - DDL: `be-web-admin/sql/2026-11-19-01-auth-drift-fix.sql`, `-02-business-tables.sql`, `-03-eval-tables.sql`
  - Mã nguồn: `be-web-admin` (7 entity auth), `server-ai-worker` (3 entity AI), `qbank-console-vue3` (model JS), `app_mobile`
- **Không có trong sandbox**: MariaDB → không chạy được `SHOW CREATE TABLE`. Mọi kết luận dưới đây là so khớp tĩnh diagram ↔ DDL ↔ annotation.
- **Tài liệu liên quan**: `docs/review-latest-commit-2026-11-22.md` (rà mã nguồn), `docs/review-current-state.md` (rà CSDL v4 trước đó — một số điểm đã lỗi thời, xem §7).

---

## 0. Kết luận ngắn

1. **Thiết kế CSDL v4 là phần mạnh nhất của dự án.** Composite FK, cột sinh (`parent_key`, `active_flag`, `default_key`, `annotator_key`), CHECK constraint, grant theo cột, `version` optimistic lock, `content_hash`, trigger stale, và đặc biệt là cặp `split` + `annotator` + FK ghép chống rò rỉ DEV/TEST — đây là mức thiết kế hiếm thấy ở một đề án thạc sĩ và **nên dành 1–2 slide**.
2. Nhưng **“đã chốt” hiện đang là 3 nguồn sự thật lệch nhau**: diagram ≠ DDL ≠ entity. Cụ thể **12 điểm lệch** (§3) và **3 điểm trong đó sẽ làm hỏng lúc chạy** (`ddl-auto=validate` fail, INSERT fail, hoặc ghi sai enum).
3. **11/21 bảng chưa có code** (§2). Trong đó `ntt_questions`, `ntt_question_revisions` là **điều kiện tiên quyết** của gần như mọi UC còn lại.
4. Có **17 rủi ro thiết kế** (§5); **8 việc nên sửa trước khi viết code** (Phụ lục B). Nặng nhất là: mẫu eval trỏ `question_id` mà **không trỏ revision** (nhãn bất biến bị phá khi sửa câu hỏi), trigger stale **không đánh dấu đề xuất cũ** khi caller đặt `not_checked`, và **view `vw_eval_confusion` tính sai TP/FP/FN**.
5. Có **1 lỗ hổng tái lập**: 7 bảng auth **không có DDL** trong repo (script 01 chỉ `ALTER`), nên một máy mới **không thể dựng lại schema từ repo** — mâu thuẫn với yêu cầu “tái lập bằng Docker Compose” ở Chương 4.

---

## 1. Cách đọc 21 bảng

Diagram chia 5 nhóm theo miền nghiệp vụ; tôi giữ nguyên cách chia này vì nó khớp cách Chương 3.4 và 4.3 trình bày:

| Nhóm | Bảng | Có entity/service? |
|---|---|---|
| **A. Xác thực (7)** — “bảng cũ”, không ALTER cấu trúc | `ntt_admins`, `ntt_courses`, `ntt_backup_codes`, `ntt_auth_audit_logs`, `ntt_web_sessions`, `ntt_mobile_devices`, `ntt_totp_credentials` | ✅ 7/7 (`be-web-admin/model/jpa`) |
| **B. Ngân hàng câu hỏi (4)** | `ntt_subjects`, `ntt_topics`, `ntt_questions`, `ntt_question_revisions` | ❌ 0/4 |
| **C. AI (3)** | `ntt_ai_tasks`, `ntt_ai_suggestions`, `ntt_issue_types` | ⚠️ 3/3 nhưng **chỉ ở `server-ai-worker`**, Server 1 không có |
| **D. Moodle (3)** | `ntt_moodle_profiles`, `ntt_moodle_exchanges`, `ntt_moodle_exchange_items` | ❌ 0/3 |
| **E. Thực nghiệm (4)** — “tùy chọn” | `ntt_eval_runs`, `ntt_eval_items`, `ntt_eval_labels`, `ntt_eval_predictions` | ❌ 0/4 (worker dùng `ntt_demo_*` khác schema) |

Tổng: **10/21 bảng có code**, 11 bảng trống. Ngoài ra 3 bảng `ntt_chat_*` (migration 2026-11-18) **không nằm trong 21 bảng v4** — nếu vẫn giữ chat trong đề án, Chương 3.4 phải nói rõ đây là bảng ngoài phạm vi; nếu không, nên tách khỏi repo nộp.

---

## 2. Ma trận bảng ↔ code ↔ UC

| Bảng | UC liên quan | Entity cần tạo | Ghi chú implement |
|---|---|---|---|
| `ntt_admins` | UC01–03 | ✅ có | Thiếu API CRUD user (UC03) |
| `ntt_courses` | — | ✅ có | Chương trình đào tạo, **không phải môn học** — đừng map vào “Môn học” trên UI |
| `ntt_subjects` | UC04, UC05, UC06 | ❌ | UI qbank đang gọi biến `COURSES` → phải đổi thành `SUBJECTS` |
| `ntt_topics` | UC04 | ❌ | Cây; chống chu trình bằng CTE đệ quy ở service (DDL đã ghi chú) |
| `ntt_questions` | UC04, UC05, UC06, UC07 | ❌ | **Bảng trung tâm**; xem §5 D3–D6 |
| `ntt_question_revisions` | UC04, UC08 | ❌ | **Phải tạo rev 1 ngay khi tạo câu hỏi** (xem D3) |
| `ntt_ai_tasks` | UC07, UC09 | ⚠️ worker | Server 1 cần entity/projection riêng nếu chọn kiến trúc “Server 1 sở hữu task” |
| `ntt_ai_suggestions` | UC08 | ⚠️ worker | Quyết định duyệt thuộc Server 1 → **không được** để worker UPDATE cột `decision` (grant đã đúng) |
| `ntt_issue_types` | UC07, UC08, Ch4 | ⚠️ worker | 8 dòng seed nhưng parser chỉ cho 5 giá trị → xem D16 |
| `ntt_moodle_profiles` | UC12 | ❌ | |
| `ntt_moodle_exchanges` | UC10, UC11 | ❌ | Lưu `file_sha256`, `storage_ref` (không lưu nội dung tệp) |
| `ntt_moodle_exchange_items` | UC10, UC11 | ❌ | Thiếu hash nội dung từng câu (D12) |
| `ntt_eval_runs` | Ch4 E1–E8 | ❌ | worker không dùng bảng này → số liệu đang nằm ở `ntt_demo_eval_runs` |
| `ntt_eval_items` | Ch4 | ❌ | Xem D1, D2 (rủi ro cao nhất về học thuật) |
| `ntt_eval_labels` | Ch4 (kappa) | ❌ | Chưa có dữ liệu 2 annotator |
| `ntt_eval_predictions` | Ch4 (TP/FP/FN) | ❌ | Xem D7 (view sai) |

---

## 3. Lệch diagram ↔ DDL (12 điểm)

Đây là phần cần bạn **quyết định dứt điểm**: diagram, DDL hay entity là chuẩn? Khuyến nghị của tôi: **DDL là chuẩn** (vì nó chạy được), sửa diagram theo, **trừ B1 và B2 cần sửa cả DDL** (bên dưới có lý do).

| # | Điểm | Diagram | DDL | Mức | Xử lý đề xuất |
|---|---|---|---|---|---|
| **B1** | `created_by` / `changed_by` **NOT NULL** | `N` ở 6 cột: `ntt_subjects.created_by`, `ntt_questions.created_by` (nhưng `updated_by` = `Y` — khớp DDL), `ntt_question_revisions.changed_by`, `ntt_ai_tasks.created_by`, `ntt_moodle_profiles.created_by`, `ntt_moodle_exchanges.created_by`, `ntt_eval_runs.created_by` | Tất cả **`BIGINT NULL`** | **P1** | Cần một **policy chung** thay vì sửa lẻ — xem bảng dưới |
| **B2** | Enum của 7 bảng auth | `role enum('admin','lecturer')`, `account_status enum('active',…)` (chữ thường) | File 01 đã đổi sang **`ENUM('ADMIN','LECTURER')`**, `('PENDING','ACTIVE','LOCKED','DISABLED')` | **P1** | Giữ chữ HOA (khớp `Role`, `AccountStatus`); **sửa diagram**. Đây là điểm diagram sai so với DB thật |
| **B3** | `ntt_auth_audit_logs.event_type` | `enum(19 giá trị)` | `VARCHAR(40) NULL` (file 01 + migration 11-14) | P2 | Sửa diagram; 40 ký tự để thêm sự kiện mới. `AuthEvent` hiện có đúng 19 giá trị → khớp số nhưng kiểu khác |
| **B4** | `ntt_issue_types` | **3 cột** (`issue_type`, `label_vi`, `is_evaluated`) + chú thích “6 dòng (seed)” | **4 cột** (thêm `sort_order INT NOT NULL DEFAULT 0`) + **8 dòng** seed | P1 | Sửa diagram (cả class diagram thiếu `sortOrder`, trong khi entity `IssueType` **đã có**). 8 dòng = 6 dòng chấm điểm + `key_doubt`, `none` (xem D16) |
| **B5** | `ntt_totp_credentials.iv` | `UQ` | Script 01 **định bỏ** nhưng câu `DROP INDEX iv` đang **comment** | P2 | Chốt: **bỏ UQ** (12 byte ngẫu nhiên, trùng IV không phải lỗi bảo mật ở đây vì mỗi bản ghi có salt riêng). Chạy ALTER, cập nhật diagram |
| **B6** | `ntt_moodle_exchange_items.question_revision_id` | `Y` | `BIGINT NULL` | ✅ khớp | Giữ NULL (item `skipped` không có revision) |
| **B7** | `ntt_eval_items` khoá `(dataset_version, question_id)` | Có | `uq_eval_dataset_question` có | ✅ khớp | Tốt: chặn 1 câu xuất hiện 2 lần trong cùng dataset |
| **B8** | `ntt_eval_runs.method` | `N` + CHK `method='llm' ⇒ model/runtime/gpu NOT NULL` | `NOT NULL` + cùng CHECK | ✅ khớp | — |
| **B9** | `ntt_ai_tasks.active_flag` | `GEN` | Cột sinh STORED, entity `insertable=false` | ✅ khớp | — |
| **B10** | `ntt_questions.content_hash` / `version` / `status` / `validation_status` | Có | Có | ✅ khớp | `content_hash` chỉ INDEX (không UNIQUE) đúng như chú thích trong DDL |
| **B11** | `ntt_ai_suggestions.target_field` | 6 giá trị, **không có** `correct_label` | 6 giá trị | ✅ khớp | Điểm rất mạnh: AI không thể đề xuất đổi đáp án đúng ở tầng dữ liệu |
| **B12** | `ntt_courses.course_id` | `bigint` (không `AI`) | Không AUTO_INCREMENT (app cấp mã) | ✅ khớp | Nhưng `DataInitializer` phải seed id ổn định |

### B1 — Policy cho cột audit (cần chốt rồi sửa cả 2 phía)

| Bảng.cột | Nên | Lý do |
|---|---|---|
| `ntt_ai_tasks.created_by` | **NOT NULL** | Mọi task đều do 1 admin khởi tạo (kể cả burst demo). Entity `AiTask` hiện `nullable=false` → **đang lệch DDL**, `ddl-auto=update` sẽ ALTER cột và có thể fail nếu có dòng NULL |
| `ntt_eval_runs.created_by` | **NULL** | Run có thể chạy bằng script/notebook, không có admin |
| `ntt_questions.created_by` | **NULL** | Nhập từ Moodle/legacy, job hệ thống (`updated_by` diagram đã là `Y`, khớp DDL) |
| `ntt_subjects.created_by` | **NULL** | Seed/import |
| `ntt_question_revisions.changed_by` | **NULL** | Restore/import hệ thống |
| `ntt_moodle_profiles/exchanges.created_by` | **NULL** | Job nhập/xuất có thể chạy nền |

→ Hành động: **1 câu ALTER** cho `ntt_ai_tasks.created_by NOT NULL`; **sửa diagram** 5 bảng còn lại thành `Y`.

---

## 4. Lệch entity ↔ DDL (sẽ nổ khi bật `validate` hoặc khi INSERT)

| # | Entity | Annotation | DDL | Hệ quả |
|---|---|---|---|---|
| **C1** | `AiTask.createdBy` | `nullable = false` | `created_by BIGINT NULL` | `ddl-auto=update` ALTER thành NOT NULL (có thể fail); `validate` có thể báo; chèn task không có admin → lỗi |
| **C2** | `IssueType.sortOrder` | có, `nullable=false` | có | ✅ (diagram thiếu, entity đúng) |
| **C3** | `Admin.role/accountStatus` | `@Enumerated(STRING)` → `ADMIN`, `ACTIVE` | `ENUM('ADMIN',…)` | ✅ sau drift-fix 01; **nhưng** diagram vẫn ghi chữ thường → người đọc diagram sẽ code sai |
| **C4** | `WebSession.status`, `MobileDevice.status/platform`, `AuthAuditLog.eventType` | `@Enumerated(STRING)` → `ACTIVE`, `ANDROID`, `LOGIN_OK` | 7 bảng cũ không có DDL trong repo; DB thật do Hibernate sinh → nhiều khả năng cũng CHỮ HOA | Diagram ghi chữ thường → **diagram sai**; cần dump schema thật để chốt (xem D14) |
| **C5** | Các entity mới (Question…) chưa có | — | 14 bảng mới dùng **chữ thường** | Bắt buộc dùng `AttributeConverter` (worker đã làm mẫu: `AiTaskStatusConverter`) — nếu dùng `@Enumerated(STRING)` trực tiếp sẽ ghi `CLEAN` vào enum `('clean')`; **với `utf8mb4_unicode_ci` so sánh không phân biệt hoa/thường nên có thể “chạy được”**, nhưng đây là may mắn, không phải thiết kế — và sẽ vỡ nếu ai đó đổi collation hoặc so sánh nhị phân |
| **C6** | `AiSuggestion.suggestionId` | `@GeneratedValue(IDENTITY)` + `columnDefinition="bigint"` | `BIGINT AUTO_INCREMENT` | ✅ |
| **C7** | `AiTask` không có `@Version` | — | `ntt_ai_tasks` không có `version` | ✅ (không cần) |
| **C8** | `Question` (sẽ tạo) | — | `version INT NOT NULL DEFAULT 0` | Phải thêm `@Version` + `If-Match`/409 ở API |
| **C9** | `DemoQuestion` (worker) | bảng `ntt_demo_questions` | **không thuộc 21 bảng** | Số liệu Chương 4 đang nằm ngoài schema chính thức |
| **C10** | `IssueType` ở worker là entity duy nhất cho bảng này | — | Server 1 sẽ cần đọc `ntt_issue_types` | Tránh map JPA trùng ở 2 app; dùng projection/JdbcTemplate hoặc module dùng chung |

---

## 5. Rủi ro thiết kế (chưa phải “lệch”, mà là sẽ cắn khi implement)

### D1 — `ntt_eval_items` trỏ `question_id` mà **không trỏ revision** ⚠️ nặng nhất
Nhãn `gold` và dự đoán gắn với `item_id` → `question_id`. Nếu sau khi gán nhãn, câu hỏi bị sửa (kể cả sửa chính tả), `ntt_question_revisions` có thêm bản ghi nhưng **eval item vẫn trỏ câu hỏi “hiện tại”** → mọi chỉ số Chương 4 đo trên nội dung khác với nội dung đã gán nhãn, **không thể tái lập**.
**Sửa**: thêm `question_revision_id BIGINT NOT NULL` + FK ghép `(question_id, question_revision_id) → ntt_question_revisions`, hoặc `snapshot_json JSON NOT NULL` (khuyến nghị: cả hai — revision để truy vết, snapshot để bất biến kể cả khi xoá revision).

### D2 — `ntt_eval_items.question_id NOT NULL` buộc dataset phải nằm trong ngân hàng thật
Muốn chạy E1–E8 phải insert 300–1000 câu eval vào `ntt_questions` (`source='eval'`). Hệ quả: dashboard “Tổng số câu hỏi” bị đội lên, tìm kiếm của giảng viên bị nhiễu, và tập TEST “chỉ chạy một lần” lại sống trong cùng bảng nghiệp vụ.
**Sửa (chọn 1)**:
- (a) cho `question_id` NULL + `snapshot_json` (eval độc lập hoàn toàn với ngân hàng); hoặc
- (b) thêm `ntt_eval_datasets` (dataset_version, split, item content) và bỏ FK sang `ntt_questions`;
- (c) chấp nhận (a/b) không được thì **phải** lọc `source <> 'eval'` ở mọi màn hình và ghi rõ trong luận văn.

### D3 — Task `question_check` bắt buộc `question_revision_id NOT NULL`, nhưng luồng tạo câu hỏi chưa có revision
`chk_task_question_check` yêu cầu `question_id + question_revision_id` cùng NOT NULL. Nếu `POST /questions` không tạo revision 1 trong **cùng transaction**, thì **mọi yêu cầu kiểm định AI sẽ fail FK** — đúng lỗi sẽ xuất hiện ngay ở UC07 lúc demo.
**Sửa**: `create()` phải ghi `ntt_questions` + `ntt_question_revisions(rev_no=1, change_source='create', snapshot_json)` rồi mới trả về; thêm test tích hợp “tạo câu → tạo task” trong 1 test.

### D4 — `content_hash` NOT NULL nhưng **chưa có đặc tả chuẩn hóa**
Không có code nào tính. Nếu hai nơi (Server 1 export, Server 1 import, worker eval) tính khác nhau thì chống trùng vô hiệu. Cần spec + test vector:
```
h = SHA-256(
      NFC(trim(stem)) + U+0001 +
      NFC(trim(option_a)) + U+0001 + … + option_d + U+0001 +
      correct_label + U+0001 + content_format
)
```
Quy tắc: NFC (không NFD), trim, gộp mọi khoảng trắng liên tiếp thành 1, giữ nguyên hoa/thường (chỉ lower-case nếu muốn chống trùng mạnh tay), **giữ thứ tự đáp án A–D**, bỏ tag HTML nếu `content_format='html'`? — phải ghi rõ. Khuyến nghị thêm cột `hash_algo VARCHAR(10) DEFAULT 'sha256-v1'` để sau này đổi công thức không phải migrate dữ liệu.

### D5 — Thiếu khoá lạc quan ở `ntt_subjects` / `ntt_topics`
`ntt_questions` có `version`; 2 bảng còn lại không. Hai admin sửa cùng 1 chủ đề → last-write-wins. Với UC04 (nhiều giảng viên) nên thêm `version INT` cho cả hai, hoặc chấp nhận và ghi rõ.

### D6 — Trigger stale **không** đánh dấu đề xuất cũ khi caller đặt `not_checked`/`clean` ⚠️ logic
`trg_question_au_stale_suggestions` chỉ chạy khi `NEW.validation_status='stale' AND OLD<>'stale'`. Nhưng `trg_question_bu_stale` **tôn trọng ý caller**: nếu service sửa nội dung và đồng thời set `not_checked` (điều rất tự nhiên khi giảng viên sửa tay sau khi AI chạy), thì:
- `validation_status` = `not_checked` (không phải `stale`) → trigger thứ 2 **không chạy**;
- các `ntt_ai_suggestions` **pending** của nội dung cũ **vẫn pending** → UI vẫn hiện “đề xuất chờ duyệt” cho câu đã đổi → giảng viên có thể bấm “Chấp nhận” và **áp nội dung cũ đè lên nội dung mới**.

Đây là lỗi nghiệp vụ thật, không phải chuyện tài liệu. **Sửa**: đổi trigger 2 thành điều kiện “nội dung đổi” (dùng cùng biểu thức so sánh với trigger 1), không phụ thuộc `validation_status`:
```sql
IF (NOT (NEW.stem <=> OLD.stem) OR … OR NOT (NEW.correct_label <=> OLD.correct_label)) THEN
   UPDATE ntt_ai_suggestions s JOIN ntt_ai_tasks t ON t.task_id=s.task_id
   SET s.decision='stale' WHERE t.question_id=NEW.question_id AND s.decision='pending';
END IF;
```
Đồng thời ở service: khi `accept`, kiểm tra `suggestion.task_id` thuộc revision hiện tại (nếu không → 409 `STALE_SUGGESTION`).

### D7 — View `vw_eval_confusion` tính sai
```sql
FROM ntt_eval_runs r CROSS JOIN ntt_issue_types it
LEFT JOIN ntt_eval_predictions p ON p.run_id=r.run_id AND p.issue_type=it.issue_type
LEFT JOIN ntt_eval_labels l ON l.item_id=p.item_id AND l.issue_type=it.issue_type AND l.stage='gold'
```
Ba lỗi:
1. **`FN luôn = 0`**: `l` được join qua `p.item_id`, nên item “có nhãn vàng nhưng không có dự đoán” không bao giờ sinh dòng ⇒ recall/F1 của mọi nhóm lỗi đều bị thổi lên 1.0. Đây là lỗi nghiêm trọng nhất vì Chương 4 dùng chính view này để báo cáo.
2. **`TP` chỉ đếm trên item có dự đoán**, không đối chiếu với tập item của run ⇒ mẫu số (support) không xác định, không thể tính precision/recall theo tổng số item.
3. **Không tính `macro-F1` / `micro-F1`** — view chỉ trả 2 cột đếm, trong khi đề cương cam kết báo cáo F1 cho từng loại lỗi.

Lưu ý: phần `split` **không** bị trộn — các FK ghép `fk_pred_item (split,item_id)` và `fk_pred_run (run_id,split)` đã chặn ở tầng dữ liệu. Đây là điểm thiết kế tốt, chỉ có phần tính toán là sai.

**Sửa**: bỏ view, tính trong service (nhưng `DemoEvalService.metrics` hiện dùng accuracy đơn nhãn — phải viết lại theo set đa nhãn), hoặc dùng view đúng ở Phụ lục A (thêm “universe” item × issue_type của từng run).

### D8 — Không có ràng buộc “đã duyệt ⇒ đã áp dụng”
`ntt_ai_suggestions` có CHECK cho `decided_by/decided_at`, nhưng **không** có:
```sql
decision <> 'accepted' OR applied_revision_id IS NOT NULL
```
⇒ có thể ghi `decision='accepted'` mà không tạo revision, tức “duyệt” nhưng nội dung không đổi — trái với đóng góp #1 của đề án (“chỉ khi duyệt mới đổi dữ liệu và có lưu vết”). **Thêm CHECK** (và test).

### D9 — Thiếu bảng phân công giảng viên ↔ môn (UC03, phạm vi dữ liệu)
Diagram xác nhận **không có** bảng nào nối `admin` với `subject`; `ntt_admins.course_id` chỉ trỏ **chương trình đào tạo**. Trong khi UI mẫu (`UI_Accept_v9`) có `user.subjects[]` và hàm `subjOk()` lọc dữ liệu theo môn. ⇒ UC03 và yêu cầu “giảng viên chỉ thấy môn được phân công” **không thực hiện được** với schema hiện tại.
**Sửa**: thêm
```sql
CREATE TABLE ntt_admin_subjects (
  admin_id BIGINT NOT NULL, subject_id BIGINT NOT NULL,
  assigned_by BIGINT NULL, assigned_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (admin_id, subject_id),
  CONSTRAINT fk_as_admin FOREIGN KEY (admin_id) REFERENCES ntt_admins(admin_id),
  CONSTRAINT fk_as_subject FOREIGN KEY (subject_id) REFERENCES ntt_subjects(subject_id)
);
```
Hoặc nếu không kịp: **bỏ hẳn** phân quyền theo môn (chỉ ADMIN/LECTURER toàn quyền) và sửa lại Chương 3.2 cho khớp.

### D10 — Index chưa tối ưu cho màn danh sách & dashboard
Hiện có `ix_q_scope (subject_id, topic_id, status)`, `ix_q_validation (subject_id, validation_status)`. Truy vấn thực tế của qbank: lọc theo môn + chương + trạng thái kiểm định + **sắp xếp `updated_at DESC`** + phân trang. Thiếu `(subject_id, validation_status, updated_at)` và `(subject_id, difficulty)`.
Ngoài ra: `FULLTEXT ft_q_text` với `innodb_ft_min_token_size=3` sẽ **bỏ qua từ 1–2 ký tự** (“AI”, “CS”, “DB”) — đúng loại từ khoá sinh viên hay tìm. Cần ghi vào tài liệu triển khai: đặt `innodb_ft_min_token_size=2` + `innodb_ft_enable_stopword=0` (hoặc dùng `LIKE` prefix cho từ ngắn).

### D11 — `updated_at` không có cơ chế cập nhật
Comment DDL nói “ứng dụng ghi tường minh”. Nhưng nếu quên, `updated_at` đứng yên → UI sort “Mới nhất” và cột `last_validated_at` sai. **Sửa**: `@PreUpdate`/`@PrePersist` ở entity base, hoặc thêm trigger set `updated_at = UTC_TIMESTAMP(6)` (nhưng xem D13).

### D12 — `ntt_moodle_exchange_items` không lưu hash nội dung từng câu
Chương 4.2 hứa “xuất rồi nhập lại, 100% khớp nội dung gốc”. Với schema hiện tại chỉ có `source_name` + `item_status` ⇒ không chứng minh được “khớp”, chỉ tin vào mắt người kiểm tra.
**Sửa**: thêm `content_hash CHAR(64) NULL` (hash của câu tại thời điểm xuất/nhập) → báo cáo round-trip tự động: `100/100 hash trùng`.

### D13 — ⚠️ Trộn múi giờ: `DEFAULT CURRENT_TIMESTAMP(6)` vs Hibernate ghi UTC
14 bảng mới dùng `DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)`. `CURRENT_TIMESTAMP` lấy **múi giờ của server MariaDB** (`system_time_zone`), trong khi `hibernate.jdbc.time_zone=UTC` khiến mọi giá trị do app ghi là **UTC**. Trên máy dev ở Việt Nam (UTC+7) sẽ có 2 loại dấu thời gian trong cùng một cột, lệch 7 giờ — và `last_validated_at`/`updated_at` sẽ không nhất quán.
**Sửa**: `SET GLOBAL time_zone='+00:00'` (và trong my.cnf / compose), hoặc bỏ DEFAULT và để app luôn ghi. Khuyến nghị: cả hai.

### D14 — 7 bảng auth không có DDL ⇒ không tái lập được
File 01 chỉ `ALTER TABLE` (giả định bảng đã tồn tại do `ddl-auto=update`). Không có `CREATE TABLE ntt_admins…` ở bất kỳ đâu (`grep` toàn repo = 0). ⇒ máy mới chạy 01/02/03 sẽ **lỗi ngay**; `compose.yaml` của `be-web-admin` cũng đang bị comment 100%.
**Sửa**: `mariadb-dump --no-data ntt_thesis > sql/00-baseline-auth.sql` (đã ẩn dữ liệu), commit file này, và đổi thứ tự chạy 00 → 01 → 02 → 03. Đây là điều kiện để Chương 4 nói “tái lập bằng Docker Compose” là đúng.

### D15 — Trigger chỉ là “lưới an toàn”, không phải phân quyền
Trigger stale so sánh nội dung, **không** chặn caller ghi `validation_status='clean'` cùng lúc với sửa nội dung (đã ghi chú “tôn trọng ý caller”). Trong khi Chương 3.3 nói “AI không được tự đổi trạng thái chính thức”. ⇒ phải enforce ở service + test; **và** cân nhắc bỏ quyền UPDATE `validation_status` khỏi user worker (grant hiện tại cho `ntt_s2` UPDATE `result_json`… không cho `validation_status` — **đúng**; chỉ Server 1 được ghi).

### D16 — Ba danh mục `issue_type` **không giao nhau** ⚠️ làm sai cả Chương 4
Đây là điểm tôi đã đếm lại trực tiếp trên seed và parser:

| `issue_type` | DB `is_evaluated` | `AiResultParser` cho phép | Hệ quả |
|---|---|---|---|
| `spelling` | 1 | ✅ | bình thường |
| `grammar` | 1 | ✅ | bình thường |
| `logic` | 1 | ✅ | bình thường |
| `distractor` | 1 | ✅ | bình thường |
| **`presentation`** | **1** | ❌ | **Không bao giờ được sinh ra.** Nếu có nhãn vàng → **FN vĩnh viễn**, recall nhóm này = 0 |
| **`style`** | **1** | ❌ | như trên |
| `key_doubt` | 0 | ✅ | Sinh ra được nhưng không được chấm → dữ liệu rác trong bảng `predictions` |
| `none` | 0 | ❌ | Chỉ dùng làm nhãn vàng “không lỗi” |

⇒ Nếu Chương 4 báo “4 nhóm lỗi”, cần chốt **đúng 4–5 giá trị** và sửa **cả ba nơi** (seed DB, `AiResultParser.ALLOWED_ISSUE_TYPES`, prompt `AiPromptFactory`, và nhãn hiển thị ở qbank). Nếu để nguyên, chỉ số F1 của `presentation`/`style` sẽ bằng 0 và người phản biện sẽ hỏi ngay “vì sao nhóm này không bao giờ phát hiện được?”.
**Sửa nhanh (30 phút)**: chọn 5 giá trị chấm điểm `spelling, grammar, logic, distractor, presentation`; xoá `style` hoặc gộp vào `presentation`; đặt `key_doubt` = 1 nếu muốn đo (và thêm vào prompt), ngược lại bỏ khỏi whitelist.

### D17 — Ngữ nghĩa `validation_status='failed'` chưa định nghĩa
Có `failed` nhưng không rõ: “AI chạy lỗi” hay “câu hỏi không đạt kiểm định”? Với `has_issues` đã là “có lỗi”, `failed` nhiều khả năng là “task AI thất bại”. Cần ghi rõ trong tài liệu + mapping UI (`failed` chưa có trong qbank).

---

## 6. Thứ tự implement (đồng bộ với §2)

> Phần B1–B4 (entity + repository + service + Redis cho giao diện) đã được hiện thực ở commit này:
> xem `docs/api-qbank-redis.md` (ánh xạ 3 tầng, 19 endpoint, 6 khoá Redis, cách kiểm chứng).

```
B1. ntt_subjects + ntt_topics        (CRUD + cây, không phụ thuộc bảng nào)
B2. ntt_questions + revision rev1    (validate 4 đáp án, content_hash, version)
B3. Logging: revision cho mọi update, archive thay delete
B4. ntt_ai_tasks (Server 1 tạo) ──► ntt_ai_suggestions (worker ghi) ──► duyệt (Server 1)
B5. ntt_moodle_profiles → exchanges → exchange_items (export trước, import sau)
B6. ntt_eval_* (sau khi B2 ổn định, để dataset trỏ revision)
```

Song song: `ntt_admin_subjects` (D9) trước khi làm UC03; `00-baseline-auth.sql` (D14) trước khi ai đó cài máy mới.

**Đặt tên class** theo class diagram (đây là điểm class diagram làm tốt): `Subject`, `Topic`, `Question`, `QuestionRevision`, `IssueType`, `AiTask`, `AiSuggestion`, `MoodleProfile`, `MoodleExchange`, `MoodleExchangeItem`, `EvalRun`, `EvalItem`, `EvalLabel`, `EvalPrediction`. Enum kèm hậu tố `…D` như diagram (`OptionLabelD`, `ContentFormatD`, `ValidationStatusD`, `QuestionStatusD`) — **và phải có `AttributeConverter`** sang chữ thường (C5).

---

## 7. Vì sao diagram và DDL lệch? (và cách chấm dứt)

Diagram được vẽ từ **ý tưởng thiết kế**, DDL được viết sau khi đối chiếu code (file 01 ghi rõ: “đưa schema thực tế về đúng ER v4, đồng thời vá vài điểm lệch phát hiện khi đối chiếu code Java”). Vì vậy DDL **mới hơn** ở các điểm B1–B5, còn diagram mới hơn ở… không đâu cả. Kết luận: **DDL là chuẩn**, diagram cần cập nhật 12 điểm ở §3.

Để không tái diễn, chọn 1 trong 2 (khuyến nghị làm cả hai, rất rẻ):
1. **Flyway/Liquibase**: mỗi thay đổi là 1 migration có version; `spring.jpa.hibernate.ddl-auto=validate`; CI chạy migration trên MariaDB rỗng rồi `validate`.
2. **Sinh diagram từ DB**: `schemaspy` hoặc `mariadb-dump --no-data` + `dbdocs`/`drawio import`. Diagram trở thành **ảnh chụp** của DB, không còn là nguồn sự thật thứ hai — đúng chuẩn luận văn (“hình 3.4 được sinh từ schema ngày …”).

Ngoài ra: hiện tài liệu cũ (`docs/review-current-state.md`) có một số kết luận **đã lỗi thời** so với commit này (ví dụ `created_by` được mô tả theo drawio cũ, chưa cập nhật việc file 01 đã đổi enum sang chữ hoa). Nên đánh dấu tài liệu đó là “snapshot 21-11” để tránh trích dẫn nhầm khi viết luận văn.

---

## Phụ lục A — View tính TP/FP/FN đúng (nếu muốn giữ ở DB)

```sql
CREATE OR REPLACE VIEW vw_eval_confusion AS
WITH universe AS (              -- mọi (run, item) thuộc đúng dataset_version + split của run
    SELECT r.run_id, i.item_id
    FROM ntt_eval_runs r
    JOIN ntt_eval_items i
      ON i.dataset_version = r.dataset_version
     AND i.split           = r.split
),
gold AS (                       -- nhãn chốt (stage = gold) theo item + nhóm lỗi
    SELECT l.item_id, l.issue_type
    FROM ntt_eval_labels l
    WHERE l.stage = 'gold'
),
pred AS (
    SELECT p.run_id, p.item_id, p.issue_type
    FROM ntt_eval_predictions p
)
SELECT r.run_id, r.run_label, r.split, it.issue_type,
       SUM(g.item_id IS NOT NULL AND p.item_id IS NOT NULL) AS tp,
       SUM(g.item_id IS NULL     AND p.item_id IS NOT NULL) AS fp,
       SUM(g.item_id IS NOT NULL AND p.item_id IS NULL)     AS fn
FROM ntt_eval_runs r
JOIN ntt_issue_types it ON it.is_evaluated = b'1'
JOIN universe u         ON u.run_id = r.run_id
LEFT JOIN gold g        ON g.item_id = u.item_id AND g.issue_type = it.issue_type
LEFT JOIN pred p        ON p.run_id = r.run_id AND p.item_id = u.item_id AND p.issue_type = it.issue_type
GROUP BY r.run_id, r.run_label, r.split, it.issue_type;
```
Hiệu năng: `items × issue_types` cho mỗi run (1000 × 6 = 6.000 dòng/run) — chấp nhận được cho báo cáo, nhưng **khuyến nghị tính trong service** để còn xử lý `error_code`, latency, và macro/micro-F1 (Micro-F1 đa nhãn = `2·ΣTP / (2·ΣTP + ΣFP + ΣFN)`, không phải accuracy như code demo đang dùng).

## Phụ lục B — Checklist chốt CSDL (làm được trong 1 buổi)

- [ ] `ALTER TABLE ntt_ai_tasks MODIFY created_by BIGINT NOT NULL;` (B1, C1)
- [ ] Sửa diagram: 5 bảng audit còn lại → `Y`
- [ ] Sửa diagram: `role`/`account_status` → CHỮ HOA; `event_type` → `VARCHAR(40)`; `issue_types` thêm `sort_order` + 8 dòng (B2–B4)
- [ ] `ALTER TABLE ntt_totp_credentials DROP INDEX iv;` (B5) — câu lệnh đang comment trong `sql/01:63`, kiểm tra tên index trước
- [ ] Chốt danh mục `issue_type`: DB ↔ parser ↔ prompt ↔ UI (D16)
- [ ] Thêm `ntt_admin_subjects` (D9) hoặc bỏ phân quyền theo môn
- [ ] Thêm `question_revision_id`/`snapshot_json` vào `ntt_eval_items` (D1) và quyết định NULL-able `question_id` (D2)
- [ ] Viết lại trigger `trg_question_au_stale_suggestions` theo “nội dung đổi” (D6)
- [ ] Thêm CHECK `accepted ⇒ applied_revision_id NOT NULL` (D8)
- [ ] Bỏ/correct view `vw_eval_confusion` (D7)
- [ ] `mariadb-dump --no-data` 7 bảng auth → `sql/00-baseline-auth.sql` (D14)
- [ ] Đặt `time_zone='+00:00'` + xem lại các `DEFAULT CURRENT_TIMESTAMP(6)` (D13)
- [ ] Bật `ddl-auto=validate` và chạy thử migration trên DB rỗng (D14)

## Phụ lục C — Bảng đối chiếu nhanh 21 bảng

| # | Bảng | Nhóm | Code | Ghi chú chốt |
|---|---|---|---|---|
| 1 | ntt_admins | Auth | ✅ | role/account_status CHỮ HOA (B2) |
| 2 | ntt_courses | Auth | ✅ | chương trình đào tạo — không phải “môn học” |
| 3 | ntt_backup_codes | Auth | ✅ | chưa có luồng dùng backup code |
| 4 | ntt_auth_audit_logs | Auth | ✅ | event_type VARCHAR(40) (B3) |
| 5 | ntt_web_sessions | Auth | ✅ | enum status CHỮ HOA |
| 6 | ntt_mobile_devices | Auth | ✅ | platform CHỮ HOA |
| 7 | ntt_totp_credentials | Auth | ✅ | iv UNIQUE cần bỏ (B5) |
| 8 | ntt_subjects | QBank | ❌ | created_by NULL (B1) |
| 9 | ntt_topics | QBank | ❌ | parent_key GEN; chống chu trình ở service |
| 10 | ntt_questions | QBank | ❌ | trung tâm; version/content_hash/trigger |
| 11 | ntt_question_revisions | QBank | ❌ | rev 1 bắt buộc khi create (D3) |
| 12 | ntt_issue_types | AI | ⚠️ | 8 dòng, sort_order |
| 13 | ntt_ai_tasks | AI | ⚠️ | active_flag GEN, created_by NOT NULL |
| 14 | ntt_ai_suggestions | AI | ⚠️ | target_field không có correct_label |
| 15 | ntt_moodle_profiles | Moodle | ❌ | default_key GEN bảo đảm 1 default |
| 16 | ntt_moodle_exchanges | Moodle | ❌ | chk finished_at |
| 17 | ntt_moodle_exchange_items | Moodle | ❌ | thiếu content_hash (D12) |
| 18 | ntt_eval_runs | Eval | ❌ | scenario E1–E8, split |
| 19 | ntt_eval_items | Eval | ❌ | cần revision/snapshot (D1, D2) |
| 20 | ntt_eval_labels | Eval | ❌ | annotator + gold, annotator_key GEN |
| 21 | ntt_eval_predictions | Eval | ❌ | FK ghép split chống rò rỉ |
