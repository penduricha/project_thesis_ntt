# Review `be-web-admin` (Java) và `app_mobile` (Flutter) — đợt 3

Rà trên `feature/apply-ui-project-thesis@de04940a0` (sau khi bạn merge 2 commit mới).
Máy không có `javac`/`javadoc`/`flutter` nên mọi kết luận dưới đây là đọc code + đối chiếu tài liệu
chuẩn (RFC 6238 / RFC 4226), **không phải** kết quả chạy thử — trừ phần TOTP có kiểm chứng độc lập
bằng Python bên dưới.

---

## 1. Java `be-web-admin` — 6 việc vòng 2 vẫn NGUYÊN, không có hồi quy

Kiểm chứng lại trên commit mới nhất:

```bash
# 1. ROLE_ADMIN vẫn hard-code
$ sed -n '45,58p' security/jwt/JwtAuthenticationFilter.java
List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))   # ← LECTURER thành ADMIN

# 2. Chưa có kiểm tra quyền ở tầng method
$ grep -rn "PreAuthorize\|EnableMethodSecurity" --include=*.java .
0 kết quả

# 3. Chưa có phân công môn ở backend
$ grep -rn "SubjectLecturer\|subject_lecturers" --include=*.java .
0 kết quả
```

`git log -- <file>.java` cho 3 commit gần nhất: **không có file `.java` nào bị sửa**. Nghĩa là 6 việc
trong `docs/review-spring-boot-can-va-vong2.md` vẫn giữ nguyên mức ưu tiên, không cần rà lại.

**Điểm nên nhắc trong báo cáo đồ án:** luồng đăng nhập Java làm đúng những chỗ dễ sai nhất —
luôn chạy BCrypt giả kể cả khi `admin` không tồn tại (chống timing attack để dò tài khoản), không
ghi mã TOTP vào log, Redis bọc `try/catch` toàn bộ nên Redis sập vẫn đăng nhập được.

---

## 2. Flutter `app_mobile` — tổng thể RẤT TỐT, tìm được 1 lỗi crash thật

### 2.1 Đã kiểm chứng là ĐÚNG (không phải vá)

**TOTP đúng chuẩn RFC 6238.** Kiểm chứng độc lập bằng Python trên đúng thuật toán trong
`totp_generator.dart` (Base32 → 8 byte big-endian → HMAC → dynamic truncation → mod 10^d):

```
RFC 6238 SHA-1 (secret 20 byte):          6/6 vector đúng
  t=59 → 94287082 ✓    t=1111111109 → 07081804 ✓   t=1111111111 → 14050471 ✓
  t=1234567890 → 89005924 ✓   t=2000000000 → 69279037 ✓   t=20000000000 → 65353130 ✓

RFC 6238 SHA-256 (secret 32 byte):        6/6 vector đúng
  t=59 → 46119246 ✓    t=1111111109 → 68084774 ✓   t=1111111111 → 67062674 ✓
  t=1234567890 → 91819424 ✓   t=2000000000 → 90698825 ✓   t=20000000000 → 77737706 ✓
```

Đáng khen: dùng `setUint32` × 2 thay vì `setUint64` **có chủ ý** — `setUint64` không chạy trên
Flutter Web. Nếu đổi sang `setUint64` cho "gọn" thì app vỡ khi build web.

**Bảo mật lưu trữ:** `flutter_secure_storage` (Android Keystore / iOS Keychain) cho cả access token,
refresh token **và secret TOTP** — đúng. Không có `print`/`debugPrint` nào in token, secret hay mã OTP.

**WebSocket** (`chat_socket_service.dart`) làm đúng chuẩn: vé dùng một lần sống 30 giây (không nhét
JWT vào URL — tránh lộ token qua log proxy), heartbeat `pong` 10 giây, backoff có jitter.

**Quản lý tài nguyên:** 12 chỗ khai báo `dispose()` đều có; `StreamSubscription` đều được lưu và
cancel; `Timer` không có chỗ nào bị bỏ quên.

### 2.2 🟠 Lỗi THẬT đã vá: `periodSeconds` từ server không được kiểm ⇒ crash tab Xác thực

**Đường đi của dữ liệu:**

```
server GET .../enrollment/pending
  → { "periodSeconds": 30, "digits": 6, "algorithm": "HmacSHA256" }
  → _fetchPending():  periodSeconds: (data['periodSeconds'] as num).toInt()   ← KHÔNG kiểm
  → AuthAccount.periodSeconds
  → AuthenticatorScreen._elapsedFraction(period):
        final periodMs = period * 1000;
        final ms = DateTime.now().millisecondsSinceEpoch % periodMs;   ← % 0 nếu period = 0
```

Với `periodSeconds == 0` thì `% 0` ném lỗi ngay ⇒ **cả tab Xác thực trắng/crash**, không chỉ một
tài khoản. `_clockFor()` cũng tạo `AnimationController(duration: Duration(seconds: 0))` — animation
hoàn tất tức thì nên `_onClockCompleted()` được gọi lại liên tục: vòng lặp vô hạn, app treo.

**Tại sao các lớp bảo vệ hiện có không cứu được:**

- `TotpGenerator.generate()` có `assert(period > 0)` — nhưng **`assert` bị bỏ ở bản release**.
- `AuthenticatorScreen._generateCodes()` chỉ bắt `FormatException` (secret sai định dạng Base32),
  không bắt lỗi chia cho 0.
- `digits` cũng không kiểm: `digits = 0` cho mã rỗng; `digits > 9` làm `math.pow(10, digits)` vượt
  độ chính xác số nguyên của Dart.

**Cách vá (2 lớp, vì dữ liệu cũ đã lưu vẫn có thể có `period = 0`):**

| Chỗ vá | Nội dung |
|---|---|
| `EnrollmentService._fetchPending()` | Biên giới tin cậy: `periodSeconds <= 0` và `digits < 6 \|\| > 9` ⇒ ném `EnrollmentException` có câu báo tiếng Việt |
| `AuthenticatorScreen._elapsedFraction()` + `_clockFor()` | Chặn dưới: `period > 0 ? period : TotpGenerator.defaultPeriod` — cứu các tài khoản **đã lưu từ bản cũ**, không đi qua bước kiểm ở trên nữa |

### 2.3 🟡 Nên cân nhắc (chưa vá — cần bạn quyết)

**Không có kiểm tra lệch đồng hồ.** Mã TOTP phụ thuộc đồng hồ máy. Nếu điện thoại lệch vài phút,
mã luôn sai mà người dùng không biết tại sao — họ sẽ tưởng app hỏng. Server thường chấp nhận
`±1` bước (RFC 6238 §5.2) để bù, nhưng app nên **cảnh báo** khi mã bị từ chối nhiều lần liên tiếp
(gợi ý: "Kiểm tra giờ trên điện thoại — nên bật tự động"). Đã ghi vào mục "nên làm", không tự sửa
vì cần bạn quyết định thiết kế thông báo.

**`pubspec.yaml` dùng `crypto: ^3.0.8`** trong khi comment ở `totp_generator.dart` còn ghi
`crypto: ^3.0.3` — comment lỗi thời, chỉ ảnh hưởng người đọc code.

---

## 3. Tổng kết mức ưu tiên

| # | Việc | Nơi | Mức | Trạng thái |
|---|---|---|---|---|
| 1 | `ROLE_ADMIN` hard-code + không `@PreAuthorize` | `JwtAuthenticationFilter` / `SecurityConfig` | 🔴 | **ĐÃ VÁ** |
| 2 | Không phân công môn ở BE | — | 🔴 | Chưa vá (cần bảng `ntt_subject_lecturers`) |
| 3 | `AiCheckService.job/suggestions` không kiểm chủ sở hữu | `AiCheckService` | 🟠 | **ĐÃ VÁ** |
| 4 | Không có chức năng khôi phục câu hỏi đã lưu trữ | `QuestionService` | 🟠 | **ĐÃ VÁ** |
| 5 | Đăng nhập không rate-limit mật khẩu | `AdminLoginServiceImpl` | 🟠 | **ĐÃ VÁ** |
| 6 | `periodSeconds` từ server không kiểm ⇒ crash tab TOTP | `enrollment_service.dart` | 🟠 | **ĐÃ VÁ** |
| 7 | `update()` thiếu `SUBJECT_INACTIVE` | `QuestionService` | 🟡 | **ĐÃ VÁ** |
| 8 | Không cảnh báo lệch đồng hồ TOTP | `authenticator_screen.dart` | 🟡 | Đề xuất |
| 9 | Lỗi lạ trả HTML, Vue không parse được | `QbankExceptionHandler` | 🟡 | **ĐÃ VÁ** |
| 10 | Tiền kiểm + LLM báo trùng một lỗi ⇒ 2 dòng | `AiResultParser` | 🟡 | **ĐÃ VÁ** |

### 3.1 Chi tiết các bản vá (đợt này)

**Việc 1 — vai trò thật.** `JwtAuthenticationFilter` đọc `role` của admin từ DB (sau khi Redis đã xác
nhận token còn sống) và gán `ROLE_ADMIN` / `ROLE_LECTURER`. Không lấy từ JWT claim vì (a) token đã
cấp trước khi vá không có claim đó, (b) claim sống 5 phút nên hạ quyền một admin mà token cũ vẫn còn
quyền cũ. Bật `@EnableMethodSecurity` + `@PreAuthorize` ở 4 controller, quy ước tập trung ở
`controller/web/WebRoles.java`:

| Nhóm API | Quyền |
|---|---|
| `QuestionBankController`, `AiJobController` | ADMIN + LECTURER (công việc chuyên môn của giảng viên) |
| `AdminControllerTest` (tạo/xem tài khoản) | chỉ ADMIN |

Lỗi đọc DB ⇒ **không cấp quyền nào** (rơi vào 401), tuyệt đối không mặc định ADMIN.

**Việc 5 — rate-limit mật khẩu.** 10 lần sai/15 phút cho một `adminId`. Chặn **trước** khi chạy
BCrypt, nếu không kẻ tấn công vẫn ép server băm liên tục dù biết chắc bị khoá. Redis lỗi ⇒ bỏ qua
chặn (không biến sự cố Redis thành sự cố đăng nhập toàn hệ thống). Mật khẩu đúng ⇒ xoá bộ đếm.

Đánh đổi đã cân nhắc: khác với TOTP (chỉ đếm **sau** khi mật khẩu đúng, để không ai khoá được tài
khoản người khác), bước mật khẩu **phải** đếm cả khi sai — đó chính là thứ cần chặn. Nghĩa là kẻ
không biết mật khẩu vẫn khoá được tài khoản 15 phút; thà vậy còn hơn để lộ mật khẩu.

**Việc 3 — chủ sở hữu AI job.** `job()`/`suggestions()` nhận thêm `adminId` (lấy từ `WebPrincipal`,
không từ query). So với `created_by` của task; ADMIN xem được mọi task. Người không phải chủ nhận
**404** chứ không phải 403, để không xác nhận "task này có tồn tại".

Điểm dễ sai đã xử lý: `job()` đọc **Redis trước**. Nếu kiểm sau khi trả cache thì cache vẫn lộ dữ
liệu. Bản ghi Redis cũ chưa có `adminId` (null) được coi là "phải xuống MariaDB kiểm", **không** coi
null là của mọi người.

**Việc 4 — khôi phục.** `QuestionService.restore()` + `POST /web-bank/{id}/restore`. Đối xứng với
`archive()`: đổi cờ về `ACTIVE`, ghi revision với `ChangeSourceD.RESTORE`, đẩy activity
`QUESTION_RESTORED`. Enum và `SQL_TYPE` đã có sẵn từ CSDL v4 nên không phải sửa schema.

**Việc 9 — lưới an toàn lỗi.** Thêm 5 handler: `MethodArgumentTypeMismatchException` (ví dụ
`?page=abc`), `MissingServletRequestParameterException`, `HttpRequestMethodNotSupportedException`,
`NoResourceFoundException`, và `Exception` cuối cùng. Lỗi lạ giờ trả
`{success:false, code:"INTERNAL_ERROR", message, at}` — giao diện luôn có câu để hiển thị thay vì
nhận trang lỗi HTML. Stack trace chỉ ghi log server, không đưa vào phản hồi.

**Việc 10 — AI: tiền kiểm và LLM báo trùng một lỗi.** `RulePreflightChecker` (tất định) dùng khoá
`field|issue`, còn nhánh LLM dùng `field|issue|span` — hai khuôn khác nhau nên **không bao giờ
trùng**. Kết quả: `option_b` trống được báo 2 lần cho người duyệt (một của tiền kiểm, một của LLM).
Đã thêm tập `preflightKeys` riêng: lỗi nào tiền kiểm đã bắt thì bỏ bản của LLM (tiền kiểm chắc chắn
100% vì so khớp chuỗi). Mô phỏng: **3 dòng → 2 dòng**, không còn dòng trùng.

### 3.2 Một nhận định SAI của tôi, đã rút lại

Tôi từng nghi `accepted.isEmpty() ? suggestedDifficulty : null` trong `AiResultParser` là bug "luôn
true vì chạy bên trong `accepted.add(...)`" ⇒ mọi đề xuất đều mang cùng một độ khó. **Sai**: Java
đánh giá đối số **trước** khi gọi `add()`, nên `isEmpty()` đúng một lần. Đã mô phỏng 4 trường hợp
(0/1/2/3 đề xuất) — bản cũ đúng cả 4. Đã hoàn tác toàn bộ thay đổi đó và thay bằng test
`AiResultParserDifficultyTest` ghim đúng hành vi để người sau không nghi lại.

Tương tự, tôi từng nghĩ `RulePreflightChecker` không chạy trong luồng thật — sai: `AiResultParser`
gọi nó ở dòng 64 và `application.yml` đặt `rule-preflight: true`.

**Điểm mạnh nên đưa vào báo cáo đồ án:** cả hai phía đều xử lý đúng những chỗ dễ sai nhất —
Java chống timing attack + không log OTP + Redis graceful degradation; Flutter TOTP đúng RFC 6238
(đã kiểm 12/12 vector), secret lưu Keystore/Keychain, WebSocket dùng vé một lần thay vì token
trong URL.
