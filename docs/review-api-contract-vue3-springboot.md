# Đối chiếu hợp đồng API: Vue 3 (`qbank-console-vue3`) ↔ Spring Boot (`be-web-admin`)

> Tài liệu review kỹ thuật fullstack cho đồ án tốt nghiệp.
> Mọi khẳng định đều được kiểm chứng trực tiếp từ mã nguồn trong repo, kèm đường dẫn và số dòng.
> Ngày lập: 2026-11-23 · Phạm vi: chỉ 2 nhánh `qbank-console-vue3` (Vue 3 + Vite) và `be-web-admin` (Spring Boot).
> `php_moodle_503/` nằm ngoài phạm vi.

---

## 0. Kết luận ngắn

Hợp đồng API giữa hai phía **khớp nhau ở mức giao thức**: đường dẫn, tham số truy vấn, mã lỗi
nghiệp vụ và hình dạng JSON đều nhất quán. Đã đối chiếu **5 nhóm endpoint** và **4 hợp đồng
DTO** — không phát hiện lỗi tích hợp nghiêm trọng nào.

Tuy nhiên, review ghi nhận **7 vấn đề thiết kế** cần xử lý trước khi bảo vệ đồ án (xem §5).
Rủi ro nghiêm trọng nhất là **N+1 request khi tải ngân hàng câu hỏi** (§5.1) và
**thiếu kiểm thử hợp đồng tự động** giữa hai phía (§5.7).

---

## 1. Cơ chế định tuyến và base URL

Đây là điểm dễ hiểu sai nhất, vì có **hai tầng tiền tố** chồng lên nhau.

### 1.1 Phía Spring Boot — hai tầng

```
be-web-admin/src/main/resources/application.yaml:8
    server.servlet.context-path: /api

controller/web/QuestionBankController.java:37
    @RequestMapping("/web/admin/questions")
```

⇒ Đường dẫn thực tế: `http://host:8080/api` + `/web/admin/questions`
= **`/api/web/admin/questions`**

Các controller web đều khai báo `/web/admin` làm gốc (trừ các mục con nêu rõ):

| Controller | Gốc `@RequestMapping` | Dòng |
|---|---|---|
| `AdminControllerMgmtWeb` | `/web/admin` | 23 |
| `EnrollSessionControllerWeb` | `/web/admin` | 23 |
| `TwoFaEventControllerWeb` | `/web/admin` | 20 |
| `EnrollmentController` | `/web/admin` | 28 |
| `QuestionBankController` | `/web/admin/questions` | 37 |
| `CatalogController` | `/web/admin/catalog` | 17 |
| `DashboardController` | `/web/admin/dashboard` | 19 |
| `AiJobController` | `/web/admin/ai` | 28 |

### 1.2 Phía Vue 3 — base + `/admin`

`qbank-console-vue3/src/config.js` dựng `API_BASE` theo hai cách:

```js
// config.js — ưu tiên VITE_API_BASE_URL, không có thì ghép từ VITE_SPRING_*
function buildBase() {
  const direct = String(ENV.VITE_API_BASE_URL ?? '').trim()
  if (direct) return direct.replace(/\/+$/, '')
  const prefix = readPrefix('VITE_SPRING_API_PREFIX', '/api/web')   // → '/api/web'
  return `${proto}://${host}:${port}${prefix}`
}
```

⇒ `API_BASE = http://localhost:8080/api/web`

`api/qbankApi.js` thêm tiền tố `/admin`:

```js
const ADMIN = '/admin'
const url = (path) => `${ADMIN}${path}`
// GET url('/questions') → /admin/questions → ghép base → /api/web/admin/questions ✓
```

**Kết luận:** hai phía khớp chính xác. `/api` (context-path) + `/web` (prefix trong config)
+ `/admin` (tiền tố trong `qbankApi.js`).

### 1.3 Điểm không nhất quán cần biết

`auth/EnrollmentService.js` **không** dùng `qbankApi.js` mà tự dựng URL:

```js
// auth/EnrollmentService.js:7-13
const ENDPOINTS = Object.freeze({
    ENROLLMENT: '/admin/2fa/enrollment',
    ACTIVATE:   '/admin/2fa/enrollment/activate',
    PENDING:    '/admin/2fa/enrollment/pending',
    STATUS:     '/admin/2fa/status',
    EVENTS:     '/admin/2fa/events',
});
// :137
fetch(`${baseUrl}${ENDPOINTS.ENROLLMENT}`, { … })
```

Với `baseUrl = API_BASE = /api/web` thì kết quả vẫn đúng
(`/api/web/admin/2fa/status`). Nhưng **hai module tự dựng URL theo hai cách khác nhau** là
nguồn gốc của lỗi "404 chỉ ở một màn hình". Xem rủi ro §5.4.

---

## 2. Ma trận endpoint đã đối chiếu

Ký hiệu: ✓ = khớp hoàn toàn.

### 2.1 Xác thực — `AdminControllerMgmtWeb`

| Vue 3 (`auth/AdminService.js`) | Spring Boot | Trạng thái |
|---|---|---|
| `POST /admin/auth/login` | `@PostMapping({"/auth/login", "/auth/login/totp"})` :264 | ✓ |
| `POST /admin/auth/login/totp` | cùng mapping (giữ cho client cũ) | ✓ |
| `POST /admin/auth/logout` | `@PostMapping("/auth/logout")` :42 | ✓ |
| `GET /admin/information/basic?adminId=` | `@GetMapping("/information/basic")` :199 | ✓ |
| `GET /admin/information/detail?adminId=` | `@GetMapping("/information/detail")` :225 | ✓ |
| `GET /admin/auth/login/auto/access-token` | :84 | ✓ |
| `GET /admin/auth/login/auto/refresh-token` | :132 | ✓ |

> **Ghi chú thiết kế tốt:** backend gộp `/auth/login` và `/auth/login/totp` vào cùng một
> handler. Vue 3 vẫn gọi hai đường dẫn riêng (`LOGIN` và `LOGIN_TOTP`) như bản
> `fe_web_admin` gốc — điều này **không sai**, chỉ là dư thừa. Giữ nguyên là quyết định đúng
> vì yêu cầu "logic đăng nhập giống `fe_web_admin` 100%" vẫn được bảo toàn.

### 2.2 2FA — `EnrollSessionControllerWeb`, `EnrollmentController`, `TwoFaEventControllerWeb`

| Vue 3 (`auth/EnrollmentService.js`) | Spring Boot | Trạng thái |
|---|---|---|
| `POST /admin/2fa/enrollment` | `@PostMapping("/2fa/enrollment")` EnrollSession :42 | ✓ |
| `DELETE /admin/2fa/enrollment` | `@DeleteMapping("/2fa/enrollment")` EnrollSession :85 | ✓ |
| `POST /admin/2fa/enrollment/activate` | `@PostMapping("/2fa/enrollment/activate")` Enrollment :49 | ✓ |
| `DELETE /admin/2fa/enrollment/pending` | `@DeleteMapping("/2fa/enrollment/pending")` Enrollment :136 | ✓ |
| `GET /admin/2fa/status` | `@GetMapping("/2fa/status")` Enrollment :103 | ✓ |
| `GET /admin/2fa/events` | `TEXT_EVENT_STREAM_VALUE` TwoFaEvent :39 | ✓ |

**Hình dạng `GET /2fa/status` khớp chính xác:**

```java
// EnrollmentController.java — 200
// { success, message, data: { totpEnabled, pending, expiresInSeconds, periodSeconds } }
data.put("totpEnabled", status.totpEnabled());
data.put("pending", status.pending());
data.put("expiresInSeconds", status.expiresInSeconds());
data.put("periodSeconds", status.periodSeconds());
```

```js
// EnrollmentRepository.js:166-176 — Vue đọc đúng 4 trường
if (response.status === HTTP_OK && body?.success === true && typeof data?.totpEnabled === 'boolean') {
  if (data.totpEnabled) status = TWO_FA_STATUS.ENABLED;
  else if (data.pending === true) status = TWO_FA_STATUS.PENDING;
  pendingExpiresInSeconds: Number(data.expiresInSeconds) || 0,
}
```

### 2.3 Ngân hàng câu hỏi — `QuestionBankController`

| Vue 3 (`api/qbankApi.js`) | Spring Boot | Trạng thái |
|---|---|---|
| `GET /admin/questions` | `@GetMapping` :53 | ✓ |
| `GET /admin/questions/{id}` | `@GetMapping("/{questionId}")` :65 | ✓ |
| `POST /admin/questions` | `@PostMapping` :80 | ✓ |
| `PUT /admin/questions/{id}` | `@PutMapping("/{questionId}")` :87 | ✓ |
| `DELETE /admin/questions/{id}` (archive) | `@DeleteMapping("/{questionId}")` :95 | ✓ |
| `GET /admin/questions/{id}/revisions` | `@GetMapping("/{questionId}/revisions")` :71 | ✓ |
| `GET /admin/questions/{id}/ai-suggestions` | :102 | ✓ |
| `POST /admin/questions/{id}/ai-check` | :108 | ✓ |
| `GET/PUT/DELETE /admin/questions/draft` | :117/:126/:138 | ✓ |

### 2.4 AI job — `AiJobController`

| Vue 3 | Spring Boot | Trạng thái |
|---|---|---|
| `GET /admin/ai/jobs/{taskId}` | `@GetMapping("/jobs/{taskId}")` :40 | ✓ |
| `GET /admin/ai/jobs/{taskId}/suggestions` | :45 | ✓ |
| `POST /admin/ai/suggestions/{id}/decision` | `@PostMapping("/suggestions/{suggestionId}/decision")` :54 | ✓ |

### 2.5 Danh mục, bảng điều khiển

| Vue 3 | Spring Boot | Trạng thái |
|---|---|---|
| `GET /admin/catalog/subjects?includeInactive` | Catalog :26 | ✓ |
| `GET /admin/catalog/subjects/{id}/topics` | Catalog :31 | ✓ |
| `GET /admin/catalog/issue-types` | Catalog :36 | ✓ |
| `GET /admin/dashboard/summary` | Dashboard :28 | ✓ |
| `GET /admin/dashboard/activity` | Dashboard :33 | ✓ |

---

## 3. Hợp đồng DTO — 4 điểm then chốt

### 3.1 Đăng nhập: request

```java
// dto/AdminLoginDTO.java
private Long   adminId;
private String password;   // @ToString.Exclude
private String totpCode;   // null ở lần POST đầu
```

```js
// AdminService.postDataToLoginRequest — chỉ 2 trường
{ adminId: adminAccount.getAdminId(), password: adminAccount.getPassword() }

// AdminService.postDataToLoginTotpRequest — thêm totpCode
{ adminId, password, totpCode: String(totpCode) }
```

✓ Khớp. `adminId` kiểu `Long` phía Java nhận được số từ `Number(adminId)` phía JS.

### 3.2 Đăng nhập: response — đây là chỗ tinh tế nhất

Enum phía Java có **5 giá trị**, nhưng JSON trả về **không** dùng enum trực tiếp:

```java
// enums/LoginStatus.java
enum LoginStatus { OK, BAD_CREDENTIALS, TOTP_REQUIRED, TOTP_INVALID, LOCKED }
```

```java
// AdminControllerMgmtWeb.buildLoginResponse — ánh xạ sang { success, message, data.code }
case OK            -> success=true,  data={adminId, accessToken, refreshToken}
case TOTP_REQUIRED -> success=false, data={code:"TOTP_REQUIRED"}
case TOTP_INVALID  -> success=false, data={code:"TOTP_INVALID"}
case LOCKED        -> success=false, data={code:"LOCKED", retryAfterSeconds}
default            -> success=false, data=null            // BAD_CREDENTIALS
```

```js
// AdminRepository.#toLoginResult — Vue đọc đúng cấu trúc này
const { success, data } = body;
if (success) { this.#saveLoginSession(adminAccount, data); return new LoginResult(REQUEST_STATUS.OK); }
switch (data?.code) {
  case 'TOTP_REQUIRED': return new LoginResult(REQUEST_STATUS.TOTP_REQUIRED);
  case 'TOTP_INVALID':  return new LoginResult(REQUEST_STATUS.TOTP_INVALID);
  case 'LOCKED':        return new LoginResult(REQUEST_STATUS.LOCKED, Number(data.retryAfterSeconds) || 0);
  default:              return new LoginResult(REQUEST_STATUS.FAILED);   // BAD_CREDENTIALS
}
```

**Điểm cần nắm để bảo vệ đồ án:** `BAD_CREDENTIALS` trả `data: null`, nên nhánh
`switch (data?.code)` rơi vào `default` — đúng ý đồ. Nếu ai đó "cải tiến" phía Java bằng cách
thêm `data={code:"BAD_CREDENTIALS"}`, mã Vue vẫn chạy (rơi vào `default`), **nhưng** nếu thêm
`code` khác lạ thì cũng rơi `default` và bị coi là sai mật khẩu. Hành vi này **an toàn theo
hướng thất bại** (fail-safe) — nên giữ.

### 3.3 Quyết định gợi ý AI

```java
// dto/qbank/SuggestionDecisionRequest.java
public record SuggestionDecisionRequest(
        @NotBlank String decision,        // "accepted" | "rejected"
        @Size(max = 20000) String finalText,
        @Size(max = 255)   String rejectReason,
        Integer expectedVersion) { }
```

✓ Khớp với tham số Vue gửi. `expectedVersion` dùng cho kiểm soát xung đột lạc hậu
(optimistic locking) → backend trả `409` khi lệch phiên bản.

### 3.4 Mã lỗi nghiệp vụ

```java
// exception/qbank/QbankExceptionHandler.java — trả { success:false, code, message }
// Vue: api/qbankApi.js:apiErrorMessage() đọc body.message → body.code → theo HTTP status
if (body && typeof body === 'object') {
  if (body.message) return body.message;
  if (body.code) return body.code;
}
if (error?.response?.status === 401) return 'Phiên đăng nhập đã hết hạn…';
if (error?.response?.status === 503) return 'Dịch vụ AI (Server 2) chưa được cấu hình…';
```

✓ Khớp. `503 AI_WORKER_NOT_CONFIGURED` được xử lý riêng — nhất quán với ghi chú trong
`application.yaml`: *"Bỏ trống base-url => tính năng Kiểm định AI trả 503, KHÔNG tạo task treo"*.

---

## 4. Cơ chế khóa đăng nhập — đối chiếu hai phía

Đây là phần dễ lệch nhất giữa frontend và backend, nên đối chiếu kỹ.

### 4.1 Phía Spring Boot

```java
// service/jpa/impl/AdminLoginServiceImpl.java
private static final int MAX_TOTP_FAILS = 5;
private static final Duration TOTP_FAIL_WINDOW = Duration.ofMinutes(5);
private static final String TOTP_FAIL_KEY = "totp:fail:";
```

Luồng thực tế (theo đúng thứ tự trong `login()`):

1. `adminId` hoặc `password` null → `BAD_CREDENTIALS`
2. `adminRepository.findAdminByAdminId()` + `mgmtPassword.matches()` — **luôn chạy BCrypt kể cả
   khi admin không tồn tại**, để thời gian phản hồi không lộ tài khoản có tồn tại hay không
3. Sai mật khẩu → `BAD_CREDENTIALS`
4. **Chưa có credential `confirmed=true`** → `issueTokens()` luôn, bỏ qua ô mã 6 số
5. Đã bật 2FA, thiếu mã → `TOTP_REQUIRED`
6. Đếm `totp:fail:{adminId}` trong Redis; `>= 5` → `LOCKED` kèm `ttl` còn lại
7. Mã đúng → `redis.delete(failKey)` + ghi audit + cấp token
8. Mã sai → `INCR`; lần sai **thứ 5** khóa ngay và **đặt lại TTL đủ 5 phút** kể từ lần sai cuối

> **Chi tiết đáng chấm điểm:** bước 6 và bước 8 đều đặt `expire` = 5 phút. Nếu giữ TTL tính
> từ lần sai đầu, thời gian khóa thực tế có thể chỉ còn vài giây. Đặt lại từ lần sai cuối đảm
> bảo người dùng luôn bị khóa đủ 5 phút — đây là quyết định thiết kế có chủ ý, mã nguồn ghi rõ
> lý do tại dòng 123-125.

**Điểm bảo mật tốt:** bộ đếm chỉ tăng **sau khi mật khẩu đã đúng**, nên kẻ không biết mật khẩu
không thể cố ý khóa tài khoản của admin (tấn công từ chối dịch vụ có chủ đích).

### 4.2 Phía Vue 3

```js
// views/LoginView.vue
const LOCK_STORAGE_KEY = 'login.totpLock'    // :40
case REQUEST_STATUS.LOCKED:                   // :302
```

Vue nhận `retryAfterSeconds` từ `LoginResult.getRetryAfterSeconds()`, lưu mốc hết hạn vào
`localStorage` và đếm ngược. Nhờ vậy **tải lại trang vẫn còn khóa** — hành vi đúng, tránh
người dùng F5 để vượt khóa phía client.

### 4.3 Kết luận đối chiếu

✓ Khớp về ngưỡng (5 lần), thời gian (5 phút) và ngữ nghĩa `retryAfterSeconds` (giây còn lại).

⚠️ **Hai đồng hồ, hai nguồn sự thật.** Backend là nguồn chuẩn (Redis TTL), frontend chỉ
mô phỏng lại. Nếu người dùng chỉnh đồng hồ máy, bộ đếm ngược của Vue sẽ sai — nhưng **không mở
được khóa thật**, vì lần gửi tiếp theo backend vẫn trả `LOCKED` kèm `ttl` mới. Cơ chế tự đúng
lại. Đây là thiết kế phòng thủ đúng, nên ghi rõ trong báo cáo.

---

## 5. Rủi ro thiết kế — cần xử lý trước khi bảo vệ

### 5.1 ⚠️ NGHIÊM TRỌNG — N+1 request khi tải ngân hàng câu hỏi

`store.js` tải **toàn bộ** câu hỏi rồi gọi chi tiết từng câu:

```js
// store.js:148-160
export async function loadQuestions() {
  const catalogOk = await loadCatalog()
  const items = []
  let page = 0
  for (;;) {
    const res = await qbankApi.listQuestions({ status: 'active', page, size: 100 })
    items.push(...(res.content || []))
    if (page + 1 >= (res.totalPages || 1)) break
    page++
  }
  const details = await Promise.all(items.map((it) => qbankApi.getQuestion(it.questionId).catch(() => null)))
  …
}
```

Với **N câu hỏi**, số request = `ceil(N/100)` (danh sách) + **N** (chi tiết).

| Số câu hỏi | Request danh sách | Request chi tiết | Tổng |
|---|---|---|---|
| 100 | 1 | 100 | **101** |
| 500 | 5 | 500 | **505** |
| 2 000 | 20 | 2 000 | **2 020** |

`Promise.all` chỉ song song hoá **độ trễ**, không giảm số request. Với 2 000 câu hỏi, trình
duyệt mở 2 000 kết nối đồng thời — sẽ chạm giới hạn 6 kết nối/domain của HTTP/1.1 và có thể
làm nghẽn cả những request khác.

**Nguyên nhân:** `QuestionListItem` (dùng trong danh sách) không chứa 4 phương án, nên frontend
phải gọi chi tiết để lấy.

**Ba hướng khắc phục, xếp theo chi phí:**

1. **Rẻ nhất — thêm `size` lớn hơn và trả kèm phương án.** Cho `QuestionListItem` mang thêm
   `options[]`, hoặc thêm endpoint `GET /questions?includeOptions=true`. Sửa 1 DTO + 1 truy vấn
   (dùng `JOIN FETCH` hoặc `@EntityGraph` để tránh N+1 ngay trong Hibernate).
2. **Trung bình — endpoint danh sách theo lô:** `POST /questions/batch {ids:[…]}` trả nhiều chi tiết
   trong một request.
3. **Dài hạn — phân trang thật ở UI.** Hiện Vue tải hết rồi phân trang phía client. Chuyển sang
   phân trang server-side thì N nhỏ (mỗi trang 20-50 câu).

Với đồ án, hướng **1** là đủ và thể hiện hiểu biết về N+1 — một điểm cộng khi bảo vệ.

### 5.2 ⚠️ Tham số tìm kiếm: tên `q` vs `keyword`

```java
// QuestionBankController.java:53 — tên tham số trên URL là "q"
@RequestParam(name = "q", required = false) String keyword
```

Tên biến Java là `keyword` nhưng **tên trên URL là `q`**. Vue hiện chỉ gọi
`listQuestions({ status:'active', page, size:100 })` nên chưa lộ vấn đề. Nhưng nếu sau này
thêm ô tìm kiếm và gửi `{ keyword: '…' }`, backend sẽ **bỏ qua im lặng** (vì `required=false`)
và trả toàn bộ danh sách — lỗi rất khó phát hiện vì không có thông báo.

```js
// api/qbankApi.js:44 — comment đã ghi đúng "q"
// GET /questions?subjectId&topicId&status&validationStatus&q&page&size -> PageResponse
```

**Khuyến nghị:** thêm hằng số dùng chung trong `qbankApi.js` để không ai gõ nhầm:

```js
const SEARCH_PARAM = 'q'   // khớp @RequestParam(name="q") của QuestionBankController
```

### 5.3 ⚠️ Màn hình tìm kiếm chưa nối API thật

`SearchView.vue:22` chỉ tách từ khoá từ câu lệnh tiếng Việt bằng regex:

```js
const m = s.match(/chứa\s+[“"]?([^”"]+)[”"]?/i); if (m) F.keyword = m[1].trim()
```

`F.keyword` được dùng để lọc dữ liệu **đã tải sẵn trong bộ nhớ**, chứ không gửi lên server.
Hệ quả: chỉ tìm được trong số câu hỏi đã tải (và đã chịu ảnh hưởng của §5.1).

**Khuyến nghị:** nối `F.keyword` → `qbankApi.listQuestions({ q: keyword, … })`. Khi đó §5.1 và
§5.2 phải xử lý cùng lúc.

### 5.4 ⚠️ Hai module tự dựng URL theo hai cách

`qbankApi.js` dùng `SpringConfigHeader` + tiền tố `/admin`; `EnrollmentService.js` tự nối
`${baseUrl}${ENDPOINTS.X}` bằng `fetch()` thô.

**Hệ quả thực tế:** module 2FA **không** được hưởng interceptor tự động làm mới token của
`SpringConfigHeader`. `EnrollmentService.js:171` có `retryOn401(...)` để bù, nhưng đây là hai cơ
chế song song cùng tồn tại — khi có sự cố 401, phải kiểm tra hai nơi thay vì một.

**Khuyến nghị:** thống nhất dùng `SpringConfigHeader.getAuthorizedAPIClient()` cho **tất cả**
lời gọi cần token. Riêng SSE (`/2fa/events`) cần `fetch()` thô vì `EventSource` không gắn được
header `Authorization` — trường hợp này hợp lý, nên ghi chú rõ trong mã.

### 5.5 ℹ️ Biến thừa trong `loadQuestions`

```js
// store.js:149
const catalogOk = await loadCatalog()   // gán nhưng KHÔNG BAO GIỜ đọc
```

`loadCatalog()` vẫn cần được gọi (nạp danh mục môn/chương), nhưng giá trị trả về bị bỏ qua.
Không phải lỗi chức năng, nhưng là dấu hiệu logic phòng thủ đã bị bỏ dở: có lẽ ý định ban đầu
là "nếu nạp danh mục thất bại thì dùng dữ liệu mẫu". Nên hoặc dùng giá trị đó, hoặc bỏ phép gán.

### 5.6 ℹ️ `LOCKED` không phân biệt khóa-mật-khẩu và khóa-TOTP

Enum có một `LOCKED`, dùng cho bộ đếm TOTP. Nếu sau này thêm bộ đếm cho mật khẩu,
`retryAfterSeconds` sẽ mang hai ý nghĩa khác nhau mà client không phân biệt được.

**Khuyến nghị (nếu mở rộng):** thêm trường `lockScope: "totp" | "password"` trong `data`.

### 5.7 ⚠️ Không có kiểm thử hợp đồng tự động

Hiện **không** có test nào đối chiếu đường dẫn/tham số giữa hai phía. Một thay đổi nhỏ ở
`@RequestParam(name="q")` hoặc `@RequestMapping` sẽ **không** làm test nào đỏ, chỉ vỡ khi
chạy tay.

**Khuyến nghị rẻ nhất có tác dụng lớn:** một file test gọi thẳng 5 endpoint chính với axios và
assert hình dạng JSON (`expect(body).toHaveProperty('success')`). Chạy trong CI cùng lúc với
backend bằng Testcontainers hoặc với backend đã chạy sẵn. Đây là điểm cộng đáng kể cho đồ án vì
thể hiện hiểu biết về kiểm thử tích hợp.

---

## 6. Đối chiếu CSDL: 24 bảng entity

Đã trích từ `@Table(name=…)` trong `be-web-admin/src/main/java`:

```
ntt_admins              ntt_moodle_exchange_items   ntt_questions
ntt_ai_suggestions      ntt_moodle_exchanges        ntt_question_revisions
ntt_ai_tasks            ntt_moodle_profiles         ntt_subjects
ntt_auth_audit_logs     ntt_web_sessions            ntt_topics
ntt_backup_codes        ntt_eval_items              ntt_totp_credentials
ntt_chat_conversations  ntt_eval_labels             ntt_issue_types
ntt_chat_members        ntt_eval_predictions        ntt_courses
ntt_chat_messages       ntt_eval_runs               ntt_mobile_devices
```

**Lưu ý quan trọng khi đối chiếu CSDL v4:** `redis` **không phải bảng**. Redis chỉ là cache cho
`totp:fail:{adminId}` (§4.1) và phiên token. Các tài liệu đối chiếu CSDL cũ
(`docs/review-csdl-v4-chot-vs-code.md`) đã tuân thủ nguyên tắc này — 21 bảng trong tài liệu đó
là 21 bảng MariaDB, không tính Redis.

SQL migration nằm ở `be-web-admin/sql/…`, không ép schema bằng Hibernate:

| File | Bảng |
|---|---|
| `2026-11-19-02-business-tables.sql` | subjects, topics, issue_types, questions, question_revisions, ai_tasks, ai_suggestions, moodle_profiles, moodle_exchanges, moodle_exchange_items |
| `2026-11-19-03-eval-tables.sql` | eval_runs, eval_items, eval_labels, eval_predictions |
| `2026-11-18-chat-tables.sql` | chat_conversations, chat_members, chat_messages |
| `2026-11-19-01-auth-drift-fix.sql` | (sửa lệch) auth/audit |

---

## 7. Danh sách kiểm tra trước khi bảo vệ

- [ ] **§5.1** Giảm N+1: thêm `options[]` vào `QuestionListItem` (hoặc `includeOptions=true`)
- [ ] **§5.2** Hằng số `SEARCH_PARAM='q'` trong `qbankApi.js`
- [ ] **§5.3** Nối `F.keyword` trong `SearchView.vue` lên server
- [ ] **§5.4** Thống nhất lời gọi có token qua `SpringConfigHeader` (ghi chú ngoại lệ SSE)
- [ ] **§5.7** Thêm test hợp đồng cho 5 endpoint chính
- [ ] Chạy `npm run check:syntax` trong `qbank-console-vue3` → phải `[OK] 55 file sach cu phap`
- [ ] Chạy `npm run build` → phải `139 modules transformed`
- [ ] Kiểm tra CORS: `application.yaml:28` phải chứa origin thực tế khi triển khai
      (hiện chỉ `localhost:5173/5174/5375`)

---

## Phụ lục — Lệnh chạy để tái lập kiểm chứng

```bash
# Backend (cần MariaDB + Redis)
cd be-web-admin && ./gradlew bootRun

# Frontend
cd qbank-console-vue3
cp .env.example .env      # VITE_DEMO_MODE=false để dùng API thật
npm ci
npm run dev               # http://localhost:5173/#/login

# Kiểm tra cú pháp nhanh (không cần backend)
npm run check:syntax

# Chỉ xem giao diện, không cần backend
VITE_DEMO_MODE=true npm run dev
```
