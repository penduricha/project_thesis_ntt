# Rà soát Spring Boot (`be-web-admin`) — tính năng cần vá

> Rà trực tiếp trên mã nguồn, kèm đường dẫn và số dòng. Ngày 2026-11-23.
> Phạm vi: `be-web-admin/src/main/java/com/example/be_web_admin/` và
> `be-web-admin/sql/2026-11-19-02-business-tables.sql`.
> Không bao gồm `php_moodle_503/` (đã có tài liệu riêng:
> `docs/review-moodle-quiz-vs-spring-boot.md`).

---

## 0. Kết luận ngắn

Phần **nghiệp vụ câu hỏi** (CRUD, khoá lạc quan, revision, khoá chống gửi trùng AI, phân trang)
làm khá chắc: có `@Version`, có `saveAndFlush` trước khi lấy ID, có `tryLockCheck`, có
`MAX_PAGE_SIZE = 100`, `topicId` được kiểm tra thuộc đúng môn.

Vấn đề **không nằm ở nghiệp vụ câu hỏi mà ở 4 tầng bao quanh**:

| # | Vấn đề | Mức | Loại |
|---|---|---|---|
| **1** | Mọi token đều được cấp `ROLE_ADMIN` (`JwtAuthenticationFilter:52`), không phân quyền theo vai trò, và không có phân công môn | 🔴 Nghiêm trọng | Bảo mật |
| **2** | Trùng nội dung (`content_hash`) chỉ ghi log, không báo cho người dùng | 🟠 Cao | Nghiệp vụ |
| **3** | Luồng Moodle chỉ có entity + repository, không có service/controller | 🟠 Cao | Tính năng thiếu |
| **4** | Không có kiểm thử tích hợp cho tầng API | 🟡 Trung bình | Chất lượng |

---

## 1. 🔴 Không phân quyền theo vai trò

### 1.1 Hiện trạng

`security/SecurityConfig.java:47-63` chỉ có hai mức: **hoặc** là endpoint công khai (login,
refresh token, OPTIONS), **hoặc** `anyRequest().authenticated()`.

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers(HttpMethod.POST, "/web/admin/auth/login", ...).permitAll()
        .requestMatchers(HttpMethod.GET, ".../auto/access-token", ...).permitAll()
        .anyRequest().authenticated()          // <- chỉ cần ĐÃ ĐĂNG NHẬP
)
```

Không có `hasRole(...)`, không có `@PreAuthorize` ở bất kỳ controller nào:

```bash
grep -rn "PreAuthorize\|hasRole\|hasAuthority" be-web-admin/src/main/java/
# → không có kết quả
```

Và `Admin` **có** phân biệt vai trò:

```java
model/jpa/Admin.java:54-55
@Column(length = 12, name = "role", nullable = false)
private Role role = Role.ADMIN;
```

### 1.2 ⚠️ Nghiêm trọng hơn: mọi token đều được cấp `ROLE_ADMIN`

`security/jwt/JwtAuthenticationFilter.java:49-52` gán **cứng** một authority duy nhất, không
đọc vai trò thật từ token hay từ DB:

```java
Long adminId = tokenProvider.parseAccessTokenAdminId(token);
...
SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(
                adminId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));   // <- hằng số
```

Hệ quả kép:

1. Vai trò `LECTURER` **bị nâng thành** `ROLE_ADMIN` ngay khi qua filter.
2. Nếu chỉ thêm `@PreAuthorize("hasRole('ADMIN')")` mà không sửa chỗ này, thì **nó không chặn
   được ai cả** — vì ai đăng nhập xong cũng mang `ROLE_ADMIN`. Đây là cái bẫy: sửa nửa vời còn
   tệ hơn, vì tưởng đã có phân quyền mà thực tế không có.

**Phải sửa filter trước**, bằng cách lấy vai trò thật từ DB.

Dữ kiện đã kiểm chứng: `Role` enum có đúng 2 giá trị `ADMIN`, `LECTURER`
(`enums/Role.java:4-5`), và **JWT không mang claim `role`** — token chỉ có
`token_type` + `subject`
(`AdminJwtTokenProvider.java:97` chỉ tạo `.claim("token_type", "ACCESS")`). Vậy không thể đọc
vai trò từ token, buộc phải tra DB (hoặc thêm claim — xem ghi chú dưới).

```java
Long adminId = tokenProvider.parseAccessTokenAdminId(token);
...
if (adminTokenService.findAdminAccessTokenByJwtToken(token)) {
    // Tra vai trò THẬT. Có thể cache ngắn hạn (vài giây) nếu thấy tốn query,
    // luồng này đã đi Redis một lần rồi nên thêm 1 SELECT theo khoá chính là chấp nhận được.
    Role role = adminRepository.findRoleByAdminId(adminId);   // ADMIN | LECTURER
    SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                    adminId,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
}
```

**Cách khác (nhanh hơn nhưng có đánh đổi):** thêm `.claim("role", role)` vào
`createAccessToken` rồi đọc từ token — bớt 1 query mỗi request. Nhưng khi **hạ quyền** một tài
khoản (ADMIN → LECTURER), quyền cũ vẫn còn hiệu lực tới khi access token hết hạn. Với đồ án
thì chấp nhận được nếu ghi rõ trong báo cáo; tra DB là lựa chọn an toàn hơn và đơn giản hơn để
giải thích trước hội đồng.

### 1.3 Hệ quả cụ thể

Xác thực JWT thành công là có **toàn quyền**. Tài khoản vai trò `LECTURER` gọi được:

| Endpoint | Tệp · dòng | Hậu quả |
|---|---|---|
| `POST/PUT/DELETE /web/admin/admins/**` | `AdminControllerMgmtWeb.java` | Tạo/sửa/xoá tài khoản, đổi vai trò, cấp lại mật khẩu |
| `GET/PUT /web/admin/enrollments/**` | `EnrollmentController.java` | Sửa dữ liệu tuyển sinh |
| `POST /web/admin/questions/{id}/ai-check` | `QuestionBankController.java:112` | Đốt hạn mức gọi Server 2 (tốn tiền/tài nguyên) |
| `GET/PUT /mobile/admin/**` | các controller mobile | Toàn bộ API mobile |

> Đây là lỗ hổng **phân quyền dọc** (privilege escalation): người dùng cấp thấp làm được việc
> của cấp cao. Trong đồ án, hội đồng rất dễ phát hiện: chỉ cần đăng nhập tài khoản giảng viên
> rồi mở DevTools gọi `DELETE /web/admin/admins/{id}` là xoá được tài khoản admin.

### 1.4 Cần vá

**Bước 1** — bật method security và dùng vai trò của chính JWT:

```java
@Configuration
@EnableMethodSecurity            // <-- thêm vào SecurityConfig
public class SecurityConfig { ... }
```

**Bước 2** — gắn `@PreAuthorize` cho các nhóm quản trị:

```java
// AdminControllerMgmtWeb
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/web/admin/admins")
public class AdminControllerMgmtWeb { ... }

// EnrollmentController, các controller mobile
@PreAuthorize("hasRole('ADMIN')")
```

**Bước 3 (LÀM TRƯỚC HAI BƯỚC KIA)** — sửa `JwtAuthenticationFilter` để cấp authority theo
vai trò **thật** thay vì hằng số `ROLE_ADMIN` (xem §1.2). Không sửa bước này thì `@PreAuthorize`
ở bước 1–2 vô nghĩa.

---

## 2. 🔴 Không phân quyền theo môn

### 2.1 Hiện trạng

Giao diện **có** lọc theo môn được phân công:

```
qbank-console-vue3/src/store.js:534-535
export const myScope = () => (S.role === 'admin' ? null : new Set(asUser()?.subjects || []))
export const subjOk = ci => { const sc = myScope(); return !sc || sc.has(COURSES[ci]?.id) }
```

Nhưng đó là lọc **phía client**, dựa trên `asUser()?.subjects` — dữ liệu **mock**
(`src/data/mock.js`). Ba vấn đề:

1. **Backend không có khái niệm phân công môn.** Không có bảng nối giảng viên ↔ môn trong
   `sql/2026-11-19-02-business-tables.sql`; `Admin` không có trường môn.
2. **Không controller/service nào kiểm tra** — `grep -rn "assignedSubject\|hasSubject"` trên
   `service/qbank/` và `controller/web/` không có kết quả.
3. Vì vậy chỉ cần gọi API trực tiếp (`PUT /web/admin/questions/{id}` với id của môn bất kỳ)
   là sửa được, bất kể giao diện có ẩn nút hay không.

### 2.2 Đây không chỉ là lỗi bảo mật mà là **tính năng còn thiếu**

UI đã giả định có phân công môn; backend chưa có bảng để lưu. Cần:

```sql
-- Bảng phân công: một giảng viên phụ trách nhiều môn, một môn có nhiều giảng viên
CREATE TABLE IF NOT EXISTS ntt_subject_lecturers (
    subject_id BIGINT NOT NULL,
    admin_id   BIGINT NOT NULL,
    assigned_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    assigned_by BIGINT NULL,
    PRIMARY KEY (subject_id, admin_id),
    KEY ix_sl_admin (admin_id, subject_id),
    CONSTRAINT fk_sl_subject FOREIGN KEY (subject_id) REFERENCES ntt_subjects (subject_id),
    CONSTRAINT fk_sl_admin   FOREIGN KEY (admin_id)   REFERENCES ntt_admins (admin_id)
) ENGINE=InnoDB;
```

rồi thêm kiểm tra ở tầng service (không phải controller, để mọi lối gọi đều đi qua):

```java
private void assertSubjectAccess(Long adminId, Long subjectId) {
    if (isAdmin(adminId)) return;                       // ADMIN: toàn quyền
    if (!assignments.existsByAdminIdAndSubjectId(adminId, subjectId)) {
        throw QbankException.forbidden("SUBJECT_FORBIDDEN",
                "Bạn không được phân công môn học này.");
    }
}
```

Gọi trong `create`, `update`, `archive`, `AiCheckService.submit` — và quan trọng hơn:
**lọc danh sách** (`list`) theo môn được phân công, nếu không giảng viên vẫn *đọc* được câu
hỏi của môn khác dù không sửa được.

> ⚠️ Nếu đồ án quyết định **không** làm phân công môn (cho đơn giản), thì phải **gỡ `subjOk`
> khỏi giao diện** — để nguyên sẽ là "hàng rào giấy": giao diện tỏ ra có phân quyền nhưng
> thực tế không có, dễ bị hỏi khó khi bảo vệ.

---

## 3. 🟠 Trùng nội dung chỉ ghi log

### 3.1 Hiện trạng

`QuestionService.java:217` tính `duplicate`, nhưng `:266-269` **chỉ ghi log**:

```java
if (duplicate) {
    log.info("Câu hỏi #{} trùng nội dung với câu đã có trong môn {}", ...);
}
return toDetail(saved, subject, topic, 1, 0);      // không trả cờ gì
```

`QuestionDetail` (`dto/qbank/QuestionDetail.java:5-30`) không có trường nào báo trùng.

### 3.2 Hệ quả

Người dùng tạo câu trùng nội dung với câu đã có (cùng môn, cùng 4 phương án, cùng đáp án) và
**không nhận được bất kỳ dấu hiệu nào**. Hệ thống biết (đã tính hash, đã ghi log) nhưng im lặng.

Đồ án đã có sẵn hai thứ để hiển thị việc này:
* cột `ntt_questions.content_hash` + index `ix_question_hash` (không UNIQUE — đúng, vì trùng
  nội dung là **cảnh báo** cho người biên soạn chứ không phải lỗi chặn),
* UI đã có khái niệm **cảnh báo mềm** (dùng cho câu dẫn trùng).

### 3.3 Cần vá

Thêm **một** trường vào response là đủ, không cần chặn:

```java
// QuestionDetail: thêm
int duplicateCount        // số câu khác trong cùng môn có cùng content_hash (0 = không trùng)

// QuestionService.toDetail(): tính thêm
long duplicates = questions.countBySubjectIdAndContentHashAndQuestionIdNot(
        q.getSubjectId(), q.getContentHash(), q.getQuestionId());
```

UI hiện cảnh báo mềm sau khi lưu: *"Nội dung câu này trùng với N câu đã có trong môn."*

**Không nên** chặn bằng lỗi 409: hai câu có cùng nội dung có thể là chủ ý (ví dụ cùng một câu
dùng cho hai chương khác nhau). Đây là cảnh báo, không phải lỗi — giống cách đang xử lý
`dupStem`.

---

## 4. 🟠 Luồng Moodle chưa có tầng nghiệp vụ

Đã phân tích chi tiết trong `docs/review-moodle-quiz-vs-spring-boot.md` §2. Tóm tắt: có 3 entity
(`MoodleProfile`, `MoodleExchange`, `MoodleExchangeItem`) + 3 repository, **không có service,
không có controller**. Màn `MoodleView.vue` chạy mock vì phía sau không có endpoint.

Cần: `MoodleExchangeService` + `MoodleController` (`/web/admin/moodle`), và bổ sung field khi
xuất XML (`layout`, `single`, `shuffleanswers`, `fraction` từng đáp án, `penalty`, `defaultmark`,
`qtype`; sinh `name` thay thế khi `title` NULL).

---

## 5. 🟡 Không có kiểm thử tầng API

`src/test/java/` có 6 tệp: `AdminJwtTokenProviderTest`, `EnumDbValueTest`,
`QuestionContentHasherTest`, `ApiPathConventionTest`, `BeWebAdminApplicationTests`,
`DataInitializerTest`.

Đều là **unit test thuần** (không dựng Spring context) hoặc smoke test. **Không có** test nào
kiểm chứng hợp đồng HTTP: mã trạng thái, hình dạng JSON, hay các quy tắc nghiệp vụ vừa thêm
(`TITLE_DUPLICATED`, `VERSION_CONFLICT`, `QUESTION_ARCHIVED`).

Hệ quả thực tế: ba vòng sửa gần đây (bao gồm cả lỗi `question_id` NULL làm ứng dụng không khởi
động được) **không thể phát hiện bằng test** — đều phải chạy tay mới thấy.

Đề xuất tối thiểu 3 test (không cần Docker, dùng `@WebMvcTest` + mock service):

| Test | Kiểm chứng |
|---|---|
| `QuestionBankControllerTest` | `PUT` thiếu `expectedVersion` → 400; `version` lệch → 409 `VERSION_CONFLICT` |
| `QuestionTitleRuleTest` | Tạo mới trùng tiêu đề → 409 `TITLE_DUPLICATED`; **sửa** giữ nguyên tiêu đề trùng → **200** (đúng phạm vi quy tắc) |
| `SecurityRoleTest` | `LECTURER` gọi `DELETE /web/admin/admins/{id}` → 403 |

Dòng thứ hai của bảng trên đặc biệt quan trọng: nó là **hồi quy** cho lỗi logic vừa sửa hôm nay
(form Sửa bị chặn oan). Không có test, lỗi này rất dễ tái phát khi ai đó "dọn dẹp" code.

---

## 6. Những điểm đã kiểm tra và **không** có vấn đề

Ghi lại để khỏi phải rà lại, và để trả lời phản biện.

| Hạng mục | Kết luận | Bằng chứng |
|---|---|---|
| Khoá lạc quan | ✅ Đúng | `UpdateQuestionRequest` có `@NotNull Integer expectedVersion`; `QuestionService:279` so với DB, lệch → 409 `VERSION_CONFLICT` |
| `isNew()` với `@Version` | ✅ Đã sửa | Bỏ `setVersion(0)`; dùng `saveAndFlush` rồi lấy ID trả về (commit `03bcce60b`) |
| Xoá | ✅ Mềm | `DELETE /questions/{id}` → `archive()` đặt `ARCHIVED`, không `deleteById` |
| Sửa câu đã lưu trữ | ✅ Chặn | `QUESTION_ARCHIVED` 409 ở `update()` và `AiCheckService.submit()` |
| Phân trang | ✅ Có trần | `Math.min(Math.max(1, size), MAX_PAGE_SIZE)` với `MAX_PAGE_SIZE = 100` |
| N+1 khi tải danh sách | ✅ Batch | `loadSubjects` / `loadTopics` nạp 1 lần cho cả trang |
| Chống gửi trùng AI | ✅ Có | `tryLockCheck(questionId)` + tìm task đang chạy trước; `finally` mở khoá nếu gửi lỗi |
| Timeout gọi worker | ✅ Có | `connect-timeout-ms:3000`, `read-timeout-ms:20000` |
| `topicId` khác môn | ✅ Chặn | `resolveTopic(subjectId, topicId)`; CSDL còn FK ghép `(subject_id, topic_id)` |
| Môn bị vô hiệu hoá | ✅ Chặn | `SUBJECT_INACTIVE` ở `create()` |
| `revNo` duy nhất | ✅ Có ràng buộc | `UNIQUE KEY uq_rev (question_id, rev_no)` trong DDL là mức bảo vệ cuối |
| Trùng tiêu đề khi sửa | ✅ Đã sửa | Chỉ kiểm tra trong `create()` (commit `3d36e3008`) |

### Một lưu ý nhỏ về `revNo`

`QuestionService:325` và `:356` tính `revNo = revisions.findMaxRevNo(questionId) + 1` — đọc rồi
ghi, không khoá. Hai request đồng thời cùng lưu một câu có thể cùng nhận `revNo = N`; khi đó
`UNIQUE KEY uq_rev` sẽ chặn và trả lỗi 500 khó hiểu.

Xác suất thấp (cần hai người lưu cùng một câu trong cùng khoảng mili giây, và `@Version` ở bảng
`ntt_questions` **đã** chặn phần lớn trường hợp trước khi tới bước này). Nhưng nếu muốn chắc:
bắt `DataIntegrityViolationException` và đổi thành `QbankException.conflict("REVISION_CONFLICT", …)`
— rẻ hơn nhiều so với khoá bi quan.

---

## 7. Thứ tự thi công đề xuất

| Bước | Việc | Công sức | Vì sao ưu tiên này |
|---|---|---|---|
| **1a** | Sửa `JwtAuthenticationFilter`: cấp `ROLE_<vai trò thật>` thay cho hằng số `ROLE_ADMIN` | Rất nhỏ (vài dòng) | **Điều kiện tiên quyết** — không sửa thì mọi `@PreAuthorize` đều vô hiệu |
| **1b** | `@EnableMethodSecurity` + `@PreAuthorize("hasRole('ADMIN')")` cho `AdminControllerMgmtWeb`, `EnrollmentController`, controller mobile | Nhỏ (làm được trong 1 buổi) | Lỗ hổng leo thang đặc quyền — nghiêm trọng nhất, sửa rẻ nhất |
| **2** | Cảnh báo trùng nội dung (`duplicateCount` trong `QuestionDetail`) | Nhỏ | Đã có hash sẵn, chỉ cần trả ra và hiện toast |
| **3** | Bảng `ntt_subject_lecturers` + `assertSubjectAccess` + lọc `list` | Vừa | Cần thiết kế CSDL, ảnh hưởng nhiều chỗ |
| **4** | 3 test ở §5 | Vừa | Chặn hồi quy cho các quy tắc vừa thêm |
| **5** | `MoodleExchangeService` + `MoodleController` | Lớn | Xem tài liệu Moodle riêng |

> Nếu thời gian eo hẹp: làm **1 + 2 + test hồi quy cho quy tắc tiêu đề**. Ba việc đó sửa được
> lỗ hổng nghiêm trọng nhất, khép kín được một tính năng đang dở (cảnh báo trùng), và bảo vệ
> các quy tắc vừa thêm khỏi bị phá khi sửa code sau này.
