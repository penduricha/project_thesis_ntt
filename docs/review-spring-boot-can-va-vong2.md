# Review source code Spring Boot (`be-web-admin`) — vòng 2

> Rà trực tiếp trên nhánh `feature/apply-ui-project-thesis` @ `c82f0686c` (bản mới nhất sau khi merge).
> Ngày 2026-11-24. Không phải đọc lại tài liệu cũ — mọi kết luận dưới đây đều kiểm chứng lại trên code hiện tại.
> Tài liệu vòng 1: `docs/review-spring-boot-can-va.md` (vẫn còn 2 lỗ hổng chưa vá).

---

## 0. Kết luận ngắn

Đã rà **tầng bảo mật, tầng nghiệp vụ qbank, tầng lỗi, tầng cache, luồng đăng nhập/2FA**.
Phần **chất lượng code rất tốt**: xử lý null cẩn thận, chống timing attack khi login, Redis bọc
try/catch, AI worker phân loại lỗi rõ ràng, có audit log.

Nhưng **vẫn còn 6 việc phải vá**, trong đó **2 việc là lỗ hổng bảo mật từ vòng 1 chưa được xử lý**:

| # | Vấn đề | Mức | Trạng thái |
|---|---|---|---|
| **1** | Mọi token đều mang `ROLE_ADMIN`; 0 `@PreAuthorize` | 🔴 Nghiêm trọng | **Chưa vá** |
| **2** | Không phân công môn ở backend (UI lọc bằng dữ liệu mock) | 🔴 Nghiêm trọng | **Chưa vá** |
| **3** | `job(taskId)` / `suggestions(taskId)` không kiểm tra chủ sở hữu | 🟠 Cao | **Mới phát hiện** |
| **4** | Không có chức năng khôi phục câu hỏi đã lưu trữ (hằng `RESTORE` có sẵn nhưng chưa dùng) | 🟠 Cao | **Mới phát hiện** |
| **5** | Đăng nhập không giới hạn số lần thử **mật khẩu** (chỉ TOTP bị khoá) | 🟠 Cao | **Mới phát hiện** |
| **6** | `update()` thiếu kiểm tra môn còn hoạt động | 🟡 Trung bình | **Mới phát hiện** |

Ngoài ra **2 vấn đề hiệu năng** không chặn nhưng nên biết (§7), và **3 điểm làm tốt** cần ghi
nhận trong báo cáo đồ án (§8).

---

## 1. 🔴 Mọi token đều mang `ROLE_ADMIN` — CHƯA VÁ

```java
// security/jwt/JwtAuthenticationFilter.java:52
List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
```

Kiểm chứng lại trên `c82f0686c`:

```bash
grep -rn "PreAuthorize\|EnableMethodSecurity\|hasRole" src/main/java/ | wc -l
# → 0
```

Cộng với `SecurityConfig:61` chỉ có `anyRequest().authenticated()`, kết quả: **tài khoản
`LECTURER` có toàn quyền như `ADMIN`** — xoá tài khoản, sửa tuyển sinh, gọi API mobile.

> Đây là vấn đề **lặp lại từ vòng 1**. Nếu không có thời gian làm hết, chỉ cần sửa đúng **1 dòng**
> ở filter + `@EnableMethodSecurity` + `@PreAuthorize("hasRole('ADMIN')")` cho 2 controller
> `AdminControllerMgmtWeb` và `EnrollmentController` là đã bịt được đường leo thang đặc quyền
> rõ ràng nhất.

---

## 2. 🔴 Không phân công môn ở backend — CHƯA VÁ

Kiểm chứng lại:

```bash
grep -rn "assignedSubject\|hasSubject\|LECTURER" service/qbank/ controller/web/QuestionBankController.java
# → không có kết quả
grep -c "ntt_subject_lecturers" sql/2026-11-19-02-business-tables.sql
# → 0
```

UI (`store.js:534-535`) vẫn lọc bằng `myScope()` lấy từ **mock** (`asUser()?.subjects`). Backend
không có bảng nối giảng viên–môn ⇒ gọi API trực tiếp sửa được câu của môn bất kỳ.

Chi tiết DDL đề xuất ở `docs/review-spring-boot-can-va.md` §2.

---

## 3. 🟠 MỚI: `job()` và `suggestions()` không kiểm tra chủ sở hữu

### Hiện trạng

```java
// AiCheckService.java:148
public AiJobView job(String taskId) {          // ← KHÔNG có tham số adminId
    ...
    AiTask task = tasks.findById(taskId)        // ← tra thẳng theo id
            .orElseThrow(...);
    ...
}

// AiCheckService.java:167
public List<AiSuggestionView> suggestions(String taskId) {   // ← cũng không có adminId
```

Và controller cũng không truyền:

```java
// AiJobController.java:40-48
@GetMapping("/jobs/{taskId}")
public AiJobView job(@PathVariable("taskId") String taskId) {
    return aiCheckService.job(taskId);           // ← không lấy Authentication
}
```

### Hệ quả

`taskId` là UUID nên khó đoán, nhưng **bất kỳ ai đã đăng nhập** (kể cả `LECTURER` của môn khác)
có được một `taskId` — qua link chia sẻ, log, hoặc response cũ — đều đọc được **đề xuất AI của
câu hỏi môn khác**, gồm cả nội dung câu hỏi và đáp án đúng.

Trong khi đó `pushActivity` có ghi `adminId` (`AiCheckService:127`), tức là **hệ thống có biết ai
tạo task** — chỉ là không dùng để kiểm tra.

### Cần vá

```java
// AiCheckService
public AiJobView job(String taskId, Long adminId) {
    ...
    AiTask task = tasks.findById(taskId).orElseThrow(...);
    if (task.getCreatedBy() != null && !Objects.equals(task.getCreatedBy(), adminId)
            && !isAdministrator(adminId)) {
        throw QbankException.forbidden("TASK_FORBIDDEN",
                "Task này không thuộc về bạn.");
    }
    ...
}
```

Và controller:

```java
@GetMapping("/jobs/{taskId}")
public AiJobView job(@PathVariable("taskId") String taskId, Authentication auth) {
    return aiCheckService.job(taskId, WebPrincipal.adminId(auth));
}
```

> Lưu ý: **không** nên chặn tuyệt đối theo `createdBy` nếu đồ án muốn cho giáo vụ xem tiến trình
> của mọi người — hãy cho `ADMIN` xem tất cả, `LECTURER` chỉ xem của mình. Nhưng phải ghi rõ quyết
> định này.

---

## 4. 🟠 MỚI: Không có chức năng khôi phục câu hỏi đã lưu trữ

### Hiện trạng

`archive()` đặt `status = ARCHIVED` và **không có đường quay lại**:

```java
// QuestionService.java:344-370
@Transactional
public QuestionDetail archive(Long questionId, Long adminId) {
    ...
    question.setStatus(QuestionStatusD.ARCHIVED);
    ...
}
```

Kiểm tra toàn bộ API:

```bash
grep -rn "restore\|unarchive" controller/web/QuestionBankController.java service/qbank/QuestionService.java
# → chỉ có SUBJECT_INACTIVE ở create(), không có restore
```

Và `update()` chặn sửa câu đã lưu trữ:

```java
// QuestionService.java:276-278
if (question.getStatus() == QuestionStatusD.ARCHIVED) {
    throw QbankException.conflict("QUESTION_ARCHIVED", "Câu hỏi đã bị lưu trữ, không sửa được.");
}
```

### Hệ quả

Người dùng **lỡ tay bấm "Lưu trữ" là mất câu hỏi vĩnh viễn** — không sửa được, không dùng lại
được, không khôi phục được trên giao diện. Dữ liệu vẫn còn trong DB nhưng coi như bỏ.

Giao diện còn có nút **"Lưu trữ hàng loạt"** (chọn nhiều câu rồi lưu trữ) — một lần bấm sai là
mất cả loạt.

### Cần vá

Thêm 1 endpoint:

```java
// QuestionBankController
@PostMapping("/{questionId}/restore")
public QuestionDetail restore(@PathVariable("questionId") Long questionId,
                             Authentication auth) {
    return questionService.restore(questionId, WebPrincipal.adminId(auth));
}

// QuestionService
@Transactional
public QuestionDetail restore(Long questionId, Long adminId) {
    Question question = requireQuestion(questionId);
    if (question.getStatus() != QuestionStatusD.ARCHIVED) {
        throw QbankException.conflict("NOT_ARCHIVED", "Câu hỏi không ở trạng thái lưu trữ.");
    }
    // Không khôi phục được nếu môn đã bị vô hiệu hoá.
    Subject subject = subjects.findById(question.getSubjectId())
            .orElseThrow(() -> QbankException.notFound("SUBJECT_NOT_FOUND", "Môn học không còn."));
    if (!Boolean.TRUE.equals(subject.getIsActive())) {
        throw QbankException.badRequest("SUBJECT_INACTIVE",
                "Môn học đang bị vô hiệu hoá, không khôi phục được câu hỏi.");
    }
    question.setStatus(QuestionStatusD.ACTIVE);
    question.setUpdatedBy(adminId);
    questions.saveAndFlush(question);

    int revNo = revisions.findMaxRevNo(questionId) + 1;
    appendRevision(question, revNo, ChangeSourceD.RESTORE, adminId, "Khôi phục câu hỏi", subject, null);
    uiState.evictDashboard(question.getSubjectId());
    ...
}
```

**Chi tiết đáng chú ý:** hằng `ChangeSourceD.RESTORE("restore")` **đã có sẵn** trong enum và
`SQL_TYPE` đã khai báo `'restore'` — nghĩa là **CSDL đã thiết kế sẵn cho việc khôi phục**, chỉ là
chưa ai viết phần cài đặt. Kiểm chứng:

```bash
grep -rn "ChangeSourceD.RESTORE" src/main/java/
# → không có kết quả (enum có hằng nhưng không nơi nào dùng)
```

Vậy không cần sửa enum, chỉ cần viết `restore()` là dùng được ngay.

---

## 5. 🟠 MỚI: Đăng nhập không giới hạn số lần thử mật khẩu

### Hiện trạng — phần TOTP làm rất tốt

`AdminLoginServiceImpl:88-125` có khoá tạm khi nhập sai mã TOTP nhiều lần, với **thiết kế có chủ ý**:

```java
// (c) Khóa tạm khi nhập sai mã nhiều lần. Chỉ tính SAU khi mật khẩu đã đúng,
//     nên người không biết mật khẩu không thể cố ý khóa tài khoản của admin.
```

Đây là chi tiết **rất đáng khen** — chống cả brute-force lẫn tấn công từ chối dịch vụ.

### Nhưng mật khẩu thì không có gì

```java
// AdminLoginServiceImpl:57-66
Admin admin = adminRepository.findAdminByAdminId(adminId);
String storedHash = (admin == null) ? null : admin.getPassword();
boolean passwordOk = mgmtPassword.matches(req.getPassword(), storedHash);
if (admin == null || !passwordOk) {
    return LoginOutcome.of(LoginStatus.BAD_CREDENTIALS);   // ← không đếm, không khoá
}
```

Kiểm chứng:

```bash
grep -rn "loginAttempt\|lockout\|maxAttempts" --include=*.java . | grep -iv enroll
# → không có gì cho mật khẩu
```

### Hệ quả

`adminId` là số nguyên tăng dần và **hiện trên form đăng nhập**. Kẻ tấn công có thể:

1. Thử `adminId` từ 1..N với mật khẩu phổ biến — **không bị chặn lần nào**.
2. Khi tìm được admin có 2FA, sẽ bị khoá 5 phút — nhưng đã biết mật khẩu là đủ để tấn công sau.

BCrypt làm chậm mỗi lần thử, nhưng không chặn được dò vét cạn có kiểm soát.

### Cần vá

Dùng **cùng cơ chế Redis đã có**, chỉ cần thêm một cặp key:

```java
private static final String PWD_FAIL_KEY = "login:pwd_fail:";
private static final int MAX_PWD_FAILS = 10;
private static final Duration PWD_FAIL_WINDOW = Duration.ofMinutes(15);

// sau khi passwordOk == false
String key = PWD_FAIL_KEY + adminId + ":" + ip;    // khoá theo (tài khoản, IP)
Long count = redis.opsForValue().increment(key);
if (count != null && count == 1L) redis.expire(key, PWD_FAIL_WINDOW);
if (count != null && count >= MAX_PWD_FAILS) {
    return LoginOutcome.locked(PWD_FAIL_WINDOW.toSeconds());
}
return LoginOutcome.of(LoginStatus.BAD_CREDENTIALS);
```

> Khoá theo **cặp (tài khoản, IP)** chứ không chỉ theo tài khoản — nếu chỉ theo tài khoản, kẻ tấn
> công có thể cố ý nhập sai để khoá tài khoản admin (tấn công từ chối dịch vụ). Đây chính là lý
> do đoạn comment ở phần TOTP nói "chỉ tính sau khi mật khẩu đã đúng".

---

## 6. 🟡 MỚI: `update()` thiếu kiểm tra môn còn hoạt động

### Hiện trạng — `create()` có, `update()` không

```java
// create(): QuestionService:199-204  ← CÓ kiểm tra
Subject subject = subjects.findById(request.subjectId())
        .orElseThrow(() -> QbankException.notFound("SUBJECT_NOT_FOUND", ...));
if (!Boolean.TRUE.equals(subject.getIsActive())) {
    throw QbankException.badRequest("SUBJECT_INACTIVE", "Môn học đang bị vô hiệu hoá.");
}

// update(): QuestionService:285  ← KHÔNG kiểm tra
Subject subject = subjects.findById(question.getSubjectId()).orElse(null);   // orElse(null)!
```

Hai điểm khác biệt:

1. `create()` dùng `orElseThrow`, `update()` dùng `orElse(null)` — nếu môn bị xoá khỏi DB,
   `update()` lặng lẽ trả `subjectName = null` thay vì báo lỗi.
2. Không có `SUBJECT_INACTIVE` — vẫn sửa được câu hỏi của môn đã bị vô hiệu hoá.

### Hệ quả

Quản trị viên vô hiệu hoá một môn (ngừng dạy), nhưng giảng viên vẫn sửa câu hỏi của môn đó.
Ngược lại với ý định của việc vô hiệu hoá.

### Cần vá

```java
Subject subject = subjects.findById(question.getSubjectId())
        .orElseThrow(() -> QbankException.notFound("SUBJECT_NOT_FOUND",
                "Môn học của câu hỏi không còn tồn tại."));
if (!Boolean.TRUE.equals(subject.getIsActive())) {
    throw QbankException.badRequest("SUBJECT_INACTIVE",
            "Môn học đang bị vô hiệu hoá, không sửa được câu hỏi. Hãy liên hệ quản trị viên.");
}
```

> Nhưng cân nhắc: nếu **muốn** vẫn cho phép sửa (để dọn dẹp dữ liệu cũ trước khi khoá môn), thì
> chỉ cần sửa phần `orElse(null)` → `orElseThrow`. Đây là quyết định nghiệp vụ, phải ghi rõ.

---

## 7. Hiệu năng — 2 điểm nên biết (không chặn)

### 7.1 Tìm kiếm dùng `LIKE '%...%'` nhưng bảng có `FULLTEXT`

```sql
-- sql/2026-11-19-02-business-tables.sql:143
FULLTEXT KEY ft_q_text (stem, option_a, option_b, option_c, option_d)
```

```java
// QuestionSpecs.java:52-56
String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
predicates.add(cb.or(
        cb.like(cb.lower(root.get("stem")), pattern),
        cb.like(cb.lower(root.get("title")), pattern)));
```

`LIKE '%x%'` **không dùng được index** — MySQL phải quét toàn bộ bảng. Với 500 câu seed thì
không sao; với 10.000 câu thì chậm rõ.

Thêm nữa `title` **không nằm trong** `ft_q_text`, nên không thể chuyển sang `MATCH ... AGAINST`
mà giữ nguyên hành vi tìm theo tiêu đề.

**Đề xuất:** hoặc thêm `title` vào `ft_q_text` rồi dùng `MATCH(stem, title) AGAINST (... IN BOOLEAN MODE)`,
hoặc chấp nhận `LIKE` và ghi rõ trong báo cáo là phạm vi đồ án chỉ tới ngưỡng vài nghìn câu.

### 7.2 `revNo = max + 1` không khoá

```java
// QuestionService:325, 356
int revNo = revisions.findMaxRevNo(questionId) + 1;
```

Hai request đồng thời cùng sửa một câu có thể cùng nhận `revNo = N`, khi đó
`UNIQUE KEY uq_rev (question_id, rev_no)` chặn và trả `DB_CONSTRAINT` — thông báo khó hiểu cho
người dùng.

Xác suất rất thấp vì `@Version` trên `ntt_questions` đã chặn phần lớn trường hợp trước đó. Nếu
muốn chắc: bắt `DataIntegrityViolationException` ở `appendRevision` và đổi thành
`QbankException.conflict("REVISION_CONFLICT", "Có người vừa lưu, hãy tải lại.")`.

---

## 8. Ba điểm làm TỐT — nên ghi vào báo cáo đồ án

Không phải chỉ tìm lỗi. Ba chỗ này làm tốt hơn mức trung bình của đồ án tốt nghiệp:

### 8.1 Chống timing attack khi đăng nhập

```java
// AdminLoginServiceImpl:60-61
boolean passwordOk = mgmtPassword.matches(req.getPassword(), storedHash);
// luôn chạy 1 lần BCrypt giả kể cả khi admin = null
```

Nếu `admin == null` mà return sớm, thời gian phản hồi sẽ **lộ tài khoản có tồn tại hay không**.
Code hiện tại luôn chạy BCrypt nên không lộ. Đây là chi tiết bảo mật mà rất nhiều đồ án bỏ qua —
nên nêu ra khi bảo vệ.

### 8.2 Không ghi mã TOTP vào log

```java
// (d) ... auditService.log(..., outcome.result() == REPLAYED ? "replayed code" : "wrong code");
// Không ghi mã người dùng nhập vào log
```

Ghi mã TOTP vào log là lỗi phổ biến (log bị đọc được = mất 2FA). Code này có comment rõ ràng.

### 8.3 Redis graceful degradation

`UiStateStore` bọc **mọi** thao tác Redis trong `try/catch`, trả giá trị mặc định khi Redis chết:

```java
try { ... } catch (Exception e) { log.warn(...); return null; }
```

Nghĩa là Redis sập thì ứng dụng vẫn chạy (chỉ mất cache), không trả 500 cho người dùng. Đây là
thiết kế đúng cho hệ thống có cache.

---

## 9. Những điểm đã kiểm tra và KHÔNG có vấn đề

| Hạng mục | Kết luận | Kiểm chứng |
|---|---|---|
| Unboxing/null trong so sánh | ✅ An toàn | `revision.getRevNo() != latestRev` có 1 vế là `int` ⇒ Java unbox, so giá trị (JLS 15.21), không phải so tham chiếu |
| So sánh enum bằng `==` | ✅ Đúng | `getStatus() == QuestionStatusD.ARCHIVED` — so với hằng enum là cách đúng |
| `AdminControllerTest` lộ ở prod | ✅ Không | `@Profile({"dev","local","test"})` |
| Lộ `password` trong JSON | ✅ Không | `@JsonIgnore` trên field |
| `getAllAdmins()` trả toàn bộ bảng | ✅ Không nguy hiểm | Chỉ dùng ở `AdminControllerTest` (đã chặn bởi `@Profile`) |
| Khoá lạc quan | ✅ Đúng | `Objects.equals(question.getVersion(), request.expectedVersion())`, lệch → 409 |
| Exception handler | ✅ Đủ 4 loại chính | `QbankException`, `OptimisticLockingFailure`, `DataIntegrityViolation`, `MethodArgumentNotValid` |
| Lộ thông tin DB trong lỗi | ✅ Không | `handleIntegrity` log chi tiết nhưng **trả** thông báo chung |
| N+1 khi tải danh sách | ✅ Đã batch | `loadSubjects`/`loadTopics` nạp 1 lần/trang |
| Phân trang | ✅ Có trần | `Math.min(Math.max(1, size), 100)` |
| AI worker lỗi | ✅ Phân loại rõ | Tách `ResourceAccessException` (không kết nối) / `RestClientResponseException` (bị từ chối) |
| 2FA khoá tạm | ✅ Có, thiết kế đúng | Chỉ đếm sau khi mật khẩu đúng — chống cả DoS |
| `topicId` khác môn | ✅ Chặn | `resolveTopic()` + FK ghép trong DDL |
| Trùng tiêu đề khi sửa | ✅ Đã sửa | Chỉ kiểm tra trong `create()` (commit `3d36e3008`) |

### Một điểm nên cân nhắc (không phải lỗi)

`QbankExceptionHandler` **không có** `@ExceptionHandler(Exception.class)` fallback. Nếu một lỗi
không lường trước xảy ra, Spring Boot sẽ trả **HTML mặc định** thay vì JSON — frontend Vue gọi
`apiErrorMessage()` sẽ không đọc được `body.message` và hiện thông báo chung.

Cách sửa rẻ:

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e) {
    log.error("Qbank: lỗi không lường trước", e);
    return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
            "Máy chủ gặp lỗi không lường trước. Vui lòng thử lại hoặc báo quản trị viên.");
}
```

Đặt `@Order(Ordered.LOWEST_PRECEDENCE)` để không nuốt các handler cụ thể ở trên.

---

## 10. Thứ tự vá đề xuất

| Bước | Việc | Công sức | Vì sao |
|---|---|---|---|
| **1** | Sửa `JwtAuthenticationFilter` (ROLE thật) + `@EnableMethodSecurity` + `@PreAuthorize` | Rất nhỏ | Lỗ hổng leo thang đặc quyền, lặp lại 2 vòng chưa vá |
| **2** | Rate limit cho mật khẩu (§5) | Nhỏ | Chỉ thêm ~10 dòng, dùng lại Redis đã có |
| **3** | Kiểm tra chủ sở hữu `job()`/`suggestions()` (§3) | Nhỏ | Đọc được đề xuất AI của môn khác |
| **4** | Endpoint `restore` (§4) | Vừa | Lỗi mất dữ liệu do thao tác |
| **5** | `update()` kiểm tra môn (§6) | Rất nhỏ | 3 dòng |
| **6** | Fallback `@ExceptionHandler(Exception.class)` (§9) | Rất nhỏ | Tránh trả HTML khi lỗi lạ |
| **7** | Phân công môn (§2) | Vừa | Cần thiết kế CSDL |
| **8** | Tối ưu tìm kiếm (§7.1) | Vừa | Chỉ cần khi dữ liệu lớn |
| **9** | Test tầng API | Vừa | Chặn hồi quy |

> Nếu chỉ có **một buổi**: làm **1 + 2 + 5 + 6** — bốn việc này cộng lại chưa tới 40 dòng code
> nhưng bịt được lỗ hổng nghiêm trọng nhất và ba lỗi logic rõ ràng.
