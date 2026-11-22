# Rà soát mã nguồn — commit mới nhất `feature/apply-ui-project-thesis`

- **Commit rà soát**: `8bc435e69b03836bc8d38958294100e20c38880d` (2026-11-22 21:19 +07) — merge commit của nhánh `feature/apply-ui-project-thesis`, nhánh làm việc `arena/aa735601-project-thesis-ntt`.
- **Ngày rà**: 2026-11-22
- **Đối tượng**: `be-web-admin` (Spring Boot), `server-ai-worker` (Spring Boot), `fe_web_admin` (Vue 3), `qbank-console-vue3` (Vue 3 — Dashboard ngân hàng câu hỏi), `app_mobile` (Flutter), `php_moodle_503` (Moodle 5.03 + theme), `project-design/UI_Accept_v9_Thesis.html` (bản mẫu HTML/CSS/JS), `database/` + `be-web-admin/sql/` (CSDL v4).
- **Nguồn đối chiếu nghiệp vụ**: Đề cương 16-11-2026 (Từ Quang Nhật, 59 trang) — Chương 1.6 phạm vi, 3.1 kiến trúc, 3.2 nghiệp vụ UC01–UC12, 3.3 AI, 3.4 CSDL, Chương 4 (E1–E8).

## Cách rà & giới hạn

| Làm được | Không làm được trong sandbox |
|---|---|
| Đọc toàn bộ mã nguồn 4 project + SQL + đề cương PDF | Không có JDK/Gradle → **không compile được 2 server Spring Boot** |
| `npm install` + `npm run build` cho `qbank-console-vue3` → **build PASS** (37 module, JS 117 KB / 45,66 KB gzip, CSS 52,5 KB) | Không có MariaDB / Redis → không chạy tích hợp |
| Kiểm tra tĩnh: endpoint ↔ UI ↔ bảng ↔ enum ↔ ràng buộc | Không có Flutter/Dart SDK → không `flutter analyze`/`test` |
| Đối chiếu số liệu Chương 4 với code thực nghiệm | Không có PHP/Moodle runtime → không test nhập XML thật |

> ⚠️ **Lưu ý về các tài liệu review cũ trong repo** (`docs/review-current-state.md`, `docs/review-4-projects-business-logic.md`, `docs/evaluation-ui-accept-v3.md`): các file này rà nhánh `arena/f960d1ff` @ `90bf3e053`, trong đó mô tả `fe_web_admin` có **15 trang shell**, `TopBar.vue`, `stores/appStore.ts`, `routers_after_login.ts`. **Những file đó KHÔNG tồn tại trong commit này** — commit `8bc435e` chỉ có 5 trang (login, information, 404…). Vì vậy các kết luận F1–F6 trong tài liệu cũ **không áp dụng** cho commit hiện tại. Tài liệu này rà lại từ đầu trên cây mã nguồn thật.

---

## 0. Kết luận nhanh

### 0.1 Một câu

> Dự án đang có **hai khối rất tốt nhưng rời nhau**: (1) khối xác thực 2FA/TOTP của `be-web-admin` + `app_mobile` hoàn chỉnh và có chất lượng bảo mật tốt; (2) khối suy luận LLM của `server-ai-worker` hoàn chỉnh ở mức demo. **Trong khi đó phần trung tâm của đề tài — ngân hàng câu hỏi (CRUD, phân loại, tìm kiếm, duyệt đề xuất, Moodle XML) — chưa có một dòng backend nào**, và Dashboard mới chuyển được 3/12 màn hình từ bản mẫu HTML sang Vue, toàn bộ dữ liệu là mock.

### 0.2 Bảng điểm hiện trạng

| Thành phần | Trạng thái | Điểm mạnh thật | Chặn chính |
|---|---|---|---|
| `be-web-admin` (Server 1) | **Chỉ có xác thực + chat** | TOTP/AES-GCM/HKDF/anti-replay/audit rất tốt | 11/21 bảng v4 chưa có entity/service/REST |
| `server-ai-worker` (Server 2) | **Hoàn chỉnh nhưng độc lập** | Structured output, chống ảo giác, SSRF guard, preflight luật | Không nối Server 1; khác DB; grant cấm INSERT task |
| `qbank-console-vue3` (Dashboard mới) | **Demo UI 25%** | Port sạch, build OK, giữ design token | 100% mock, chưa có API layer, thiếu 9 màn + mọi hộp thoại |
| `project-design/UI_Accept_v9_Thesis.html` | **Đầy đủ (mẫu)** | 12 màn + toàn bộ luồng nghiệp vụ chạy được | Là nguồn sự thật thứ 3, dễ lệch với bản Vue |
| `fe_web_admin` (Vue cũ) | **Chỉ xác thực** | Silent refresh, 2FA UI, SSE | Không có màn ngân hàng câu hỏi; trùng vai với qbank |
| `app_mobile` (Flutter) | **TOTP tốt, chat ngoài phạm vi** | Secure storage, algorithm lấy từ server | TOTP default `sha1` lệch server `HmacSHA256` nếu thiếu field |
| CSDL v4 (21 bảng) | **Thiết kế tốt, ít được dùng** | FK ghép, cột sinh, trigger, grant theo cột | 11 bảng không có code; thiếu bảng phân công môn cho giảng viên |
| Moodle | **Vendor nguyên source** | Theme NTT + 1 file XML mẫu | Không có code xuất/nhập XML; repo nặng 595 MB |

### 0.3 Sáu việc phải làm ngay (theo thứ tự)

1. **Dựng lớp API ngân hàng câu hỏi ở Server 1** (subject/topic/question CRUD + filter + validate 4 đáp án + phân trang). Không có lớp này thì mọi thứ khác chỉ là demo.
2. **Chốt kiến trúc tích hợp Server 1 ↔ Server 2** — hiện tại không có một dòng code nào nối hai server, và 2 server mặc định trỏ **hai database khác nhau**. Phải chọn: (A) làm đúng đề cương (Server 1 tạo task → Redis queue → Server 2 ghi kết quả), hoặc (B) sửa Chương 3 cho khớp thực tế (Server 2 tự sở hữu task + FE gọi trực tiếp). Trả lời sai câu này trước hội đồng là mất điểm nặng.
3. **Nối `qbank-console-vue3` vào API thật** và port nốt 8 màn còn lại từ file mẫu (đã có code HTML/JS chạy được, chỉ cần dịch sang Vue).
4. **Sửa 3 lỗi nghiệp vụ trong UI**: AI xong tự set `clean` (vi phạm “AI chỉ đề xuất”), thang độ khó 3 mức ≠ CSDL 5 mức, thiếu trạng thái `failed`.
5. **Đồng bộ Chương 4 với code thực nghiệm** (300 câu vs 1000 câu; split DEV/TEST; đa nhãn; kappa) — nếu không, số liệu bảo vệ sẽ không truy vết được.
6. **Dọn bảo mật/hygiene**: `fe_web_admin/.env`, `php_moodle_503/.../moodle/config.php` (root/1234) đang **được track trong Git**; Moodle 595 MB trong repo.

---

## 1. Kiến trúc thực tế vs đề cương

### 1.1 Đề cương nói gì (Chương 3.1)

```
Vue.js Dashboard + Flutter → Server 1 (Spring Boot: xác thực, nghiệp vụ, 2FA)
                                   ├─ MariaDB (dữ liệu chuẩn)
                                   ├─ Redis (cache + trạng thái + HÀNG ĐỢI tác vụ AI)
                                   └─ điều phối task → Server 2 (Spring Boot: prompt, HTTP client)
                                                          └─ Colab/Ollama Qwen2.5-14B
Server 2 cập nhật trạng thái/kết quả → Server 1 đọc, kiểm tra, trả đề xuất cho Vue
```

### 1.2 Code đang chạy thực tế

```
fe_web_admin (5173)  ──Bearer──► be-web-admin :8080/api/web|mobile/admin/**   (chỉ auth + chat)
qbank-console-vue3   ── mock ──► (không gọi gì)
                                    MariaDB: ntt_thesis  (7 bảng auth + 3 bảng chat)

(ai) server-ai-worker :8082/api/web/ai/**  ──Bearer/internal token──► FE gọi TRỰC TIẾP
                                    MariaDB: ntt_demo_llm (tự tạo ntt_ai_tasks, ntt_ai_suggestions,
                                                          ntt_issue_types, ntt_demo_*)
                                    Redis: ai:queue (nội bộ worker) ──► Colab/Ollama
```

### 1.3 Năm điểm lệch (bằng chứng cụ thể)

| # | Lệch | Bằng chứng | Hệ quả |
|---|---|---|---|
| **A1** | Server 1 không có nghiệp vụ câu hỏi | `find be-web-admin/src -name "*.java"`: chỉ có `chat/**`, `controller/{web,mobile}/**` (auth, 2FA, enroll, chat), `model/jpa/{Admin,AuthAuditLog,BackupCode,Course,MobileDevice,TotpCredential,WebSession}`. Không có `Question`, `Subject`, `Topic`, `Moodle*`, `AiTask` | UC04/05/06/08/09/10/11/12 **không có backend** |
| **A2** | Hai server không nối nhau | `grep -rniE "8082\|ai-worker\|AiTask" be-web-admin/src` → **0 kết quả**. Worker tự tạo task: `AiTaskService.submit()` → `tasks.saveAndFlush(entity)` + `queue.push(id)` | Đề cương nói Server 1 tạo task ID & đẩy Redis; code nói Worker tự tạo. Không có “một đầu mối dữ liệu chuẩn” |
| **A3** | Hai database khác nhau | `be-web-admin/.env.example`: `MARIADB_DBS=ntt_thesis`; `server-ai-worker/src/main/resources/application.yml:48`: `${MARIADB_DBS:ntt_demo_llm}` | Nếu bật cùng lúc, task/suggestion nằm ở DB khác với câu hỏi → FK `ntt_ai_tasks.question_id → ntt_questions` **không thể tồn tại** |
| **A4** | Grant cấm đúng việc worker đang làm | `sql/2026-11-19-04-grants.sql:28-39`: `ntt_s2` chỉ `SELECT` trên `ntt_ai_tasks`, `UPDATE` 16 cột, `INSERT` suggestions — **không INSERT task, không DDL**. Worker lại `INSERT` task và `ddl-auto=update` (`application.yml:67`) | Chạy worker bằng `ntt_s2` trên schema v4 sẽ **fail ngay** ở bước submit (và fail lúc khởi động vì DDL) |
| **A5** | Tên schema trong grant không khớp | Grant ghi `ON ntt.*` / `ntt.ntt_ai_tasks`; ứng dụng dùng `ntt_thesis` | Grant không có tác dụng, “bằng chứng bảo vệ” (mục 3 của file grant) không chạy được. Sửa: dùng `ntt_thesis` hoặc đổi `MARIADB_DBS=ntt` |

> **Khuyến nghị kiến trúc**: chọn **(A)** nếu còn ≥ 6 tuần — làm `AiTaskController` + `AiQueueService` ở Server 1, worker chỉ poll/claim và PATCH kết quả (đúng grant đã thiết kế). Chọn **(B)** nếu < 4 tuần — giữ worker tự chủ, nhưng phải: dùng chung DB, thêm grant `INSERT` + FK thật, và **viết lại mục 3.1** (Server 2 là dịch vụ độc lập, Server 1 chỉ proxy/không giữ task). Không được để nguyên như hiện tại rồi thuyết trình theo đề cương.

---

## 2. Đối chiếu nghiệp vụ UC01–UC12

| UC | Yêu cầu (đề cương 3.2) | Có gì trong code | Thiếu / lệch | Mức |
|---|---|---|---|---|
| **UC01** Đăng nhập | 1 request adminId+password+TOTP | ✅ `POST /web/admin/auth/login` nhận cả `totpCode`; `LoginOutcome` TOTP_REQUIRED/INVALID/LOCKED/OK | — | ✅ |
| **UC02** Xác minh TOTP | Chống dùng lại mã | ✅ `totp:used:{adminId}:{step}` (`TotpDisableServiceImpl`), lock 5 lần/5 phút, window ±1, audit | Redis lỗi → fail-closed (đã ghi chú) | ✅ |
| **UC03** Quản lý user & phân quyền | Tạo/cập nhật tài khoản, gán vai trò | ⚠️ Có `AdminControllerTest` (`/web/admin/all`) + seed; `Role = ADMIN/LECTURER` | Không có API tạo/sửa/khóa user; **không có bảng phân công giảng viên ↔ môn** (design HTML giả định `user.subjects`) | ❌ |
| **UC04** CRUD câu hỏi | Tạo/đọc/sửa/xóa, kiểm tra đủ 4 phương án | ❌ UI mock: đọc/lọc/xóa mềm; “Thêm/Sửa” chỉ `notYet()` | Không có backend; không có validate 4 đáp án + 1 đáp án đúng ở cả 2 tầng | ❌ |
| **UC05** Tìm kiếm & lọc | Từ khóa + bộ lọc | ⚠️ Chỉ client-side trên mock (`store.js:31 list()`) | Cần endpoint + `FULLTEXT ft_q_text` (đã có index) | ❌ |
| **UC06** Tìm kiếm thông minh | NL → bộ lọc có cấu trúc | ✅ Worker: `parseNlSearch` whitelist trường/toán tử, chặn SQL tự do | Không có endpoint ở Server 1; UI là placeholder; chưa có test “exact match” (E8) | ⚠️ |
| **UC07** Kiểm tra nội dung AI | Bất đồng bộ, trả mã task | ✅ Worker `POST /web/ai/tasks` + Redis queue + retry + node pool | Server 1 không có API tạo task; UI chỉ giả lập `setTimeout` (`store.js:51-77`) | ⚠️ |
| **UC08** Duyệt đề xuất AI | Accept/Reject, chỉ khi duyệt mới đổi dữ liệu | ❌ Không có code; UI không có màn này | Cần transaction: insert `ntt_question_revisions`, update `validation_status`, set `decision/decided_by/at`, `applied_revision_id` | ❌ |
| **UC09** Theo dõi trạng thái task | Tra theo mã task | ⚠️ Worker có `GET /tasks/{id}`, `/status` | UI placeholder; không có polling/stream; task ID UUID do worker sinh | ⚠️ |
| **UC10** Nhập Moodle XML | Parse, kiểm tra, lưu | ❌ **Không có code Java/Dart nào** (`grep "question type="` chỉ thấy file design HTML và `php_moodle_503/quiz/quiz_toeic.xml`) | Parser, ánh xạ category, chống trùng | ❌ |
| **UC11** Xuất Moodle XML | Sinh tệp chuẩn | ❌ Chỉ có hàm `xout()` trong HTML mẫu | Service export + profile (`ntt_moodle_profiles`) | ❌ |
| **UC12** Quản lý cấu hình Moodle | Hồ sơ kết nối | ❌ Không có code | 2 bảng `ntt_moodle_*` trống | ❌ |

**Tự đánh giá thẳng**: hiện có **2/12 UC hoàn chỉnh** (UC01, UC02), **4/12 một phần** (UC03, UC06, UC07, UC09), **6/12 chưa có** (UC04, UC05, UC08, UC10, UC11, UC12). Đề cương Chương 4.2 hứa “12 chức năng UC01–UC12 vận hành đúng” — **hiện chưa đúng**.

---

## 3. Đối chiếu thực nghiệm Chương 4 (E1–E8) với code

Đây là phần dễ bị hội đồng “đào” nhất, vì số liệu trong đề cương và code đang đo **hai thứ khác nhau**.

| Nội dung Chương 4 | Đề cương | Code thực tế | Kết luận |
|---|---|---|---|
| Kích thước tập | **300 câu** (90 sạch, 45 chính tả, 45 ngữ pháp, 60 logic, 60 nhiễu) | `python_demo_llm/gen_eval_questions.py`: **1000 câu**, quota `none 250 / spelling 170 / grammar 165 / …`, 12 môn, sinh bằng template + seed | ❌ Lệch số liệu & bản chất |
| Nguồn dữ liệu | “câu thật của giảng viên (đã ẩn danh) + câu chèn lỗi có kiểm soát” | Sinh tự động từ mẫu câu + chèn lỗi tất định (`gen_eval_questions.py` docstring) | ❌ Chưa có dữ liệu thật |
| DEV/TEST + chống rò rỉ | 100 DEV / 200 TEST, test chỉ chạy 1 lần | `ntt_demo_eval_runs` không có cột `split`; `DemoEvalService.start(mode)` chạy trên toàn bộ | ❌ Không có split |
| Gán nhãn 2 người + Cohen’s kappa | 2 annotator độc lập, người thứ 3 phân xử, kappa ≥ 0,70 | Không có; nhãn do script sinh (`goldIssueType`) | ❌ Chưa có |
| Độ đo | TP/FP/FN **theo từng nhóm lỗi**, Macro/Micro-F1, tỷ lệ báo động giả trên nhóm sạch | `DemoEvalService`: **phân loại đơn nhãn** 5 lớp (`none, spelling, grammar, logic, distractor`), confusion matrix + P/R/F1/label + macro + micro(=accuracy) | ⚠️ Cùng tên, khác định nghĩa: đề cương đa nhãn, code đơn nhãn |
| E2/E3/E4 zero/few/few-CoT | 3 kịch bản | `AiPromptFactory` **có** 3 chế độ (`zero|few|few_cot`), nhưng `DemoEvalService.start()` chỉ nhận `rule|ai`; muốn đổi prompt phải sửa `AiPromptFactory` hoặc chạy qua notebook Python | ⚠️ Có năng lực, chưa lộ ra API |
| E5 so sánh lượng tử hóa | Tùy chọn Q8/Qwen-7B | Không có | ❌ |
| E6 độ ổn định 3 seed | Có | Không có tham số seed trong `DemoEvalService` | ❌ |
| E7 độ trễ & tỷ lệ JSON hợp lệ khi 1/2/4 request đồng thời | Có | Có `inference_ms`/`wall_s` lưu theo item, nhưng không có kịch bản concurrency | ⚠️ |
| E8 exact-match bộ lọc tìm kiếm | 50 truy vấn | Không có (grep `exact` trong `demo/*.java` = 0) | ❌ |
| Hai harness song song | — | `server-ai-worker/demo/*` (Java) và `python_demo_llm/*.ipynb` + `runs/*.jsonl` | ⚠️ Nguy cơ 2 bộ số liệu khác nhau cho cùng một bảng trong luận văn |

**Khuyến nghị**: chọn **một** harness (nên là Java, vì nó đã ghi được vào MariaDB + có endpoint metrics) và:
- thêm `split` (dev/test) + `prompt_mode` + `seed` vào `ntt_eval_runs` (hoặc `notes` nếu không muốn sửa DDL);
- ghi **đa nhãn**: mỗi item có thể có nhiều `issue_type` (bảng `ntt_eval_labels` đã hỗ trợ), dự đoán cũng đa nhãn (`ntt_eval_predictions` có `UNIQUE (run_id, item_id, issue_type)` — dùng đúng thiết kế này thay vì nhãn đơn);
- hoặc **trung thực sửa Chương 4**: “đánh giá phân loại đơn nhãn trên 1000 câu tổng hợp” + thêm mục “hạn chế”. Cách này nhanh và vẫn bảo vệ được, miễn là không tuyên bố quá.

---

## 4. Rà chi tiết theo project

Mức: **P0** chặn demo/bảo vệ · **P1** sai nghiệp vụ/không nhất quán · **P2** chất lượng kỹ thuật · **P3** dọn dẹp.

### 4.1 `be-web-admin` (Server 1) — 8.493 dòng Java

**Điểm mạnh (nên giữ và nhấn khi bảo vệ)**
- TOTP đúng RFC 6238, so sánh hằng thời gian, window ±1, chống replay theo `step` (Redis), lockout 5 lần/5 phút, audit append-only.
- Secret TOTP mã hóa AES-256-GCM + HKDF-SHA256, salt/IV riêng từng bản ghi, **AAD = `totp:{adminId}:v{keyVersion}`**, có version khóa để xoay; fail-fast nếu thiếu khóa (`CryptoService`).
- `JwtSecretGuard` chặn khởi động nếu dùng secret dev; CORS allowlist theo pattern; STATELESS + Bearer.
- Có test quy ước đường dẫn `ApiPathConventionTest` — rất đáng khen, nên mở rộng cho lớp nghiệp vụ.

**Phát hiện**

| ID | Mức | Vấn đề | Bằng chứng | Cách sửa |
|---|---|---|---|---|
| S1 | **P0** | Không có tầng nghiệp vụ ngân hàng câu hỏi | danh sách file ở mục A1 | Dựng `model/jpa/{Subject,Topic,Question,QuestionRevision,IssueType,AiTask,AiSuggestion,MoodleProfile,MoodleExchange}`, repository, service, controller theo kiến trúc `Controller → Service → Repository` đã dùng |
| S2 | **P0** | Không có Moodle XML | grep toàn repo | `MoodleXmlExporter`/`Importer` dùng StAX (`XMLStreamReader`) để chống XXE, map 4 đáp án, `fraction=100` đúng 1, category `$course$/top/...` |
| S3 | **P0** | Không có API AI/orchestration | A2 | Quyết định (A)/(B) ở mục 1.3 |
| S4 | P1 | Grant không khớp schema + cấm INSERT task | `04-grants.sql:15,28,39` vs `.env.example` | Đổi `ntt` → `ntt_thesis`; nếu chọn (B): thêm `GRANT INSERT`, `GRANT INSERT ON ntt_questions`? **Không** — câu hỏi vẫn thuộc Server 1 |
| S5 | P1 | `hibernate.ddl-auto` mặc định `update` | `application.yaml` + comment | Đặt `validate` khi đã có script; thêm test `ddl-auto=validate` trong CI |
| S6 | P2 | Logout web là `permitAll` và nhận token qua body | `SecurityConfig` khớp `POST /web/admin/auth/logout` | Chấp nhận được (revoke theo token), nhưng nên rate-limit và log audit |
| S7 | P2 | `forward-headers-strategy: native` | `application.yaml` | Chỉ bật khi chắc chắn sau nginx; nếu demo trực tiếp thì bỏ để tránh giả `X-Forwarded-For` vào audit log |
| S8 | P2 | 6 bản `resolveAdminId` trùng lặp | các controller 2FA/mobile | Gom thành `AdminTokenGuard` (1 chỗ sửa) |
| S9 | P3 | Rác trong `src/main/resources/static`: `phpDemo.php` gọi `http://localhost:8080/api/admin/all` | `static/phpDemo.php` | Xóa khỏi jar production |
| S10 | P3 | `last_login_at` đã được ghi (đã fix ở commit trước) | `AdminLoginServiceImpl:148` | ✅ xác nhận đã có |

### 4.2 `qbank-console-vue3` — Dashboard ngân hàng câu hỏi (661 dòng)

**Hiện trạng chuyển từ file mẫu**: build PASS, nhưng độ phủ rất thấp.

| Màn (nav) | Trong HTML mẫu | Trong Vue | Ghi chú |
|---|---|---|---|
| Tổng quan `dash` | ✅ | ✅ `DashboardView.vue` | Có, nhưng số liệu từ mock |
| Tất cả câu hỏi `bank` | ✅ | ✅ `QuestionBankView.vue` | Lọc/tìm/sắp xếp/chọn/phân trang OK |
| Câu hỏi theo môn `course` | ✅ | ✅ (cùng view) | OK |
| Môn học `courses` | ✅ (thẻ + tìm + CRUD) | ❌ placeholder | Thiếu cả lưới môn học |
| Tìm kiếm thông minh `search` | ✅ `nls()` (3 bước NL→JSON→SQL) | ❌ placeholder | UC06 |
| Xem xét đề xuất `review` | ✅ `diffWords`, duyệt đơn/lô, JSON viewer | ❌ placeholder | **UC08 là lõi học thuật, đang thiếu** |
| Tác vụ AI `tasks` | ✅ bảng + lọc trạng thái + retry | ❌ placeholder | UC09 |
| Moodle XML `moodle` | ✅ xuất/nhập + kiểm tra cấu trúc | ❌ placeholder | UC10/11 |
| Quản trị `admin` | ✅ danh bạ + thêm user + phân môn | ❌ placeholder | UC03 |
| Cài đặt LLM `llm` | ✅ 5 node Colab + kiểm tra kết nối | ❌ placeholder | Khớp `NodePool` của worker |
| Tài khoản `account` | ✅ hồ sơ + CCCD ẩn/hiện 30s | ❌ placeholder | — |
| Cài đặt `settings` | ✅ 2FA card + tùy chọn | ❌ placeholder | — |
| Hộp thoại thêm/sửa câu hỏi | ✅ `edit()` (validate 4 đáp án, chống trùng) | ❌ `notYet()` | **UC04** |
| Hộp thoại CRUD môn/chương | ✅ `cdlg()`, `topicMgrDlg()` | ❌ | UC04 phụ |

**Phát hiện chi tiết**

| ID | Mức | Vấn đề | Bằng chứng | Cách sửa |
|---|---|---|---|---|
| Q1 | **P0** | Toàn bộ dữ liệu là mock; không có lớp API | `repositories/questionRepository.js` (10 dòng, comment “đổi thân hàm sang axios”) | Tạo `src/api/http.js` (axios + interceptor như `fe_web_admin/src/services/spring`), đổi repository gọi `/api/web/admin/questions`; `main.js` đọc `VITE_API_BASE` |
| Q2 | **P1** | **AI chạy xong tự đặt `validation_status='clean'` mà không có người duyệt** | `store.js:68-70`: `q.st='ok'` khi `r.is.length===0` | Giữ `not_checked`/`has_issues`; chỉ khi người dùng bấm “Chấp nhận/Đã xem” mới set `clean` + `last_validated_at`. Đây là yêu cầu bắt buộc trong 3.3 (“AI chỉ hỗ trợ, người có thẩm quyền xác nhận”) |
| Q3 | **P1** | Đề xuất AI không có accept/reject | `S.sug` chỉ được push/filter; không có `decision` | Thêm `review` view + `POST /suggestions/{id}/decide` (transaction + revision) |
| Q4 | P1 | Thang độ khó 3 mức, CSDL 5 mức | `data/mock.js:52-53` vs `sql/...02...:126` `difficulty 1..5` | Chốt 1..3 cho phạm vi đề tài rồi sửa CHECK thành `BETWEEN 1 AND 3`, hoặc làm 5 mức ở UI. **Không để 3 vs 5** |
| Q5 | P1 | Thiếu trạng thái `failed` trong map UI | `mock.js:94` chỉ có 4 khóa; SQL có 5 giá trị | Thêm `failed: ['Kiểm định lỗi','er']` + hiển thị lý do (`error_code`) |
| Q6 | P1 | Sắp xếp “Mới nhất” dựa vào chuỗi `dd/MM HH:mm` **không có năm** | `store.js:30 tsk()` parse regex; dữ liệu `updatedAt:'18/11 08:15'` | Sắp xếp phía server theo `updated_at` (UTC); client chỉ gửi `sort=recent` |
| Q7 | P1 | Không có phân quyền theo môn | `store.js:8` `role:'admin'`, `name:'Demo Admin'`; `router/index.js:22` chỉ chặn `admin|llm` | Lấy role/name/subjects từ `/information/basic`; **backend phải lọc theo môn được phân công** (cần bảng phân công — xem D3) |
| Q8 | P1 | Không có kiểm tra “đủ 4 phương án / 1 đáp án đúng” ở bất kỳ đâu khi lưu | `notYet('Hộp thoại thêm/sửa')` | Bê nguyên validate của `edit()` trong HTML mẫu + validate Bean Validation ở server |
| Q9 | P2 | Trạng thái chọn (`S.sel`) giữ id câu đã bị xóa/không còn trong filter | `store.js:82-84` | Prune `sel` khi xóa/đổi filter; hoặc chọn theo “filter đang áp dụng” |
| Q10 | P2 | `setTimeout` không có cleanup/không huỷ được task | `store.js:51-77` | Khi có API: `POST /ai/tasks` trả `taskId` + polling có backoff; không giả lập bằng timer |
| Q11 | P2 | CSS phụ thuộc CDN ngoài | `src/assets/main.css:1` `@import ... jsdelivr bootstrap`; `index.html` Google Fonts | Vendor Bootstrap (hoặc bỏ — chỉ dùng vài class) + self-host font; demo offline/Colab sẽ vỡ layout nếu mạng chặn |
| Q12 | P2 | Không có lint/test/TS; `.gitignore` gõ sai `node_module` | `package.json`, `.gitignore` | Thêm `vitest` + `eslint`; sửa `node_modules/` |
| Q13 | P2 | Nút hành động chính đang là “chết” | `QuestionBankView.vue:80,119,120`; `App.vue:113` | Trong bản demo bảo vệ: ẩn nút chưa làm, hoặc làm nốt (ưu tiên thêm/sửa) |
| Q14 | P2 | A11y combobox thiếu | `CourseCombobox.vue`: `role=listbox/option` nhưng không `aria-selected`, không `aria-activedescendant`, focus không trả về input sau khi chọn | Bổ sung; đây là phần đã được đánh giá cao ở file mẫu nên không nên làm mất |
| Q15 | P3 | Comment “CHECK 1..3 sau chuẩn hóa v5” nhưng repo đang ở v4 | `mock.js:53` | Sửa comment/không dẫn phiên bản không tồn tại |

**Về chất lượng port**: nhìn chung **tốt hơn mức demo thường thấy** — dùng `reactive` store có vòng đời `idle→loading→ready|error`, giữ nguyên token CSS của design, giữ phím tắt `/`, `?`, `n`, có `aria-live` cho toast, phân trang + chọn toàn bộ theo filter, undo xóa. Vấn đề không phải chất lượng code mà là **độ phủ và việc cắt rời khỏi backend**.

### 4.3 `fe_web_admin` (Vue cũ) — 5.015 dòng

| ID | Mức | Vấn đề | Bằng chứng |
|---|---|---|---|
| F1 | **P1** | **`.env` được track trong Git** chứa `VITE_AUTH_PASSWORD=fcbeef03-6630-...` | `git ls-files fe_web_admin/.env` → có; nội dung dòng 1–2 |
| F2 | P1 | `package.json` tên `frontend-students`, thừa `vuetify/bootstrap/canvasjs` không dùng nhiều | `fe_web_admin/package.json` |
| F3 | P1 | Chỉ có 5 route; không có màn ngân hàng câu hỏi → **trùng vai với `qbank-console-vue3`** | `router/routers_after_login.js` |
| F4 | P2 | Route `/` và `/information-admin` để `allow: true`; guard chưa xử lý “đã đăng nhập vẫn vào /login” | `routers_*` |
| F5 | P3 | Có `VITE_NODEJS_*` nhưng không còn server Node | `.env` |

**Khuyến nghị**: chọn **một** frontend. Cách ít công nhất: giữ `fe_web_admin` làm app duy nhất, nhúng shell + view của `qbank-console-vue3` vào (đã cùng Vue 3 + Vue Router), tái dùng `SilentTokenRefresher`, `AdminService`, `SpringConfigHeader` cho qbank. Ngược lại (qbank làm app chính) thì phải port toàn bộ auth 2FA sang qbank — nhiều việc hơn.

### 4.4 `server-ai-worker` (Server 2) — 7.668 dòng

**Điểm mạnh đáng nhấn**: JSON Schema structured output (`AiPromptFactory`), whitelist `issue_type` (`AiResultParser:33`), `target_field` **không có** `correct_label`, chống trùng đề xuất theo `target|issue|text`, định vị `span` có fallback, `RulePreflightChecker` (baseline E1), `OllamaClient` retry + requeue, `NodePool` với SSRF guard (chỉ https + suffix `.trycloudflare.com`), `AiAuthFilter` fail-closed + so sánh hằng thời gian, test cho parser/preflight/locator/node.

| ID | Mức | Vấn đề | Bằng chứng |
|---|---|---|---|
| W1 | **P1** | Như A2/A3/A4: worker tự sở hữu task + DB riêng + grant không cho phép | `AiTaskService.submit`, `application.yml:48,67`, `04-grants.sql` |
| W2 | **P1** | 3 hệ từ vựng loại lỗi khác nhau | DB seed 8 (`02:81-100`), parser cho 5 (`spelling,grammar,logic,distractor,key_doubt`), eval/UI dùng 4–5 (thiếu `presentation`, `style`, `key_doubt`) |
| W3 | **P1** | Đo lường lệch Chương 4 | mục 3 |
| W4 | P2 | Không có idempotency: bấm tạo task 2 lần → 2 task (trừ khi DB chặn `uq_one_active_task`, mà cột `active_flag` cho `nl_search` không chặn được vì `question_id NULL`) | `AiTaskService.submit` |
| W5 | P2 | CORS mặc định chỉ `5173/8082` — nếu `fe_web_admin` và qbank chạy cùng lúc (đều mặc định 5173) sẽ xung đột cổng | `application.yml:184`, `vite.config.js` |
| W6 | P2 | Endpoint `demo/*` (seed 1000 câu, chạy eval) mở cùng chuỗi với API thật; nên tách profile | `DemoEvalController` |
| W7 | P3 | `submitBurst` gán `createdBy=1` mặc định | `AiTaskService` |

### 4.5 `app_mobile` (Flutter) — 9.928 dòng

| ID | Mức | Vấn đề | Bằng chứng |
|---|---|---|---|
| M1 | P2 | `AuthAccount.algorithm` mặc định `TotpAlgorithm.sha1`, server phát hành `HmacSHA256` | `auth_account.dart:21` vs `EnrollmentServiceImpl:140` |
| M2 | P2 | Toàn bộ module `chat/**` (~20 file) nằm ngoài UC01–UC12 → **scope creep**, tăng rủi ro tiến độ | `lib/features/chat/**` |
| M3 | P3 | Thiếu test cho `TotpGenerator` dù file có ghi vector RFC 6238 | `test/` chỉ 1 file |

**Khuyến nghị M1**: đổi default thành `sha256` (khớp server hiện tại) hoặc bắt buộc `algorithm` có trong JSON, và viết test dùng đúng 6 vector RFC 6238 đã ghi trong comment — đây là test “rẻ” và rất đẹp khi bảo vệ.

### 4.6 `php_moodle_503` (Moodle)

| ID | Mức | Vấn đề | Bằng chứng |
|---|---|---|---|
| P1 | **P1** | **`config.php` được track, chứa `$CFG->dbuser='root'`, `$CFG->dbpass='1234'`** | `moodle/config.php:27,30` (`git ls-files` xác nhận tracked) |
| P2 | **P1** | Vendor nguyên source Moodle **595 MB / 67.661 file**; `.git` phình **123 MB** | `du -sh`, `git ls-files | wc -l` |
| P3 | P2 | Chỉ có theme `ntt`; không có plugin/khối nào phục vụ nhập XML của đề tài | `public/theme/ntt/**` |
| P4 | P3 | File XML mẫu `quiz/quiz_toeic.xml` không được dùng bởi code nào (chưa có code) | `php_moodle_503/quiz/` |

**Khuyến nghị**: `git rm --cached` toàn bộ `moodle-latest-503/`, giữ `theme/ntt` + `quiz/*.xml` + `run.txt` hướng dẫn cài; hoặc chuyển sang git submodule/external storage. Rotate mật khẩu DB (dù là dev, vẫn phải sạch).

### 4.7 `project-design/UI_Accept_v9_Thesis.html` (1992 dòng)

- Đây là **bản đặc tả nghiệp vụ chạy được**, và là nguồn tham chiếu tốt: có `myScope/subjOk` (phân quyền theo môn), `diffWords` (so sánh gốc/đề xuất), `xout()/xin()` (Moodle XML kèm kiểm tra cấu trúc), `dec()/bulkDec()` (duyệt đề xuất), `topicMgrDlg`, `udlg` (phân công môn), `nls()` (NL search 3 bước), `tfaCard`.
- **Rủi ro D1**: cùng một CSS/JS tồn tại ở 2–3 nơi (HTML mẫu + `qbank-console-vue3/src/assets/main.css` + `fe_web_admin`). Không có cơ chế đồng bộ → sửa một nơi là lệch. Nên: coi HTML mẫu là **tài liệu tham chiếu đóng băng**, chỉ sửa bản Vue; hoặc tách token CSS dùng chung.
- **D2**: `xout()` sinh XML đúng dạng cơ bản nhưng **không có chống trùng** khi nhập lại (không tính `content_hash`), trong khi CSDL v4 đã có cột `content_hash` + index → phải dùng.
- **D3**: UI giả định mỗi giảng viên có `subjects[]` nhưng **CSDL v4 không có bảng phân công**. Cần `ntt_admin_subjects(admin_id, subject_id)` (hoặc `ntt_subject_lecturers`) trước khi làm UC03.

---

## 5. CSDL v4 vs code — bảng lệch

> Bản đầy đủ (đối chiếu diagram ↔ DDL ↔ entity, 12 điểm lệch + 17 rủi ro thiết kế + checklist): **`docs/review-csdl-v4-chot-vs-code.md`**.

| Hạng mục | Thiết kế v4 | Code hiện tại | Việc cần làm |
|---|---|---|---|
| Bảng có entity/service | 21 | **10/21**: 7 auth + 3 AI (`ntt_ai_tasks`, `ntt_ai_suggestions`, `ntt_issue_types`) — 3 bảng `ntt_chat_*` là bảng ngoài v4 | 11 bảng còn lại chưa dùng: `ntt_subjects`, `ntt_topics`, `ntt_questions`, `ntt_question_revisions`, `ntt_moodle_profiles`, `ntt_moodle_exchanges`, `ntt_moodle_exchange_items`, `ntt_eval_runs`, `ntt_eval_items`, `ntt_eval_labels`, `ntt_eval_predictions` |
| Tên schema | grant dùng `ntt` | app dùng `ntt_thesis` | Thống nhất |
| `difficulty` | 1..5 | UI 1..3 | Chốt 1 thang |
| `validation_status` | 5 giá trị (có `failed`) | UI 4 giá trị | Thêm `failed` |
| `content_hash` | NOT NULL, SHA-256 nội dung chuẩn hóa | Không có code tính | Bắt buộc: tính khi create/update + dùng để cảnh báo trùng & chống trùng khi import |
| `version` (optimistic lock) | INT DEFAULT 0 | Không có entity | `@Version` khi làm UC04/UC08 (chống 2 người sửa/duyệt đồng thời) |
| `ntt_question_revisions` | Append-only mỗi lần sửa | Không có code | Ghi revision trong transaction của accept suggestion |
| Trigger `trg_question_bu_stale` | Tự set `stale` khi nội dung đổi | Chưa chạy vì không có write path | Giữ; khi làm service phải set `validation_status` tường minh để tránh trigger ghi đè |
| `ntt_issue_types` 8 dòng | Có `presentation`, `style`, `key_doubt`, `none` | UI/worker/eval chỉ dùng 4–5 | Thống nhất 1 danh mục, UI hiển thị đủ |
| Phân công môn cho LECTURER | **Không có** | UI giả định có | Thêm bảng/hoặc cột JSON, hoặc bỏ phân quyền theo môn trong phạm vi |
| `ntt_eval_*` | Đa nhãn + split + annotator | Dùng `ntt_demo_*` đơn nhãn | Chuyển sang bảng v4 (đã đúng thiết kế) hoặc sửa Chương 4 |

Ngoài ra: `ntt_courses` (chương trình đào tạo: faculty/industry/major) **khác** `ntt_subjects` (môn học). UI đang đặt tên biến `COURSES` cho **môn học** — rất dễ map nhầm sang `ntt_courses` khi viết API. Nên đổi tên `SUBJECTS`/`subjects` ngay từ repository.

---

## 6. Hợp đồng API nên dựng (để qbank gọi được)

Ràng buộc: `ApiPathConventionTest` bắt mọi controller trong `controller.web` phải dưới `/web/admin`. Vì vậy giữ prefix `/api/web/admin/...`.

| # | Method & path | Quyền | Ghi chú nghiệp vụ |
|---|---|---|---|
| 1 | `GET /web/admin/subjects?q=&active=` | ADMIN, LECTURER | LECTURER chỉ thấy môn được phân công |
| 2 | `POST /web/admin/subjects` · `PUT /{id}` · `DELETE /{id}` | ADMIN | Xóa mềm `is_active=0` nếu đã có câu hỏi |
| 3 | `GET /web/admin/subjects/{id}/topics` · `POST/PUT/DELETE` | ADMIN | Chống chu trình bằng CTE đệ quy (ghi chú trong SQL 02) |
| 4 | `GET /web/admin/questions?q=&subjectId=&topicId=&difficulty=&validationStatus=&page=&size=&sort=` | cả 2 | Trả `page/size/total/sort`; dùng `FULLTEXT ft_q_text` cho `q` |
| 5 | `GET /web/admin/questions/{id}` | cả 2 | Kèm `version`, `contentHash` |
| 6 | `POST /web/admin/questions` | cả 2 | **Validate: 4 phương án khác rỗng, 1 đáp án đúng, độ khó hợp lệ**; tính `content_hash`; nếu trùng → cảnh báo (không chặn) |
| 7 | `PUT /web/admin/questions/{id}` (If-Match version) | cả 2 | Ghi revision + `updated_by`; trigger set `stale` nếu nội dung đổi |
| 8 | `DELETE /web/admin/questions/{id}` | cả 2 | Mềm: `status='archived'` (không mất revision/task) |
| 9 | `POST /web/admin/questions/bulk-delete` | cả 2 | Trả `succeeded[] / failed[{id,code}]` (không all-or-nothing) |
| 10 | `POST /web/admin/questions/{id}/ai-check` | cả 2 | Tạo task (chặn trùng bằng unique/RR); 202 + `taskId`; **không** đồng bộ chờ LLM |
| 11 | `GET /web/admin/ai/tasks?status=&type=&page=` · `GET /ai/tasks/{id}` | cả 2 | Polling 2–3s có backoff; trả `status, errorCode, attemptCount, inferenceMs` |
| 12 | `GET /web/admin/ai/tasks/{id}/suggestions` | cả 2 | Kèm `original/proposed/span/severity/issueType` |
| 13 | `POST /web/admin/ai/suggestions/{id}/decide` `{decision: accepted\|rejected, finalText?, rejectReason?}` | cả 2 | **Transaction**: kiểm `version`, tạo revision, cập nhật câu hỏi, set `decided_by/at`, `applied_revision_id`; từ chối thì không đụng câu hỏi |
| 14 | `POST /web/admin/ai/suggestions/bulk-decide` | cả 2 | Trả kết quả từng dòng |
| 15 | `POST /web/admin/search/natural` `{query}` | cả 2 | Gọi Server 2 `nl_search`; trả `{filters, warnings}`; **Server 1 dựng SQL tham số hóa** từ whitelist |
| 16 | `POST /web/admin/moodle/export` `{questionIds[], profileId}` | cả 2 | Trả `exchangeId` + tên tệp; `GET /moodle/exchanges/{id}/file` để tải |
| 17 | `POST /web/admin/moodle/import` (multipart) | cả 2 | Trả `{created, skipped, failed[{name,code,message}]}`; chống trùng bằng `content_hash` |
| 18 | `GET /web/admin/moodle/profiles` · `POST/PUT` | ADMIN | UC12 |
| 19 | `GET /web/admin/users?q=&role=&status=` · `POST/PUT` | ADMIN | UC03; kèm gán môn |
| 20 | `GET /web/admin/dashboard/summary` | cả 2 | Thay 4 thẻ mock: total, clean, pending suggestions, running tasks |

**Quy ước chung cần chốt trước khi code**: envelope `{success, message, data}` (đang dùng ở auth — giữ đồng nhất), mã lỗi nghiệp vụ (`VALIDATION_FAILED`, `DUPLICATE_CONTENT`, `VERSION_CONFLICT`, `AI_UNAVAILABLE`, `XML_INVALID`), phân trang `page/size` (0-based hay 1-based — chọn 1), thời gian ISO-8601 UTC.

---

## 7. Kế hoạch hành động

### P0 — phải xong để có thể demo “hệ thống” (không chỉ “2 mô-đun”)

| # | Việc | Định nghĩa hoàn thành | Ước lượng |
|---|---|---|---|
| 1 | Lớp nghiệp vụ Server 1: Subject/Topic/Question + CRUD + filter + pagination | `curl` tạo 1 câu thiếu 1 đáp án → 422; tạo hợp lệ → 201 + `content_hash`; lọc theo môn/chương/trạng thái trả đúng trang | 5–7 ngày |
| 2 | Nối qbank ↔ API (read path trước: list/detail/filter) | Tắt mock, dữ liệu lấy từ MariaDB; xóa mock.js; có `.env.example` + proxy | 2–3 ngày |
| 3 | Sửa 3 lỗi nghiệp vụ Q2/Q4/Q5 | AI xong không còn tự set `clean`; thang độ khó 1..3 (hoặc 1..5) khớp SQL; có badge `failed` | 0,5 ngày |
| 4 | Chốt kiến trúc Server1↔Server2 và sửa tài liệu/DB cho khớp | 1 trang quyết định + sửa `application.yml`/grant + sửa mục 3.1 đề cương | 1–2 ngày |
| 5 | Dọn bảo mật: bỏ track `.env`, `config.php`; rotate; thêm `.gitignore` | `git ls-files | grep -E '\.env$|config\.php'` rỗng | 0,5 ngày |

### P1 — để đề tài đúng nghiệp vụ học thuật

| # | Việc | Ghi chú |
|---|---|---|
| 6 | UC08: duyệt đề xuất + revision + version + trigger | Đây là **đóng góp số 1** của đề án; phải có demo accept/reject có lưu vết |
| 7 | Moodle XML export/import + round-trip 100 câu | Test: export → import → so `content_hash` 100/100 |
| 8 | Port 8 màn còn lại từ HTML mẫu | Ưu tiên: review → tasks → moodle → search; sau đó courses/admin/llm/account |
| 9 | Đồng bộ Chương 4: 1 harness, split DEV/TEST, đa nhãn hoặc sửa văn bản | Chọn 1 trong 2, đừng để lửng |
| 10 | UC06 nối thật (Server 1 → worker `nl_search` → SQL tham số hóa) | Nhấn “mô hình không sinh SQL” |

### P2 — chất lượng & bảo vệ

| # | Việc |
|---|---|
| 11 | Test: backend integration (Testcontainers MariaDB/Redis cho CRUD + accept suggestion + XML round-trip); FE `vitest` cho store; Dart test 6 vector RFC 6238 |
| 12 | `docker-compose.yml` thật cho 5 dịch vụ (hiện `be-web-admin/compose.yaml` bị comment 100%) + healthcheck + `.env.example` đầy đủ |
| 13 | CI GitHub Actions: `./gradlew test` (2 server) + `npm ci && npm run build` (FE) + `flutter test` |
| 14 | Bỏ Moodle source khỏi Git (hoặc submodule); dọn `python_demo_llm/runs` nếu không cần |
| 15 | A11y pass cho qbank (combobox, `aria-current`, focus sau chọn) |

---

## 8. Lời khuyên cho phần bảo vệ

**Nên nói** (đúng với code):
- “Hệ thống gồm 2 service Spring Boot độc lập; Server 2 đảm nhiệm suy luận, cô lập rủi ro khi Colab mất kết nối” — nếu chọn kiến trúc (B).
- “TOTP theo RFC 6238, secret mã hóa AES-256-GCM + HKDF, AAD theo admin & key version, chống replay theo time-step, lockout, audit append-only” — **đây là điểm mạnh thật, nên dành 1 slide**.
- “Prompt có JSON Schema, whitelist `issue_type`, `target_field` không cho phép `correct_label`, khử trùng đề xuất, định vị span, tiền kiểm bằng luật” — điểm mạnh thật thứ hai.
- “Đề xuất AI lưu bảng riêng; chỉ khi người duyệt mới sinh revision và đổi câu hỏi” — **nhưng phải làm xong P1#6 trước khi nói**.

**Không nên nói** khi chưa làm:
- “12 chức năng UC01–UC12 vận hành đúng” (hiện 2/12 hoàn chỉnh).
- “Server 1 điều phối tác vụ qua Redis, Server 2 cập nhật kết quả” (hiện không có code nối).
- “300 câu gán nhãn bởi 2 người, kappa ≥ 0,70” (hiện 1000 câu sinh tự động, 1 nhãn).
- “100% tệp Moodle XML nhập thành công” (chưa có code nhập).
- “CRUD p95 ≤ 300 ms với 50 người dùng ảo” (chưa có endpoint CRUD để đo).

**Cách trình bày an toàn**: thêm mục “Phạm vi đã hiện thực / chưa hiện thực” ngay đầu Chương 4, và một mục “Threats to validity” nói thẳng: dữ liệu tổng hợp dễ phát hiện hơn dữ liệu thật; Colab không đảm bảo độ trễ; chưa có đánh giá người dùng thật. Hội đồng thường **thích** sự trung thực này hơn là con số đẹp nhưng không truy vết được.

---

## Phụ lục A — Số liệu kiểm chứng

| Hạng mục | Giá trị |
|---|---|
| `be-web-admin` | 8.493 dòng Java |
| `server-ai-worker` | 7.668 dòng Java |
| `fe_web_admin` | 5.015 dòng Vue + JS |
| `qbank-console-vue3` | 661 dòng Vue + JS |
| `app_mobile` | 9.928 dòng Dart |
| Test có `@Test` (2 server) | 10 file |
| Test Flutter | 1 file |
| Kích thước `php_moodle_503` | **595 MB**, 67.661 file |
| Kích thước `.git` | 123 MB |
| Build qbank | PASS — 37 module, `index.js` 117,39 KB (gzip 45,66), `main.css` 52,51 KB |
| Bảng v4 có code | **10/21** = 7 bảng auth (`ntt_admins`, `ntt_courses`, `ntt_backup_codes`, `ntt_web_sessions`, `ntt_mobile_devices`, `ntt_totp_credentials`, `ntt_auth_audit_logs`) + 3 bảng AI (`ntt_ai_tasks`, `ntt_ai_suggestions`, `ntt_issue_types`). 3 bảng `ntt_chat_*` nằm ngoài 21 bảng v4 |

## Phụ lục B — Lệnh đã dùng để kiểm chứng

```bash
git log -1 --format='%H %ad %s'          # 8bc435e69 2026-11-22
find be-web-admin/src -name '*.java'     # không có entity nghiệp vụ
grep -rniE '8082|ai-worker|AiTask' be-web-admin/src   # 0 kết quả
grep -n 'MARIADB_DBS' be-web-admin/.env.example server-ai-worker/.env.example
grep -n 'GRANT|CREATE USER' be-web-admin/sql/2026-11-19-04-grants.sql
grep -rn 'question type=' --include=*.java --include=*.dart .   # chỉ có HTML mẫu
git ls-files fe_web_admin/.env php_moodle_503/moodle-latest-503/moodle/config.php
cd qbank-console-vue3 && npm install && npm run build
```

## Phụ lục C — Checklist rà lại sau khi sửa

- [ ] `grep -r "notYet(" qbank-console-vue3/src` → chỉ còn ở màn chưa làm, không còn ở nút chính
- [ ] Tạo câu hỏi thiếu 1 phương án → 422; đủ 4 + 1 đáp án đúng → 201
- [ ] Chạy AI xong: `validation_status` **không** đổi thành `clean`; chỉ đổi sau khi duyệt
- [ ] Accept suggestion: có dòng mới trong `ntt_question_revisions`, `decision='accepted'`, `decided_by` khác NULL, `applied_revision_id` trỏ đúng
- [ ] Sửa nội dung câu hỏi sau khi đã duyệt: trigger set `stale` và `pending` suggestion cũ chuyển `stale`
- [ ] Export 100 câu → import lại: 100/100, không tạo bản ghi trùng (`content_hash`)
- [ ] `SHOW GRANTS FOR 'ntt_s2'` + thử `UPDATE ntt_questions` → bị từ chối (bằng chứng bảo vệ)
- [ ] `git ls-files | grep -E '(^|/)\.env$|moodle/config\.php'` → rỗng
- [ ] `./gradlew test` (2 server) + `npm run build` + `flutter test` xanh
