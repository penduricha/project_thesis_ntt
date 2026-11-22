# Rà review luồng nghiệp vụ 4 project — be-web-admin / fe_web_admin / server-ai-worker / app_mobile

- **Ngày rà soát**: 2026-11-22 (cập nhật 2026-11-22)
- **Nhánh**: `arena/f960d1ff-project-thesis-ntt` @ `90bf3e053` (UI conversion mới nhất đã push)
- **Phạm vi**: rà soát mã nguồn tĩnh (không chạy server — sandbox thiếu JVM/MariaDB/Redis)
- **Nguồn sự thật**: source code thật (BE/FE/mobile/worker), không dựa trên tài liệu cũ
- **Thứ tự ưu tiên**: Spring Boot trước (đậm nhất, dạng báo cáo), sau đó fe_web_admin, server-ai-worker, app_mobile

---

## 0. Bức tranh hệ thống

Hệ thống gồm 4 project, giao tiếp bằng HTTP/WebSocket:

```
┌─────────────┐  REST+SSE  ┌──────────────────┐   REST + WS vé một lần   ┌──────────────┐
│  fe_web_admin│──────────▶│   be-web-admin    │◀───────────────────────▶│  app_mobile  │
│ Vue 3 (web) │  (Bearer) │  Spring Boot 8080 │  (/api/mobile/chat/**)   │ Flutter 5375 │
└─────────────┘            │                  │                          └──────────────┘
                           │  :8080 /api      │   REST + JWT/nội bộ   ┌────────────────┐
                           │  MariaDB+Redis   │──────────────────────▶│ server-ai-worker│
                           └──────────────────┘                         │  Java/Gradle  │
                                                                         │  (port 8082)  │
                                                                         └────────────────┘
```

Nghiệp vụ cốt lõi (4 hướng chính):

| Luồng | Project | Cách |
|---|---|---|
| Đăng nhập web + 2FA (web + mobile dùng CHUNG chuỗi xác thực) | be-web-admin + fe_web_admin + app_mobile | POST `/web/admin/auth/login` (+ alias `/auth/login/totp`) |
| Phiên làm mới ngầm (access 5 phút / refresh 30 ngày), chống dùng lại refresh token | fe_web_admin (silent refresher) + app_mobile (auto-login) | GET `/web|mobile/admin/auth/login/auto/{access,refresh}-token` |
| Cài đặt 2FA: web sinh khóa → web bấm Kích hoạt → mobile xác nhận → web/mobile tắt | be-web-admin (5 giai đoạn) + fe_web_admin + app_mobile | REST 2FA + SSE `/web/admin/2fa/events` |
| Chat nội bộ giữa 2 admin (chỉ văn bản) | be-web-admin + app_mobile | REST `/mobile/chat/**` + WebSocket `/mobile/chat/ws` |
| AI worker cho câu hỏi (nl_search / question_check) | server-ai-worker | REST `/web/ai/**` + Redis queue + Colab/Ollama |

---

## 1. be-web-admin (Spring Boot) — báo cáo lỗi/vấn đề (ưu tiên cao nhất)

### 1.1 CRITICAL — Endpoint chi tiết admin crash khi `course == null`

`AdminServiceImpl.getAdminDetailInformation()` (dòng ~156-161) truy cập `adminFound.getCourse().getIndustry()` (và faculty/trainingSystem/typeOfTraining/major/branch) **không kiểm tra null**. Nếu admin chưa có course (Admin.course không `@NotNull`, không nullable=false ở entity) thì:

- `GET /web/admin/information/detail` → **500 NPE**
- FE `getAdminInformationDetailByAdminId` throws Error → CardInformationDetail hiện trạng thái lỗi

**Khuyến nghị**: ép `course` là non-null trong DB + kiểm tra null trong service; FE nên fallback về dữ liệu trống thay vì throw.

### 1.2 MEDIUM — Web logout không có guard & permitAll

`POST /web/admin/auth/logout` (AdminControllerMgmtWeb):

- `permitAll()` ở SecurityConfig — endpoint không cần auth; FE tự gửi token qua body.
- Nếu **cả hai token null/trống**: `removeAdminAccessTokenByJwtToken(null)` → derived query với tham số null có thể ném ngoại lệ → catch → trả **401 "Can't remove…"** thay vì 400 "missing tokens" như bản mobile (`AdminControllerMgmtMobile` kiểm tra cả hai null đầu tiên và rẽ nhánh 400).
- Không kiểm tra admin tồn tại trước khi xoá token.

(So sánh mobile: `if (accessToken == null && refreshToken == null)` → 400. Web không có.)

### 1.3 MEDIUM — Cột `last_login_at` không bao giờ được ghi

`Admin` có `lastLoginAt` (`datetime(6)`, `last_login_at`) nhưng **không code nào set** nó qua toàn bộ vòng đời đăng nhập (web + mobile). Đây là dữ liệu nghiệp vụ mất đi (không có "lần đăng nhập gần nhất" để thống kê/audit).

### 1.4 MEDIUM — Luồng đăng nhập legacy `AdminServiceImpl.loginAdmin` còn trong code

`AdminServiceImpl` (implements `AdminService`) vẫn còn `loginAdmin(adminId, password)` — luồng đăng nhập CŨ (sinh cặp token → lưu Redis → trả success), **không được controller nào gọi** (web controller dùng `AdminLoginServiceImpl`, mobile cũng dùng `AdminLoginServiceImpl`). Hành vi tương đương nhưng không có TOTP/lock/audit.

Cùng một file, `addAdmin()` / `getAllAdmins()` / `getAdminInfo()` / `getAdminDetailInformation()` / `getAdminNameByAdminId()` là các hàm đang được controller sử dụng — chỉ `loginAdmin()` là dead code (không còn được controller nào gọi).

**Dead code/file** :
- `controller/web/CourseController.java` — class rỗng
- `service/jpa/CourseService.java` + `impl/CourseServiceImpl.java` — interface/class rỗng
- `controller/rabbitMQ/*` — toàn bộ đã comment
- `ServerAiWorkerApplication` không phải — ngoài scope

### 1.5 MEDIUM — Race condition đăng nhập mobile (2 thiết bị cùng lúc)

`AdminControllerMgmtMobile.loginAdminMobile`:

1. Gọi `adminLoginService.login(...)` → cấp cặp token mới, lưu Redis.
2. `sessionLoginMobileService.findByAdminId(adminId)` — nếu có phiên cũ → revoke 2 token này + trả 409.
3. Chưa có phiên → `createSession(...)` — **ghi đè** (cùng 1 khóa `session_mobile:{adminId}`).

2 request song song (2 thiết bị cùng đăng nhập cùng lúc): cả 2 đều qua bước 2 (chưa có phiên), cả 2 vào bước 3 → cả 2 token đã cấp đều sống trong Redis tới 300s/30 ngày, nhưng chỉ 1 phiên Redis tồn tại. **Thiết bị thua cuộc vẫn dùng được token** dù không có phiên (mobile auto-login ở bước 6 sẽ fail vì `existsByAdminId` false → 401 → FE/app tự logout → không có hậu quả nặng, nhưng violate "1 thiết bị" an toàn).

Token thua cuộc có thể nhắc hoàn tác ở đây.

### 1.6 LOW — 5 bản `resolveAdminId` / `resolveAuthenticatedAdminId` giống hệt nhau

- `EnrollSessionControllerWeb`, `EnrollmentController`, `TwoFaEventControllerWeb` (web)
- `EnrollmentConfirmControllerMobile`, `TotpDisableControllerMobile` (mobile)
- `AdminControllerMgmtWeb` (một variSettings khác: dùng SecurityContextHolder thay vì Bearer)

Việc verify lại access token + Redis + trích `adminId` lặp lại 6 lần. Không phải lỗi, nhưng mỗi lần fix (vd đổi thuật toán verify hoặc thêm check) sẽ dễ quên xoá chỗ cũ. Nên gom thành `AdminTokenGuard` service.

### 1.7 LOW — `JwtAuthenticationFilter` fail-open khi Redis lỗi

- `catch Exception` → log warning, **không set authentication** → request tiếp tục vào chuỗi security. `anyRequest().authenticated()` → 401. Vậy thực chất vẫn fail-closed (sẽ 401).
- Riêng cho 2FA/SSE controllers (dùng `resolveAdminId` riêng): Redis lỗi → catch → null → 401. Cũng fail-closed.
- Nhưng: rủi ro cao nếu **sửa SecurityConfig** (vd permitAll cho 1 endpoint phụ) thì lỗi Redis sẽ làm endpoint mặc định fail-open. Ghi chú để tránh sửa nhầm.

### 1.8 LOW — `forward-headers-strategy: native`

`request.getRemoteAddr()` lấy IP thật khi sau nginx/proxy — **chỉ an toàn nếu luôn sau proxy**. Client gọi Spring Boot trực tiếp có thể giả header `X-Forwarded-For` → audit log (`AuthAuditLog.ipAddress`) ghi sai + IP của admin dùng để thu hồi phiên. (Comment trong application.yaml nhắc XÓA dòng nếu có client gọi thẳng.)

### 1.9 LOW — `AdminAccessToken` / `AdminRefreshToken` gắn `@Cache(READ_WRITE)` + `@Cacheable`

Không có `use_second_level_cache: true` ở config (tắt) → các annotation hoàn toàn không hoạt động. Dễ hiểu nhầm là "có cache". (Không phải bug, nhưng gây hiểu lầm — nên xoá hoặc bật cache.)

### 1.10 LOW — `EnrollmentServiceImpl.getStatus` bỏ qua stale pending credential

Khi credential pending đã vượt confirm window (10 phút), `getStatus` trả `(false, false, 0, null)` — nhưng credential vẫn còn trong DB; chỉ khi mobile có confirm request thì mới abort (xóa credential). Kết quả: trang web báo "Chưa kích hoạt" đúng — trên mobile vẫn giữ credential chờ — chấp nhận được, nhưng lưu ý.

### 1.11 INFO — Chat module (chat.**): thiết kế tốt

- `ChatRateLimiter` fail-open khi Redis lỗi (documented, chấp nhận cho chat liveness).
- `ChatSessionRegistry` + `TwoFaEventServiceImpl` — in-memory, giới hạn multi-instance (phải Redis Pub/Sub).
- `ChatSecurityConfig` — chat có chuỗi bảo mật riêng, `/mobile/chat/ws` permitAll (handshake xác thực vé một lần ở filter trước handshake) — thiết kế kỹ.

### 1.12 INFO — App.java tốt (chủ yếu)

- JWT: ký HS256, 5 phút access / 30 ngày refresh, **không hard-code secret** (JwtSecretGuard block).
- 2FA: AES-256-GCM, HKDF-SHA256, AAD = "totp:{adminId}:v{keyVersion}", kek protect secret cipher; `keyVersion` recorded; anti-replay (`totp:used:{adminId}:{step}`); anti-brute-force lockout (5 lần / 5 phút); lock + replay + verify window ±1 step; confirm attempt counter chống song song (`INCR` + reset sau confirm).
- Append-only audit log (`AuthAuditLog`) — ip/userAgent/detail truncate 255 char; REQUIRES_NEW transaction.
- CORS: `allowedOriginPatterns` (không dùng `*`), `allowCredentials=true` — hợp lệ với patterns.
- CSRF disable + STATELESS + Bearer-only — hợp lý cho SPA/mobile.

---

## 2. fe_web_admin (Vue 3) — luồng nghiệp vụ

### 2.1 Luồng đăng nhập & giữ phiên (rõ ràng, tốt)

`main.ts` bootstrap tuần tự:
1. `setupCrossTabLogout()` + `setupSilentRefresh()` (init, chưa start) — BroadcastChannel `auth-sync`.
2. `autoLogin()`: có refresh token → `adminService.getDataToAutoLoginAccessToken()`. Access token expired → silent refresh (single-flight + Web Locks + Cross-tab) → retry.
3. Route guard: không token → `/login`; có token + route không tồn tại → `/404-not-found`; ở `/` → khôi phục localStorage path.
4. `silentRefresher.start()` nếu đã đăng nhập (tầng tự động làm mới ngầm trước khi hết hạn, 60s).

`login/2FA`: LoginPage → `AdminRepository.getLoginResult()` → POST `/auth/login` (1 request duy nhất gồm adminId + password + totpCode). Server trả `TOTP_REQUIRED | TOTP_INVALID | LOCKED | OK` → FE xử lý: tô đỏ, đếm ngược, khóa form. ✅ Không lặp "đăng nhập 2 bước" — một request đơn, rõ ràng.

### 2.2 2FA trên web (đúng chuẩn backend)

`ModalActivate2FA.vue` + `CardSettingTwoFA.vue`:
- Mở modal → `POST /2fa/enrollment` → hiện khóa Base32 (no-store). Đóng modal → `DELETE` (cancel pending, bỏ TTL Redis 5 phút).
- Bấm Kích hoạt → `POST /2fa/enrollment/activate` → 201 → khóa vào MariaDB (confirmed=false, chờ mobile xác nhận) → modal đóng → CardSettingTwoFA chuyển "Đang chờ xác nhận".
- Poll 3s + SSE push (`watchTwoFaStatus`, exponential backoff 1s→30s + polling dự phòng) → khi server báo ENABLED → chuyển "Đã kích hoạt".

**PR-NOTE**: `CardSettingTwoFA` **không hiển thị `expiresInSeconds`** khi NOT_ENABLED (đúng), nhưng không hỗ trợ hủy pending do hết confirm window một cách tự động (FE không abort credential sau 10 phút — chỉ hiển thị status; server xóa khi mobile confirm lần tiếp theo).

### 2.3 Vấn đề/vấn đề nhỏ

| Mức | Vấn đề |
|---|---|
| LOW | TopBar role **hardcode** "Quản trị viên" + logo badge "QB" — không đọc `admin.role` từ `/information/basic` |
| LOW | TopBar TopBar `userRole` và `userName` không liên kết với `AppShell.handleLogout` — logout đi qua `AppShell`, chưa clear TopBar identity (sau reload về /login OK) |
| LOW | `.visually-hidden` class thiếu ở LoginPage/FormBanner — 1 vài label chỉ hiện mờ, không nhìn thấy |
| LOW | `routers_after_login.ts` — eager import 15 pages (Dashboard, Bank, Courses, Search, Review, Tasks, Moodle, Admin, Llm, Settings, Information, Login, PageNotFound, ...) → bundle nặng (chưa lazy-load) |
| LOW | Route `/login` được `beforeEach` cho phép cho user đã đăng nhập (allow undefined) → FE cho phép đăng nhập lần 2; minor |
| INFO | `appStore.ts` — 10 shell page (dashboard/bank/courses/search/review/tasks/moodle/admin/llm/settings) vẫn là mock data (chưa gọi API thật) — dự kiến UI-first |
| LOW | Không có test tự động (frontend) — không có vitest/jsdom |
| INFO | `routers_after_login` import trực tiếp RouterMgmt cho từng route — RouterMgmt tạo `sessionStorage/localStorage` instance mỗi lần gọi; ổn nhưng không singleton |

### 2.4 Luồng giữ phiên — tốt

- `SilentTokenRefresher`: single-flight + Web Locks + cross-tab `storage` event + visibility change/online event — robust.
- `getAuthorizedAPIClient()` interceptor: trước request refresh nếu sắp hết hạn; 401 → refresh 1 lần → gửi lại. `refreshNow` với `notifyExpired: false` ở boot — chính xác.
- Cross-tab logout broadcast: logout → broadcast → tab khác auto logout.

---

## 3. server-ai-worker (Java/Gradle) — luồng nghiệp vụ

### 3.1 Bức tranh

2 hướng chính:
- **nl_search / question_check** — tạo task → Redis queue → `AiDispatcher` (pool workers) → `AiTaskProcessor` (retry in-place ×3 / requeue) → Colab/Ollama → parse kết quả → lưu `ai_tasks` + `ai_suggestions` + cache theo promptVersion.
- **nodes / overview / burst / issue-types** — quản lý trạng thái 5 node colab trong NodePool (in-memory), kiểm tra bằng /health + /api/chat, scheduler + heartbeat.

### 3.2 Bảo mật (tốt)

- `AiAuthFilter`: Bearer JWT verify (HS256/384/512, exp, token_type=="ACCESS", sub) **hoặc** internal token constant-time; `AiSecurityGuard` fail-fast (JWT_SECRET >= 32 byte / internal token >= 16 char / không dùng dev secret).
- **SSRF guard** trong NodePool: chỉ https + suffix `.trycloudflare.com` (suy từ config `ai.llm.allowed-host-suffixes`) + không cho path/query/userinfo/port (chỉ default 443) — chặt.
  - Chất lượng tốt, đáng khen.
- CORS allowlisted (không dùng `*`), allowCredentials=false không (không dùng credentials). Không leak token trong NodeView (xem NodeView record: `hasToken: boolean`).

### 3.3 Vấn đề/vấn đề nhỏ

| Mức | Vấn đề |
|---|---|
| INFO | `JPA_DDL_AUTO=update` mặc định — docs khuyên `validate` khi ghép ER v4 (đã ghi trong comment application.yml) |
| INFO | `NodePool` in-memory (URL/token ko persist qua restart — env/.env reload qua @PostConstruct) |
| LOW | `AiTaskProcessor` — `maxAttempts` đọc từ `props.getOrchestrator().getMaxAttempts()` = 5; nếu config sai (vd set lớn hơn 5) thì coi như 5 — nhưng ở `sweepStuck` cũng max 5 → consistent |
| LOW | `submitBurst` không check `createdBy` có trong DB → burst vẫn lưu task với `createdBy = 1` mặc định (demo) — dữ liệu demo only, hợp lý |
| LOW | `OllamaClient` — tầng retry in-place (max 3) + requeue — tốt. Bug: không distinguish `LLM_UNAUTHORIZED` 401 vs 403 — treat cả hai → LLM_UNAUTHORIZED — hợp lý |
| INFO | `AiResultParser` (500 dòng) + `AiPromptFactory` (302) + `RulePreflightChecker` (124) + `TextLocator` (281) — không đọc kỹ — flag cho review trước khi publish |
| INFO | Không có "idempotency" cho task — submit lại cùng request nhiều lần sẽ tạo task mới từng lần — chấp nhận cho demo |

### 3.4 Luồng SSE/WS

Server không có SSE — chat/ws flow đi qua be-web-admin (không từ server-ai-worker). Worker là microservice độc lập. ✅ Kiến trúc clean.

---

## 4. app_mobile (Flutter/Dart) — luồng nghiệp vụ

### 4.1 Luồng đăng nhập & giữ phiên

`AutoLoginController` (splash): readSession → access token (200 → home) → 401 → refresh token (200 → lưu cặp token mới + adminName → home) → 401 → clearSession → login. **Lỗi mạng ≠ token hỏng** → giữ phiên + vào login. ✅ Đúng design.

`LoginController` — lấy deviceName + platform (DeviceInfoService) + TOTP code tự sinh từ local `AccountStore` (key `authenticator_accounts_{adminId}`, legacy migration) → POST `/mobile/admin/auth/login` (một request, dùng chung chuỗi xác thực đã có trên web). ✅ Đúng design.

### 4.2 Phiên refresh & 2FA

`SecureStorageEnrollmentAuth` — `accessToken()` đọc storage; `refreshAccessToken()` single-flight (static `_refreshing`); sau khi refresh vừa đủ → saveSession — trong cùng 1 lần refresh, dùng lại access token mới. **Chat + enrollment dùng chung** với `ChatApiService` (chat/api/token-retry). ✅ Làm mới Single-Flight chính xác.

`EnrollmentService`:
- activate(): fetch `/2fa/enrollment/pending` → generate mã từ (secret, pending) → POST `/confirm` → server bật 2FA.
- disable(): POST `/disable` với code hiện tại → 200/404 → app xóa khóa cục bộ. 422 → ném lỗi → FE xử lý.
- **Không auto-retry confirm** — đúng vì server đếm attempts (max 5).

### 4.3 2FA trên mobile — tốt

- Anti-replay, anti-brute-force, chống song song — server xử lý; app KHÔNG thử lại confirm → không vượt giới hạn server. ✅
- `EnrolledKey` — lưu period/digits/algorithm từ server — FE dùng đúng. ✅
- 404 → "alreadyOff" → FE xóa khóa cục bộ. ✅

### 4.4 Chat trên mobile — tốt

`ChatSocketService` — xin vé qua REST (rate limit 10/phút) → mở WS `?ticket=...` (dead-plaintext URL không lộ JWT) → ping 25s (interval từ ready) → pong timeout 10s → backoff 1,2,4,8,16 → 30s cap → reset 1s sau 10s ổn định. Mã 4001 → thoát, không retry. ✅

### 4.5 Vấn đề/vấn đề nhỏ

| Mức | Vấn đề |
|---|---|
| LOW | `LoginController` — không lưu `deviceName` của phiên mobile này nếu không có phiên cũ (cập nhật tên hiện tại trong local?); không xóa phiên mobile cũ — chấp nhận (server là nguồn sự thật) |
| LOW | `AccountStore` legacy migration — tự xóa legacy key; 1 lần là đủ — OK |
| INFO | `LogoutController` + `LogoutService` — POST `/mobile/admin/auth/logout` — đợi server xong trước khi clear local session — OK |
| INFO | `DeviceInfoService` — `deviceName` dùng cho audit log — không yêu cầu — OK |
| LOW | `_dio` validateStatus trên Dio 5 — endpoint 401 trả 401 → interceptor retry trên endpoint refresh — OK. `EnrollmentService` dùng `Dio(validateStatus: s != null && s < 500)` — một số response dưới 500 nhưng cần handle 422/429 — FE tự đọc status code — OK |

---

## 5. Ma trận tóm tắt vấn đề (cập nhật sau khi rà)

### Spring Boot (be-web-admin)

| # | Mức | Vấn đề | File |
|---|---|---|---|
| S1 | CRITICAL | NPE khi admin.course == null → 500 `/information/detail` | `service/jpa/impl/AdminServiceImpl.java:156-161` |
| S2 | MEDIUM | Web logout không có null guard; permitAll + derived query null có thể throw → 401 thay vì 400 | `controller/web/AdminControllerMgmtWeb.java` + `service/redis/impl/AdminTokenServiceImpl.java` |
| S3 | MEDIUM | `lastLoginAt` (column `last_login_at`) không bao giờ được ghi — mất dữ liệu audit | `model/jpa/Admin.java:79` + registration controller |
| S4 | MEDIUM | Luồng đăng nhập legacy `AdminServiceImpl.loginAdmin` chưa dùng được; không TOTP/lockout/audit | `service/jpa/impl/AdminServiceImpl.java` |
| S5 | MEDIUM | Race condition mobile login (2 device cùng lúc) — token thua cuộc vẫn sống trong Redis | `controller/mobile/AdminControllerMgmtMobile.java:101-115` |
| S6 | LOW | `JwtAuthenticationFilter` fail-open khi Redis lỗi — không set auth (chain 401 — fail-closed nhưng dễ nhầm nếu sửa SecurityConfig) | `security/jwt/JwtAuthenticationFilter.java` |
| S7 | LOW | 6 bản `resolveAdminId` / `resolveAuthenticatedAdminId` trùng lặp | 2-7 file |
| S8 | LOW | `forward-headers-strategy: native` — X-Forwarded-For giả mạo nếu gọi thẳng vào Spring Boot | `application.yaml:server` |
| S9 | LOW | `@Cacheable`/`@Cache(READ_WRITE)` trên `AdminAccessToken/AdminRefreshToken` nhưng second-level cache tắt — annotation vô dụng | `model/redis/AdminAccessToken.java:31-36` |
| S10 | LOW | `getStatus` bỏ qua stale pending credential (10 phút confirm window) — FE hiển thị "Chưa kích hoạt" sai một phần | `service/redis/impl/EnrollmentServiceImpl.java:201-220` |

### fe_web_admin

| # | Mức | Vấn đề | File |
|---|---|---|---|
| F1 | LOW | TopBar role/badge hardcode "Quản trị viên"/"QB" | `components/shell/TopBar.vue` |
| F2 | LOW | `.visually-hidden` thiếu | `pages/login-page/LoginPage.vue` |
| F3 | LOW | 15 route pages eager import (bundle nặng) | `router/routers_after_login.ts` |
| F4 | LOW | Đăng nhập user vẫn vào /login (allow undefined) | `main.ts:86-98` + `routers_after_login.ts` |
| F5 | INFO | 10 shell pages vẫn mock data | `stores/appStore.ts` |
| F6 | INFO | Không có test frontend | (toàn repo) |

### server-ai-worker

| # | Mức | Vấn đề | File |
|---|---|---|---|
| W1 | INFO | `JPA_DDL_AUTO=update` mặc định — nên `validate` cho ER v4 | `application.yml` |
| W2 | INFO | `NodePool` in-memory (URL/token restart từ env) | `service/ai/NodePool.java` |
| W3 | LOW | `submitBurst` không check `createdBy` trong DB — demo only | `service/ai/AiTaskService.java` |
| W4 | INFO | Không đọc kỹ `AiResultParser` (500 lines) / `AiPromptFactory` (302) / `TextLocator` (281) | `service/ai/*` |

### app_mobile

| # | Mức | Vấn đề | File |
|---|---|---|---|
| M1 | LOW | Không cập nhật device info cho phiên mới — server là nguồn sự thật | `login_controller.dart` |
| M2 | INFO | Dio validateStatus<500 — không ném lỗi cho 4xx — FE phải tự xử lý code | `spring_config_header.dart` + `enrollment_service.dart` |
| M3 | INFO | `LogoutController` + session clear — OK | `main_shell/logout_service.dart` |

---

## 6. Kết luận tổng quan

**Spring Boot**: kiến trúc tốt, bảo mật tốt (HS256/AES-256-GCM/HKDF/anti-replay/anti-brute-force/audit log). Sức mạnh của 2FA flow (web→MariaDB→mobile confirm→SSE) là đáng nể. Vấn đề thực tế: NPE ở `/information/detail` (1.1), thiếu việc cập nhật `last_login_at` (1.3), web logout không guard (1.2), và 1 chút legacy trong `AdminServiceImpl` (1.4). Mức độ ưu tiên cao nhất là S1.

**fe_web_admin**: silent refresher rất tốt, login/2FA flow đúng chuẩn, polling + SSE hợp lý. Vấn đề còn lại của repo: UI shell dùng mock data (10 pages), thiếu `.visually-hidden`, chưa lazy-load route, role TopBar hardcode.

**server-ai-worker**: SSRF guard, fail-closed security, in-memory pool với circuit breaker — thiết kế mạnh mẽ. Lý do 2FA/token giữ nhẹ, `AiResultParser`/`AiPromptFactory` chưa đọc kỹ.

**app_mobile**: auto-login + refresh + 2FA + WS chat + chat REST — thiết kế hợp lý, anti-replay không auto-retry confirm — đúng. Vấn đề nhỏ: không lưu deviceName cho phiên mới (consumer không xóa phiên cũ), Dio validateStatus<500 — FE cần tự xử lý code 4xx.

**Chưa rà kỹ**: `AiResultParser` (500), `AiPromptFactory` (302), `rulePreflightChecker` (124), `TextLocator` (281), `AiResultParser` và chat backend (`ConversationService` 321 dòng, `MessageService` 180 dòng). Cần rà lại trước khi publish/deploy.

**Đề xuất next step**: (1) Fix S1 (NPE); (2) Fix S3 (ghi `last_login_at`); (3) Fix S2 (web logout null-guard); (4) Fix S4 (dọn legacy); (5) chính lại S5 (race condition mobile); (6) Sau đó rà FE/UI theo yêu cầu "suy nghĩ luồng nghiệp vụ VUEJS FE mới".

---

## 7. Fixes applied (2026-11-22) — 5 điểm Spring Boot

| ID | Vấn đề gốc | Fix | File đã đổi |
|---|---|---|---|
| **S1** | NPE ở `/information/detail` khi admin.course == null | `getAdminDetailInformation()` giờ null-check course; admin không có course → trả riêng thông tin admin (address, idCard) thay vì 500 | `service/jpa/impl/AdminServiceImpl.java` |
| **S3** | Cột `last_login_at` không bao giờ được ghi | `AdminLoginServiceImpl.issueTokens()` ghi `admin.setLastLoginAt(Instant.now())` + save, best-effort (lỗi ghi không hỏng luồng đăng nhập). Dùng cho cả web + mobile (cả hai gọi cùng service) | `service/jpa/impl/AdminLoginServiceImpl.java` |
| **S2** | Web logout: thiếu token → 401 thay vì 400 | Web logout giờ kiểm tra cả hai token null/trống đầu tiên → **400** "Missing access token and refresh token." (khớp mobile); chỉ xoá token không null; log qua SLF4J thay System.out | `controller/web/AdminControllerMgmtWeb.java` |
| **S4** | Legacy/dead code | Xóa: `AdminServiceImpl.loginAdmin()` + `comparePasswordBcrypt()` + imports + constructor param không dùng; `AdminService` interface (bỏ `loginAdmin`); `CourseController.java` (rỗng); `CourseService.java` (rỗng); `CourseServiceImpl.java` (rỗng); RabbitMQ package (AdminConsumer/AdminProducerController/RabbitMQConfig — toàn bộ comment); các khối legacy comment trong AdminControllerMgmtWeb | `service/jpa/*`, `controller/web/CourseController.java`, `controller/rabbitMQ/*`, `service/jpa/impl/AdminServiceImpl.java` |
| **S5** | Race condition mobile login (2 thiết bị cùng lúc) | Đổi `createSession()` (ghi đè) → `claimSession()` (kim tra tồn tại + tạo phiên **nguyên tử** qua `synchronized(this)` trên service). Controller gọi claimSession; nếu false → revoke token + trả 409 + đọc phiên cũ để cảnh báo. `buildAlreadyLoggedInWarning` xử lý session null | `service/redis/SessionLoginMobileService.java` + `service/redis/impl/SessionLoginMobileServiceImpl.java` + `controller/mobile/AdminControllerMgmtMobile.java` |

**Nguyên tắc fix**: không đổi hành vi API (endpoint, payload, status code ngoại trừ web logout 400 theo đúng contract mobile), không thêm dependency, không commit/push trừ các fix Spring Boot này.

**Chưa làm / cần lưu ý**:
- Không rà build (sandbox thiếu JVM/Gradle). Cần chạy `./gradlew compileJava` + `./gradlew bootRun` trên máy thật để verify.
- Không sửa nhiều file khác (S6/S7/S8/S9/S10 giữ nguyên — ghi chú cho lần rà tiếp).

---

## 8. Rà UI VUEJS FE — luồng nghiệp vụ (cho việc rethink FE)

### 8.1 Luồng end-to-end hiện tại (đúng chuẩn)

```
LoginPage → handleLoginEvent
  └─ validate (adminId + password bắt buộc; totp optional, format 6-digit)
  └─ AdminRepository.getLoginResult(adminId, password, totpCode|null)
       └─ POST /auth/login | /auth/login/totp  (1 request duy nhất)
  └─ OK → handleRememberMe → navigateToMainPage → /information-admin
          (save sessionStorage path + silentRefresher.start())
  └─ TOTP_REQUIRED → tô đỏ ô mã 6 số, focus, KHÔNG clear password
  └─ TOTP_INVALID → clear ô mã, focus ô mã
  └─ LOCKED → khóa form đếm ngược (server là nguồn sự thật), persist lock ở localStorage
  └─ FAILED → tô đỏ adminId/password, clear password

AppShell mount → loadIdentity (TopBar) → /information-admin mount
  ├─ CardAccount → GET /information/basic (auto-login first)
  ├─ CardInformationDetail → GET /information/detail
  ├─ CardSettingTwoFA → GET /2fa/status (polling 3s + SSE push)
  │   └─ SSE watchTwoFaStatus: exponential backoff 1s→30s + polling dự phòng
  │   └─ ModalActivate2FA → POST /2fa/enrollment (sinh khóa) → POST /2fa/enrollment/activate (ghi MariaDB)
  │       └─ đóng modal → DELETE /2fa/enrollment (cancel Redis)
  │   └─ nút "Hủy và cài lại" → DELETE /2fa/enrollment/pending
  └─ AppShell logout → POST /auth/logout (400 nếu thiếu token) → broadcast logout (BroadcastChannel) → reload /login
```

### 8.2 Vấn đề UI / logic nghiệp vụ

**[UI-1] TopBar hardcode role & badge**
- `TopBar.vue`: `userRole = ref('Quản trị viên')`, `userName = 'Đang tải…'` → sau identity load vẫn hardcode "Quản trị viên", không đọc `admin.role` (Admin entity có ROLE enum: ADMIN/LECTURER).
- **Đề xuất**: lấy `role` từ `/information/basic` (thêm field `role` vào Map response); badge "QB" nên đổi theo tên thật.

**[UI-2] 10 shell pages mock data**
- `stores/appStore.ts` giữ toàn bộ dữ liệu mock (questions, courses, users, tasks, LLM config). Dashboard/Bank/Courses/Search/Review/Tasks/Moodle/Admin/Llm/Settings đều UI-first.
- **Đề xuất**: đánh dấu rõ trong UI (hoặc banner) "mock data / UI demo"; không để người dùng nhầm lẫn cấu hình LLM thật với dữ liệu backdrop.

**[UI-3] `.visually-hidden` thiếu**
- `.visually-hidden` không có trong ql-shell.css — login form có label dùng class này (hoặc code cũ), ô input bị mờ. Không hỏng nhưng accessible.
- **Đề xuất**: thêm `.visually-hidden` vào ql-shell.css; kiểm tra mọi label dùng class này.

**[UI-4] Eager import routes (bundle nặng)**
- `routers_after_login.ts` import trực tiếp 15+ pages → bundle lớn, mở chậm.
- **Đề xuất**: dynamic import (`() => import('@/pages/...')`) cho các shell pages; lazy cho InformationPage/LoginPage sau first paint.

**[UI-5] Đăng nhập user rồi vào `/login`**
- `routers_after_login.ts` không đặt `allow:false` trên `/login`; `beforeEach` không chặn user đã đăng nhập vào `/login` (guard: hasSessionTokens + route allow undefined → return true).
- **Đề xuất**: đặt `allow:false` trên `/login` (giống routers_before_login) → tự redirect về dashboard.

**[UI-6] CardSettingTwoFA thiếu aborting pending tự động sau confirm window**
- Polling 3s phát hiện credential chờ vượt confirm window (10 phút) nhưng không xóa; hiển thị "Chưa kích hoạt" (đúng) nhưng credential vẫn còn trong DB.
- **Đề xuất**: trong `fetchTwoFaStatus`, nếu `pendingExpiresInSeconds <= 0` (state PENDING quá hạn), tự gọi `cancelPendingEnrollment` 1 lần + set status NOT_ENABLED. Lưu ý nên có throttle (chỉ 1 lần).

**[UI-7] Logout / 2FA lock lockout**
- `LoginPage.restoreLock` lưu `login.totpLock.until` trong localStorage — giữ khóa qua reload. Nhưng **server vẫn có lock window** — FE không đọc retryAfterSeconds server mới được cấp. ✅ server là nguồn sự thật, FE chỉ là cache.

**[UI-8] Auto-login quán tính**
- `getAdminInformationBasicByAdminId` trigger `getStatusAutoLogin` cho MỖI component mount → 4+ requests auto-login khi vào /information-admin. Mỗi request gọi access-token endpoint; token đã tồn tại nên chỉ là kiểm tra — chấp nhận được, nhưng nên cache `isAutoLoggedIn` trong Pinia/appStore thay vì gọi 4 lần.
- **Đề xuất**: gom auto-login check vào một store/composable dùng chung (`useAutoLogin`) — chỉ gọi 1 lần mỗi phiên.

**[UI-9] SSE watchTwoFaStatus không có stale-guard**
- `watchTwoFaStatus` trong EnrollmentRepository: `onOpen` gọi `onChange(null)` → `fetchTwoFaStatus` mỗi lần reconnect — OK; `twoFaFetchSeq` bỏ response cũ — OK. Chưa xử lý: sau 30s backoff, watch cùng tồn tại khi component unmount — đã có `stopWatchTwoFa`. ✅

**[UI-10] `silentRefresher.stop()` + force reload ở /login**
- `clearSessionAndGoToLogin` dùng `window.location.replace('/login')` — hard reload, drop toàn bộ state — đúng cho session expired.
- Hợp lý — không đổi.

### 8.3 Flow hiện tại đã verified khớp backend

| Luồng FE | Backend | Khớp |
|---|---|---|
| LoginPage (1 request + toast/lockform) | POST /auth/login + /auth/login/totp (cùng handler) | ✅ |
| Silent refresher + interceptor | GET auto/access + auto/refresh (rotate, Redis) | ✅ |
| ModalActivate2FA (start → activate → close → cancel) | POST/DELETE /2fa/enrollment, POST /2fa/enrollment/activate, DELETE /2fa/enrollment/pending | ✅ |
| SSE events | GET /2fa/events (SSE) | ✅ |
| Logout (AppShell) | POST /auth/logout (400/404/401) → broadcast + reload | ✅ |
| Information page | GET /information/basic + /detail | ✅ |

### 8.4 Khuyến nghị cho "suy nghĩ luồng nghiệp vụ FE mới"

1. Đánh dấu rõ page thật vs mock (banner hoặc status tracking).
2. Silent refresher + session lifecycle — giữ nguyên cấu trúc, chỉ tách auto-login check ra composable dùng chung.
3. 2FA flow — thêm auto-abort pending khi vượt confirm window (UI-6) + throttle.
4. Logout + cross-tab — giữ nguyên.
5. Lazy routes + `allow:false` /login + role/Badge lấy từ API — dọn bundle và UI-1/2/3/4/5.
6. Đảm bảo responsive topbar/shell cho thiết bị nhỏ (đã có/partial — kiểm tra thủ công trước khi commit).
