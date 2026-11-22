# Rà soát hiện trạng hệ thống — project_thesis_ntt

- **Ngày rà soát**: 2026-11-21
- **Kỳ nhánh**: `arena/f960d1ff-project-thesis-ntt` (branched từ `22616a80c4189bcad2ee7011f1c775a79623be90` trên `feature/apply-ui-project-thesis`)
- **Phạm vi**: rà soát static/code + kiểm tra từng số liệu trong báo cáo demo — không sửa code
- **Không chạy được server/demo trực tiếp** (sandbox thiếu JVM, MariaDB, Redis) — toàn bộ phần "kiểm tra API demo" trong tài liệu này là phân tích tĩnh: code path, endpoint, dữ liệu đầu vào, phép tính chỉ số

---

## 1. Bức tranh hiện trạng

Đề cương (đề tài) mục 3.1 cho kiến trúc: Vue.js + Flutter → **Server 1** (Spring Boot, nghiệp vụ + xác thực + 2FA) → **MariaDB** (bền vững) + **Redis** (cache/queue) → **Server 2** (AI worker) → Colab/Ollama (Qwen2.5-14B-Instruct Q4_K_M). Câu hỏi: A/B/C/D, 1 đáp án đúng, Moodle XML trao đổi.

| Thành phần | Trạng thái | Ghi chú |
|---|---|---|
| be-web-admin (Server 1) | Sơ khai | Chỉ xác thực/TOTP/chat/course + SQL migration v4; **chưa có entity/service/REST cho 18 bảng nghiệp vụ mới** |
| server-ai-worker (Server 2) | Đã hoàn chỉnh nhưng **demo** | Map ER v4 + pipeline AI đầy đủ (few_cot, preflight, parser chống ảo giác); **độc lập, chưa ghép Server 1**; chạy trên `ntt_demo_llm` |
| fe_web_admin (Vue.js) | Sơ khai | 3 trang: login, information (2FA cards), 404; route: `/`, `/login`, `/information-student`, `/information-admin`, `/404-not-found` |
| app_mobile (Flutter) | Có | TOTP (2FA) |
| CSDL v4 (MariaDB) | **Chốt** | 7 bảng auth + 14 bảng mới (10 nghiệp vụ + 4 thực nghiệm) + 2 trigger + grants. **Chưa có Redis** (như bạn nói) |
| Moodle XML (nhập/xuất) | **Chưa có code** | Chỉ có SQL (`ntt_moodle_profiles`, `ntt_moodle_exchanges`, `ntt_moodle_exchange_items`) |
| Báo cáo thực tập demo | — | Ảnh chụp (`Báo cáo.png`) lấy từ trang `demo-eval.html` — **không phải số liệu chính thức cho Chương 4.3** |

---

## 2. CSDL đã chốt (v4) — 21 bảng

### 2.1 Tổng quan

| Nhóm | Bảng | Số lượng |
|---|---|---|
| Auth (giữ nguyên) | ntt_admins, ntt_courses, ntt_backup_codes, ntt_web_sessions, ntt_mobile_devices, ntt_totp_credentials, ntt_auth_audit_logs | 7 |
| Nghiệp vụ mới | ntt_subjects, ntt_topics, ntt_issue_types, ntt_questions, ntt_question_revisions, ntt_ai_tasks, ntt_ai_suggestions, ntt_moodle_profiles, ntt_moodle_exchanges, ntt_moodle_exchange_items | 10 |
| Thực nghiệm (E1–E8) | ntt_eval_runs, ntt_eval_items, ntt_eval_labels, ntt_eval_predictions | 4 |

File: `be-web-admin/sql/2026-11-19-02-business-tables.sql` (14 bảng nghiệp vụ) + `2026-11-19-03-eval-tables.sql` (4 bảng thực nghiệm) + `2026-11-19-04-grants.sql` (quyền 2 server) + `2026-11-19-01-auth-drift-fix.sql` (7 bảng cũ).

### 2.2 Điểm mạnh (đã đối chiếu với toàn bộ file 02/03)

- **FK ghép** chống sai lệch: `ntt_topics (subject_id, parent_id)` → `ntt_topics (subject_id, topic_id)`; `ntt_questions (subject_id, topic_id)` → `ntt_topics (subject_id, topic_id)`; `ntt_ai_tasks (question_id, question_revision_id)` → `ntt_question_revisions`; `ntt_eval_predictions (run_id, split)` → `ntt_eval_runs (run_id, split)` và `(split, item_id)` → `ntt_eval_items (split, item_id)`.
- **Cột sinh**: `parent_key`, `active_flag`, `default_key`, `annotator_key` — app không ghi, DB tính.
- **Unique chặn task sống**: `uq_one_active_task (question_id, task_type, active_flag)` — cho phép chạy lại bao nhiêu lần cũng được, nhưng chặn 2 task đang sống cho cùng câu + loại.
- **Không được sửa đáp án đúng bởi AI**: `target_field` không có lựa chọn nào như `correct_label` (enforce ở tầng ENUM của DB, không chỉ service).
- **Trigger**: `trg_question_bu_stale` (content đổi, `validation_status` không đổi → set `stale`) + `trg_question_au_stale_suggestions` (đánh dấu mọi đề xuất `pending` của câu đó thành `stale`). Tôn trọng ý caller set clean/not_checked tường minh.
- **Grants theo cột** Server 2: chỉ SELECT + UPDATE 16 cột thực thi của `ntt_ai_tasks`, SELECT+INSERT `ntt_ai_suggestions`, SELECT `ntt_issue_types`. Server 1: toàn quyền DML. **Không ghi chồng tầng khác.**

### 2.3 Số liệu đã đối chiếu với drawio (CSDL_SQL_Diagram_v4.png)

**Chốt (✅ khớp)**: 21 bảng đúng theo sơ đồ; các cột CHÍNH (PK, số nhận diện, kiểu bit/enum/varchar/text) đúng; CHECK constraint cân đối ở mức 02/03.

**Lệch — ntt_issue_types**:
- SQL 02 seed **8 dòng** (spelling, grammar, presentation, logic, distractor, style, key_doubt, none).
- Drawio ghi "Dữ liệu gốc: **6 dòng** (seed)" (thiếu presentation, style).
- SQL 02 có `sort_order` (INT NOT NULL DEFAULT 0) cho `ntt_issue_types`; drawio không có cột này.

**Lệch — created_by / changed_by audit columns** (drawio N/NOT NULL vs SQL NULL):

| Bảng | Drawio | Migration 02/03 | Hiệu ứng |
|---|---|---|---|
| ntt_subjects.created_by | N | NULL | Drawio mô tả sai |
| ntt_questions.created_by, updated_by | N, Y | NULL, NULL | created_by sai lệch (updated_by khớp Y) |
| ntt_question_revisions.changed_by | N | NULL | sai lệch |
| ntt_ai_tasks.created_by | N | NULL | sai lệch + entity bên dưới |
| ntt_moodle_profiles.created_by | N | NULL | sai lệch |
| ntt_moodle_exchanges.created_by | N | NULL | sai lệch |
| ntt_eval_runs.created_by | N | NULL | sai lệch |

**Lệch — `created_by` (drawio N) vs `BIGINT NULL` trong SQL + entity `nullable=false`**: drawio ghi audit columns là NOT NULL; migration 02/03 khai báo NULL (đúng khi anonymous import từ Moodle/chạy test không có admin ghi). Nếu entity `AiTask` (nullable=false) đi kèm `ddl-auto=update`, Hibernate sẽ ALTER cột — cần sửa trước khi chạy `validate`.

**Lệch — Sort_order trong ntt_issue_types**: chỉ có 4 cột (`issue_type`, `label_vi`, `is_evaluated`, `sort_order`) — drawio chỉ ghi 3 cột, không có sort_order.

**Lệch — CHK `chk_task_question_check`**: drawio ghi "CHK question_check ⇒ question_id + revision NOT NULL; nl_search ⇒ cả hai NULL" — đúng theo file 02.

### 2.4 Lệch — Class Diagram (CSDL_Class_Diagram_v4.png) vs SQL

Class diagram hợp lý trong việc mô tả quan hệ qua edges, nhưng thiếu các cột:

| Bảng | Thiếu ở class diagram | Có trong SQL |
|---|---|---|
| Subject | `created_by` | ✅ |
| Topic | `parent_id`, `subject_id`, `parent_key {GEN}` (có edge) | ✅ |
| Question | `subject_id`, `topic_id`, `created_by`, `updated_by` | ✅ |
| QuestionRevision | `question_id`, `changed_by` | ✅ |
| AiTask | `question_id`, `question_revision_id`, `eval_run_id`, `created_by` | ✅ |
| AiSuggestion | `task_id`, `issue_type`, `decided_by`, `applied_revision_id` | ✅ |
| IssueType | `sort_order` | ✅ |
| MoodleProfile | `created_by` | ✅ |
| MoodleExchange | `profile_id`, `created_by` | ✅ |
| MoodleExchangeItem | `exchange_id`, `question_id`, `question_revision_id` | ✅ |
| EvalRun | `created_by` | ✅ |
| EvalItem | `question_id`, `injected_from_item_id` | ✅ |
| EvalLabel | `item_id`, `issue_type` | ✅ |
| EvalPrediction | `run_id`, `item_id`, `issue_type`, `suggestion_id` | ✅ |

Class diagram cũng không có `course_id` cho `ntt_admins` (SQL diagram có `course_id`, nullable FK → ntt_courses).

**Hiện trạng trong code (server-ai-worker):**
- `AiTask.created_by` khai báo `nullable = false` (entity) — **mâu thuẫn với SQL NULL**. Cùng với `ddl-auto=update` (mặc định) sẽ phát sinh ALTER.
- `AiTask.active_flag` insertable=false updatable=false ✅ đúng với design.
- `IssueType.sort_order` entity dùng đúng SQL (không có ở drawio) ✅.
- `DemoDataInit` seed **6 dòng** (spelling/grammar/logic/distractor/key_doubt/none) khi chạy độc lập — khác SQL migration (8 dòng); ảnh hưởng tới dữ liệu demo `ntt_issue_types`.

### 2.5 grants file 04 — vấn đề schema name

- File 04 viết `GRANT ... ON ntt.* TO 'ntt_s1'` — **schema `ntt`** — nhưng `.env.example` (be-web-admin) dùng `MARIADB_DBS=ntt_thesis`. Khi deploy thật: grants chạy sai schema, server s1/s2 tự tích hợp bằng quyền mặc định của user cấp, hoặc bị lỗi quyền.
- `ntt_s2`: SELECT `ntt_ai_tasks` + 16 cột UPDATE (đúng danh sách ở `AiTask.java`: status, attempt_count, started_at, finished_at, error_code, error_message, result_json, raw_response, is_json_valid, model_name, model_digest, runtime_version, prompt_version, params_json, usage_json, inference_ms) + SELECT/INSERT `ntt_ai_suggestions` + SELECT `ntt_issue_types`. **`created_by`, `eval_run_id`, `question_id`, `input_json` không được Server 2 ghi** ✅.

### 2.6 Điểm cần lưu ý (không phải lỗi)

- **Enum chữ hoa/thường**: 7 bảng auth dùng HOA; 14 bảng mới dùng thường (`active`, `clean`, `pending`...). File 02 nêu rõ — nhưng Java enums/converters phải thống nhất trước khi chạy `ddl-auto=validate`.
- **Trigger + Hibernate DDL**: migration tạo trigger; `ddl-auto=update` (mặc định) có thể chặn ALTER nên nên dùng `validate` hoặc `none` khi đã migrate. File 04 cũng khuyến nghị validate.
- **active_flag trong CHECK**: không được CHECK trong SQL (MariaDB không cho phép) — mọi kiểm soát status phải ở service.
- **nl_search**: không có question_id → UNIQUE không chặn trùng; rate-limit phải qua Redis (`ai:rl:{admin_id}`) — chưa có code trong be-web-admin.

---

## 3. Báo cáo thực tập demo (server-ai-worker) — đã kiểm tra

### 3.1 Nguồn dữ liệu

- Bộ 1000 câu, **AI tự sinh** (`python_demo_llm/gen_eval_questions.py`, seed `20261120`, sinh ngày `2026-11-20`).
- 10 môn × 100 câu: INT3202 (Cơ sở dữ liệu), INT3304 (Mạng máy tính), PHYS, INT3401 (AI), BIO, HIST, CHEM, INT3306 (Java), MATH, TOEIC.
- gold: none 250 / spelling 170 / grammar 165 / logic 200 / distractor 215.
- Độ khó: 1: 38, 2: 488, 3: 404, 4: 68, 5: 2.
- **Lưu ý**: gold là do trình sinh quy định (`correct_idx` vs `true_correct_idx` khác nhau ở 200 câu — injected-error patterns). Không phải nhãn người; không có split DEV/TEST.

### 3.2 Kiểm tra internally consistency (số liệu screenshot)

| Check | Kết quả |
|---|---|
| Diagonal sum = 791/1000 = 79.1% | ✅ |
| Accuracy: 791/1000 | ✅ |
| Accuracy theo môn | ✅ (BIO 81%, CHEM 90%, HIST 82%, INT3202 72% — đúng theo số liệu tính; 6 môn phía sau bị cuộn mất khỏi ảnh) |
| Macro-F1 = mean per-label F1 | ✅ (0.542+0.837+0.959+0.870+0.758)/5 = 0.793 |
| Macro-P = mean per-label P | ✅ (0.923+0.992+0.936+0.769+0.619)/5 = 0.848 |
| Macro-R = mean per-label R | ✅ (0.384+0.724+0.982+1.000+0.977)/5 = 0.813 |
| Micro-F1 = accuracy (đơn nhãn) | ✅ = 0.791 |
| Câu lỗi kỹ thuật = 0 | ✅ (đã kiểm bằng `error_count=0` + không có `error` trong confusion matrix) |
| Per-label support | ✅ (250/170/165/200/215) |

### 3.3 Phân tích chi tiết báo cáo

- **none (câu sạch) recall = 0.384**: 154/250 câu sạch bị gắn nhãn có lỗi — 94 câu sạch→`distractor`, 48 câu sạch→`logic`, 11 → `grammar`, 1 → `spelling`. **Báo động giả trên câu sạch ~61.6%** — xa ngưỡng ≤ 15% của đề cương (mục 4.3).
- **logic — recall 1.000** (200/200 câu logic được dự đoán đúng), nhưng precision chỉ 0.769: nhiễu từ 60 câu sạch + 7 câu spelling + 5 câu distractor bị gắn nhãn logic.
- **distractor — recall 0.977, precision 0.619**: 129 câu số khác bị nhãn distractor (chủ yếu câu sạch).
- **spelling — precision 0.992**, recall 0.724: 47 câu spelling bị bỏ sót.
- **grammar — precision 0.936, recall 0.982**: rất ổn định (chính tả/ngữ pháp là hai nhóm AI tốt nhất — phù hợp với giả thuyết "LLM hơn luật rõ rệt" ở E2-E4).

**Giới hạn báo cáo** (Quan trọng cho thesis):
1. Chỉ đo **dự đoán nhãn**, không đo chất lượng đề xuất (text gốc/span/proposed_text có đúng không).
2. gold **AI-sinh** không phải nhãn người; không có kappa (mục 4.3 cần hai người gán độc lập, kappa ≥ 0.70).
3. **Không có baseline E1 (rule-based)** để so sánh như đề cương (mục 4.3 E1–E8).
4. Không có dev/test split 100/200, không có seed/temperature ghi lại đầy đủ (chỉ ở DemoDataInit/properties, không nằm trong run).
5. Chỉ 5 nhãn trong 6 evaluated (bỏ Presentation/Style) — khác với ntt_issue_types (8 dòng, 6 evaluated).

---

## 4. Khoảng trống với đề cương — UC01–UC12

| UC | Chức năng | Trạng thái |
|---|---|---|
| UC01 | đăng nhập | ✅ (FE login + BE) |
| UC02 | TOTP | ✅ (FE + BE + Flutter + auth tables) |
| UC03 | quản lý user + phân quyền | ⚠️ |
| UC04 | CRUD câu hỏi | ❌ (SQL đã chốt, chưa có entity/service/controller) |
| UC05 | tìm kiếm | ❌ |
| UC06 | tìm kiếm NL | ⚠️ (Server 2 có; chưa ghép Server 1) |
| UC07 | AI check | ⚠️ (Server 2 demo, chưa ghép Server 1) |
| UC08 | xem đề xuất | ❌ (FE) |
| UC09 | theo dõi task | ❌ (FE + API) |
| UC10 | nhập Moodle XML | ❌ |
| UC11 | xuất Moodle XML | ❌ |
| UC12 | quản lý cấu hình Moodle | ❌ |

UI (đề cương 4.2): 5 màn hình → đang có 1/5 (đăng nhập TOTP). Thiếu: danh sách câu hỏi + form, xem xét đề xuất side-by-side, theo dõi task, Moodle import/export.

---

## 5. Rủi ro kỹ thuật / lưu ý khi thực hiện

1. **Created_by**: SQL NULL, entity `nullable=false` — sửa thành SQL NOT NULL hoặc entity nullable=true trước khi chạy validate.
2. **Grants vs env**: sửa schema `ntt` → `ntt_thesis` trong 04 grants.
3. **Demo vs SQL seed**: DemoDataInit seed 6 dòng; migration 02 seed 8 dòng → cần thống nhất.
4. **`ntt_issue_types` 5 vs 8 nhãn**: đề cương 4.3 định 4 nhãn lỗi (chính tả/ngữ pháp/logic/phương án nhiễu kém) + none; SQL = 6 evaluated + none + key_doubt. Demo = 5 nhãn (none + 4 lỗi AI-predictable + key_doubt→logic). Chọn rõ 1 trong 2 trước khi chốt số liệu Chương 4.
5. **nl_search**: rate-limit qua Redis — chưa code.
6. **Triggers + DDL update**: dùng `ddl-auto=validate` từ lúc migrate (file 04 cũng khuyến nghị).
7. **Demo metrics**: không tính FN đầy đủ (nhãn có nhưng không dự đoán) như view `vw_eval_confusion` — đề cương 4.3 cần công thức theo item/issue_type qua service.
8. **Wrong items**: chỉ hiển thị tối đa 10 item sai theo trang bằng `onlyWrong=true` (limit tối đa 50); `errorCount` hiển thị trong run meta (không bị cuộn). Nhìn thấy ở trang re-render qua `/runs/{id}/metrics`.

---

## 6. Khuyến nghị thứ tự ưu tiên (không thực hiện ở bước này)

1. Server 1: entity + service + REST cho `ntt_subjects`, `ntt_topics`, `ntt_questions` (CRUD đơn + revision + validation).
2. Task queue Server 1 → Redis → Server 2.
3. fe_web_admin: danh sách câu hỏi + form + tìm kiếm + lọc.
4. fe_web_admin: xem đề xuất AI side-by-side + theo dõi task.
5. Moodle XML: export CSV/XML + nhập/đổi exchange log.
6. Chương 4.3: tập 300 câu có gán nhãn, DEV/TEST split, scenario E1–E8 — dùng số liệu này làm số chính thức + phân tích.
