# Rà soát Java/Spring Boot — commit `4925cfc77` (UI_v10 + web Vue demo + patch ORM)

- **Ngày**: 2026-11-22 · **Nền rà**: `feature/apply-ui-project-thesis` @ **`4925cfc77`** (3 commit mới so với `8bc435e`: `dbd5a7fc0`, `f66ab499a`, `4925cfc77`), 157 file thay đổi.
- **Vai**: review như một fullstack dev — phần backend (`be-web-admin`, Spring Boot 3.3 / Java 21) là trọng tâm, phần Vue demo chỉ rà ở mức hợp đồng API.
- **Cách rà**: đọc mã tĩnh. Sandbox **không có JDK/Gradle/MariaDB/Redis** nên không chạy được `./gradlew test`; mọi kết luận dưới đây là phân tích mã + kiểm tra cấu trúc tự động.

---

## 0. Xác nhận patch ORM đã vào đúng

| Kiểm tra | Kết quả |
|---|---|
| 14 entity + 22 enum + 22 converter + 13 repository + 5 controller có mặt | ✅ |
| `be-web-admin/scripts/check-orm-mapping.py` | ✅ `DDL: 17 bảng | entity: 14 bảng — OK` |
| File `.rej` / `.orig` sót lại | ✅ không có |
| Mapping trùng đường dẫn (quét toàn bộ 52 mapping) | ✅ 0 trùng |
| `docs/` + `project-design/UI_Accept_v10.html` | ✅ có |

Tức là phần ORM/Redis đã nằm gọn trong cây, không có dấu hiệu áp thiếu.

---

## 1. P0-1 — Spring Data REST vẫn bật: mọi repository cũ bị xuất thành API CRUD

`build.gradle.kts:39` vẫn có `spring-boot-starter-data-rest`, và **chưa** có `spring.data.rest.detection-strategy`. Hệ quả: Spring Data REST tự dựng endpoint cho **mọi** repository public, kể cả 5 repository cũ **không** có `@RepositoryRestResource`:

| Entity | Endpoint tự sinh | Rủi ro |
|---|---|---|
| `Admin` | `/api/admins` | **POST tạo tài khoản có `role`, `accountStatus` tuỳ ý** → tự cấp quyền ADMIN; PUT đổi `password` (đã băm) của người khác |
| `TotpCredential` | `/api/totpCredentials` | Sửa/xoá credential 2FA của người khác → vô hiệu hoá 2FA |
| `BackupCode` | `/api/backupCodes` | Đọc/ghi mã dự phòng |
| `AuthAuditLog` | `/api/authAuditLogs` | **Xoá dấu vết** đăng nhập thất bại |
| `Course` | `/api/courses` | Sửa danh mục môn học |

Chúng chỉ được chặn bởi `anyRequest().authenticated()`, nên **bất kỳ tài khoản đã đăng nhập** (kể cả LECTURER) đều gọi được — vòng qua service, qua kiểm tra dữ liệu và qua audit. 14 repository mới của tôi đã gắn `@RepositoryRestResource(exported = false)` nên phần ngân hàng câu hỏi an toàn, nhưng 5 cái cũ thì không.

**Đã vá trong patch kèm theo**: thêm `spring.data.rest.detection-strategy: annotated` (chỉ xuất repository có khai báo tường minh → hiện không còn cái nào). Đây là bản vá 1 dòng cấu hình, không đổi mã.

**Bản bền vững** là gỡ hẳn starter như nhánh `feature/thesis-qbank-v9` đã làm (kèm `NoSpringDataRestTest`).

---

## 2. P0-2 — Nhánh này chưa có các bản vá P0 của `feature/thesis-qbank-v9`

Cây hiện tại dựng trên `8bc435e`, tức là **thiếu** toàn bộ nhóm vá an toàn đã làm ở v9 (`88e40f4`):

| Thiếu gì | Hệ quả trên cây hiện tại |
|---|---|
| `@EnableMethodSecurity` + `RoleAuthorities` | Không có phân quyền theo vai: mọi API chỉ cần đăng nhập. 19 endpoint ngân hàng câu hỏi (kể cả `POST {id}/ai-suggestions/{id}/decision` duyệt đề xuất AI, `archive`) **giảng viên nào cũng gọi được** |
| `LoginAttemptLimiter` + `RedisAttemptCounterStore` | Không giới hạn đoán mật khẩu → brute force thoải mái |
| Kiểm `AccountStatus.blocksLogin()` | Tài khoản bị khoá vẫn đăng nhập được |
| Gỡ `spring-boot-starter-data-rest` | Chính là P0-1 ở trên |

**Đề nghị**: merge `feature/thesis-qbank-v9` vào nhánh này **trước** khi làm tiếp giao diện, rồi áp patch ORM **v2** (bản đã bỏ `@RepositoryRestResource` và có `@PreAuthorize("hasAnyRole('ADMIN','LECTURER')"))` thay cho bản v1 đang nằm trong cây. Nếu merge sau, sẽ phải sửa lại 14 annotation và 4 controller bằng tay.

---

## 3. P1-1 — Patch vá API (bộ xử lý lỗi) chưa được áp

Trong cây **không có** `config/ApiExceptionHandler.java`, `AdminLoginDTO` không có ràng buộc, `SecurityConfig` không `permitAll` cho `DispatcherType.ERROR`. Nghĩa là các lỗi đã nêu ở `docs/review-api-2026-11-22.md` vẫn còn: lỗi 400/404/405 ở endpoint công khai bị đổi thành **401**, body lỗi không theo một khuôn.

Áp `project-thesis-ntt-api-hardening-base-8bc435e-2026-11-22.patch` (đúng bản cho nền `8bc435e` này) là xong.

---

## 4. P1-2 — Lệch tên trường giữa frontend và backend khi cập nhật câu hỏi

- Frontend gửi: `{ ..., version }`
- Backend nhận: `UpdateQuestionRequest.expectedVersion`

Jackson bỏ qua `version` (không có alias), rồi `@NotNull expectedVersion` → **400** ngay khi ghép giao diện thật. Đã vá bằng `@JsonAlias({"version"})` để nhận cả hai tên, giữ nguyên API cũ cho client khác.

---

## 5. P2 — Nên thống nhất, chưa vá vì phải sửa cả hai phía

1. **Sai đường dẫn trong demo**: `QuestionDialog.vue` ghi "gửi Server 1 (`POST /api/questions`)" nhưng backend là `POST /api/web/admin/questions` (context-path `/api` + `/web/admin`). Khi ghép, dùng đúng đường dẫn của backend và **không** tạo alias `/api/questions` (dễ mở đường vòng qua phân quyền).
2. **Thang độ khó lệch**: demo dùng `LV_DB = [1,2,3]` ("Dễ/Trung bình/Khó"), DDL `chk_q_difficulty CHECK (1..5)` và DTO `@Min(1) @Max(5)`. Backend đang đúng theo DDL; **demo phải sửa** (hoặc chốt lại DDL nếu chỉ dùng 3 mức — nhưng đó là thay đổi CSDL, không phải vá code).
3. **Trường dư bị bỏ qua im lặng**: demo gửi `topicName`, `status`, `validationStatus`, `source`; DTO không khai báo nên Jackson bỏ qua. Hiện backend đặt mặc định đúng ý demo (`ACTIVE`, `NOT_CHECKED`, `MANUAL`) nên chưa lỗi, nhưng lỗi chính tả tên trường sẽ **không bị phát hiện**. Khi chốt hợp đồng, bật `spring.jackson.deserialization.fail-on-unknown-properties: true` — chỉ bật **sau khi** FE đã gửi đúng tập trường.
4. **Vue demo chưa có tầng gọi API**: không `axios`, không `VITE_*`, không proxy trong `vite.config.js`; `repositories/questionRepository.js` vẫn là mock (có ghi chú "sau này đổi thân hàm"). Đúng như bạn nói — đây là demo. Kế hoạch ghép nằm ở `docs/plan-merge-qbank-vao-fe-web-admin.md`.
5. **Phiên bản khoá lạc quan**: `ntt_questions.version` có `@Version`, và API update nhận `expectedVersion`. FE phải gửi lại đúng `version` nhận được ở lần đọc gần nhất, nếu không sẽ nhận 409 `VERSION_CONFLICT` — cần nói rõ trong tài liệu API cho FE (đã ghi ở `docs/api-qbank-redis.md`).

---

## 6. P3 — Ghi nhận

1. `@PreAuthorize` chưa dùng được trên nền này (chưa `@EnableMethodSecurity`) — sẽ có ngay khi merge v9.
2. Toàn bộ controller ORM mới trả `ResponseEntity<...>` cụ thể (tốt hơn `ResponseEntity<?>` cũ), nhưng 9 controller cũ vẫn `ResponseEntity<?>` → chưa sinh được OpenAPI.
3. `QuestionService` có `@Transactional` đúng chỗ (create/update/archive/decision là `@Transactional`, các hàm đọc `readOnly = true`) ✓.
4. `UiStateStore` dùng `StringRedisTemplate` (bean có sẵn) ✓, mọi thao tác Redis đều fail-open nên Redis chết không làm sập API ✓.
5. Không có `.env` bị commit; `be-web-admin/.env.example` chỉ chứa tên biến ✓.

---

## 7. Áp patch này

```bash
git switch feature/apply-ui-project-thesis
git log --oneline -1                       # 4925cfc77
git apply --check project-thesis-ntt-ui-v10-fixes-2026-11-22.patch
git apply         project-thesis-ntt-ui-v10-fixes-2026-11-22.patch
```

Nội dung: `application.yaml` (tắt auto-export), `UpdateQuestionRequest` (`@JsonAlias`), tài liệu này.

Kiểm chứng sau khi chạy server:

| # | Lệnh | Kỳ vọng |
|---|---|---|
| 1 | `curl -i localhost:8080/api/admins -H "Authorization: Bearer <token>"` | **404** (trước: 200 + danh sách admin) |
| 2 | `curl -i -X POST localhost:8080/api/admins ...` | **404** (trước: tạo được admin) |
| 3 | `PUT /api/web/admin/questions/{id}` với `{"version":1,...}` | không còn 400 "expectedVersion"; lệch version → 409 `VERSION_CONFLICT` |
| 4 | `PUT` với `{"expectedVersion":1,...}` | vẫn chạy (tương thích cũ) |

---

## 8. Thứ tự đề nghị cho các bước tiếp theo

1. **Merge `feature/thesis-qbank-v9`** (bản vá P0: phân quyền theo vai, khoá đoán mật khẩu, gỡ Data REST) — làm trước mọi việc khác.
2. Áp **patch ORM v2** + **patch hardening bản `88e40f4`** sau khi merge (bản v1 đang trong cây sẽ phải thay).
3. Ghép Vue demo vào `fe_web_admin` theo `docs/plan-merge-qbank-vao-fe-web-admin.md`, sửa 3 điểm hợp đồng ở §5.1–§5.2.
4. Bổ sung API còn thiếu cho 4 màn demo đang giả lập: tìm kiếm NLP, Moodle XML, cài đặt LLM, quản trị tài khoản.
