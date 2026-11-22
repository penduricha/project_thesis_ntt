# Rà soát logic Vue.js ↔ Spring Boot — bản nhánh cũ vs nhánh hiện tại

- **Ngày rà soát**: 2026-11-22
- **Nhánh gốc**: `8611e8310` (UI_Accept_v9, trước khi chuyển giao diện) — 6 file BE + FE chính được đối chiếu toàn bộ
- **Nhánh hiện tại**: `arena/f960d1ff-project-thesis-ntt` @ `a61453fe7` ("UI fidelity: account page square cards + #pact + logo subtitle per v9")
- **Phạm vi**: chỉ rà soát + viết tài liệu — KHÔNG viết lại UI, KHÔNG commit/push
- **Nguồn sự thật**: code nguồn tĩnh (FE + BE), không chạy server (sandbox thiếu JVM/MariaDB/Redis)

---

## 1. Bức tranh của nhánh cũ (8611e8310) — luồng 6 chiều

### 1.1 Bootstrap & giữ mạch phiên

`main.js` (~80 dòng) thực thi tuần tự:

1. `setupCrossTabLogout()` — lắng nghe `BroadcastChannel('auth-sync')`: tab khác broadcast `LOGOUT` → tab này thoát phiên + reload `/login`.
2. `setupSilentRefresh()` — init `silentRefresher` với `onSessionExpired: clearSessionAndGoToLogin` (dừng bộ làm mới ngầm, xoá path-storage, sessionStorage, `location.replace('/login')`).
3. `autoLogin()` — `TokenLogin` đọc storage → nếu có refresh token thì gọi `adminService.getDataToAutoLoginAccessToken()` → trả `isLoggedIn`. **Không throw** (lỗi mạng = "chưa đăng nhập" để mount được trang).
4. `resolveTargetPath(path, routes, isLoggedIn)`:
   - Chưa đăng nhập + route không tồn tại hoặc `allow===false` → `/login`
   - Đã đăng nhập + route 404 → `/404-not-found`
   - Ở `/` → `localStorage path` (lưu khi đăng nhập) hoặc route đầu
   - Phục hồi `sessionStorage path` nếu khớp bộ route hiện tại
5. Chọn bộ route: `routersAfterLogin` (đăng nhập) hoặc `routersBeforeLogin` (chưa đăng nhập).
6. `silentRefresher.start()` nếu đã đăng nhập (tầng 3: hẹn giờ làm mới trước khi hết hạn).
7. `mountApp(routes, targetPath)` — tạo SPA + `beforeEach` guard:
   - Không có token → route `allow:false` → `/login`; không có route hoặc không khớp → `/login`
   - Đã có token + route không khớp → `/404-not-found`
   - `await router.replace(targetPath)` trước khi `app.mount('#app')` → không nháy trang sai

### 1.2 Đăng nhập & TOTP (một request duy nhất)

`LoginPage.vue` (JS, không thuộc đợt UI lần này — GIỮ NGUYÊN hành vi):

1. `created` → lưu path hiện tại vào sessionStorage; `mounted` → clear focus initial (desktop).
2. `handleLoginEvent()`:
   - Validate ô: adminId/password bắt buộc, ô mã 6 số tuỳ chọn (bắt buộc check format 6 chữ số nếu nhập).
   - `AdminRepository.getLoginResult(adminId, password, totpCode)` — adminId/password luôn gửi; `totpCode` null nếu trống.
   - `AdminService.getLoginResult` → POST `/auth/login` (hoặc `/auth/login/totp` giữ lại cho client cũ — thực tế BE dùng `@PostMapping({"/auth/login", "/auth/login/totp"})` **cùng một handler**).
   - Mapping status: `{success, data}` → `OK` / `FAILED` / `EXCEPTION` / `TOTP_REQUIRED` / `TOTP_INVALID` / `LOCKED` (LOCKED kèm `retryAfterSeconds` từ server).
   - `OK` → `handleRememberMe()` (gắn `RememberMe` vào localStorage) → `silentRefresher.start()` → navigate `/information-admin` (path lưu vào localStorage để bootstrap phục hồi).
   - `TOTP_REQUIRED` → lỗi ô mã ("tài khoản đã bật 2 lớp"); `TOTP_INVALID` → clear mã + lỗi ô mã; `LOCKED` → khóa form cả form + đếm ngược + **persist lock** vào `localStorage('login.totpLock')` để F5 không mất đếm ngược; `FAILED` → sai thông tin đăng nhập; default → SERVER_ERROR.
   - Timeout 20s (`withTimeout`) — form không bị kẹt vĩnh viễn.
   - F5 giữa lúc khóa: `restoreLock()` dựng lại đếm ngược từ localStorage.
   - Focus smart: chỉ focus trên máy có hover/pointer fine (không bật bàn phím ảo trên mobile).

BE (`AdminControllerMgmtWeb` + `AdminLoginServiceImpl`):
- Chưa bật 2FA → trả token ngay (kể cả khi có totpCode gửi lên — không bắt buộc).
- Đã bật 2FA (chỉ tính credential đã `confirmed=true`):
  - thiếu mã → `TOTP_REQUIRED`
  - sai/hết hạn/dùng sai → `TOTP_INVALID` (+ đếm fail, Redis key `totp:fail:{adminId}`, 5 lần / 5 phút → `LOCKED` + `retryAfterSeconds`)
  - đúng → OK
- Không ghi mã người dùng nhập vào log. Chống timing lộ admin tồn tại: BCrypt luôn chạy kể cả admin không tồn tại.

### 1.3 Auto-login & làm mới token ngầm

`services/auth/SilentTokenRefresher.js` (giữ nguyên trong nhánh hiện tại):

- **Chủ động**: hẹn giờ khi access token còn ≤ 60s (đọc `exp` từ JWT). Fallback 4 phút mỗi 4 phút nếu không parse được exp.
- **Thức dậy**: `visibilitychange` + `online` → `tick()`.
- **Bị động**: `retryOn401` cho fetch (2FA SSE) + `getAuthorizedAPIClient` response-interceptor cho axios.
- **Single-flight**: `inflight` promise + Web Locks (`auth-token-refresh`) → chỉ 1 lần refresh server tại 1 thời điểm / multi-tab.
- `refreshNow(staleToken, {notifyExpired})`:
  - Storage đã có token khác (tab khác vừa làm mới) → dùng luôn.
  - Không có refresh token → `expire()` → `onSessionExpired` (→ logout state + reload login).
  - Gọi `adminService.getDataToRenewTokens()` (rotate refresh token) → lưu cặp token mới (access+refresh) → reschedule.
  - Lỗi mạng/server (5xx): **KHÔNG** đăng xuất; chỉ retry 5s → 15s → 30s.
- `getDataToAutoLoginAccessToken` (dùng khi khởi động): token cũ + `401` → silent refresh 1 lần → xem lại. Sau khi login (refresh token cũ) thành công → bật `start()`.

`getDataToAutoLoginAccessToken` (AdminService) — kiểm tra token còn hạn + tồn tại trong Redis (BE `/auth/login/auto/access-token`) — dùng ở bootstrap và ở mọi repository method (`getAdminInformationBasic`, `getAdminInformationDetail`):

- Auto-login thất bại (refresh hết hạn) → `#clearSession` (xoá token) → trả false → component thấy "không đăng nhập".
- Lỗi mạng (5xx/timeout) → **rethrow**, không đăng xuất.

### 1.4 Đăng xuất & cross-tab

- **Đăng xuất chính**: `CardAccount` (hoặc shell TopBar trong giao diện mới) → `AdminRepository.getResponseFromLogoutRequest()` → POST `/admin/auth/logout` (body: `{accessToken, refreshToken}`) → 200 OK → `#clearLoginSession` (xoá 2 token + adminId + login.totpLock) → `silentRefresher.stop()` → `broadcastLogout()` (BroadcastChannel → các tab khác cũng tự đăng xuất + reload) → `window.location.replace('/login')`.
- BE `/admin/auth/logout`: xoá 2 token trong Redis; lỗi → 401 (không khai báo token đăng xuất, không xoá localStorage).

### 1.5 Endpoints thực tế (BE 8611e8310 → 2x FE)

| Endpoint | Method | FE gọi | Ghi chú |
|---|---|---|---|
| `/api/web/admin/auth/login` | POST | `/auth/login` (+ legacy `/auth/login/totp`) | 1 request duy nhất: `{adminId, password, totpCode?}` → `OK` / `TOTP_REQUIRED` / `TOTP_INVALID` / `LOCKED` / `FAILED` / `EXCEPTION` |
| `/api/web/admin/auth/logout` | POST | `getDataToLogoutRequest` | body `{accessToken, refreshToken}`; 200 `status:200` |
| `/api/web/admin/auth/login/auto/access-token` | GET (Bearer) | `getDataToAutoLoginAccessToken` | kiểm tra access token còn hạn + tồn tại Redis |
| `/api/web/admin/auth/login/auto/refresh-token` | GET (Bearer refresh) | "rotate" khi refresh ngầm | xóa refresh cũ, tạo cặp token mới; **lỗi → 401** (bản cũ trả 200 với thân rỗng → gây "log out oan" trong Vue → BE đã sửa) |
| `/api/web/admin/information/basic` | GET `?adminId=` | `getAdminInformationBasicRequestByAdminId` | BE bắt buộc adminId trùng với token auth — sai → 403 |
| `/api/web/admin/information/detail` | GET `?adminId=` | `getAdminInformationDetailRequestByAdminId` | tương tự |
| `/api/web/admin/2fa/enrollment` | POST `{periodSeconds}` | `postDataToStartEnrollmentRequest` | tạo khóa Base32 mới, lưu Redis 5 phút |
| `/api/web/admin/2fa/enrollment` | DELETE | `deleteDataToCancelEnrollmentRequest` | hủy khóa chờ |
| `/api/web/admin/2fa/enrollment/activate` | POST `{periodSeconds}` | `postDataToActivateEnrollmentRequest` | ghi MariaDB (chờ mobile xác nhận); 409 = đã bật; 410 = khóa chờ hết 5 phút |
| `/api/web/admin/2fa/status` | GET | `getDataTwoFaStatusRequest` | `{totpEnabled, pending, expiresInSeconds, periodSeconds}` |
| `/api/web/admin/2fa/enrollment/pending` | DELETE | `deleteDataToCancelPendingRequest` | hủy phần chờ xác nhận ở MariaDB |
| `/api/web/admin/2fa/events` | GET (SSE) | `readStatusStream` | text/event-stream; `2fa-status` event |

### 1.6 BE structure (rút gọn)

`controller/web` — `AdminControllerMgmtWeb`, `EnrollSessionControllerWeb`, `EnrollmentController`, `TwoFaEventControllerWeb`, `CourseController` (course/topic routes — hiện FE chưa hoàn toàn wire), `AdminControllerTest`, `EnrollSessionControllerWeb`. Bên BE hiện tại: RabbitMQ + chat + mobile controllers + Redis + JWT — **không đụng tới trong phạm vi FE này**.
---

## 2. Những thay đổi trên nhánh hiện tại (8611e8310 → a61453fe7)

### 2.1 Giữ NGUYÊN (không thay đổi dòng code logic auth)

| File | Trạng thái |
|---|---|
| `main.js` → `main.ts` | Giống nhau logic (TS port). Bootstrap/cross-tab/silent refresh/auto-login/guards/resolveTargetPath đều giữ nguyên. Additions: TS types (`RoutesEntry`, `matchRoute`) |
| `routers_before_login.js → .ts` | 4 route giống nhau: `/` (LoginPage allow:true), `/login` (allow:false), `/information-admin` (allow:false), `/404-not-found` (allow:true) |
| `routers_after_login.js → .ts` | 4 route cũ + 10 route mock mới (`/` → DashboardPage, `/dashboard`, `/bank`, `/courses`, `/courses/:index`, `/search`, `/review`, `/tasks`, `/moodle`, `/admin`, `/llm`, `/settings`) — tất cả `allow:true` + `meta:{shell:true}` |
| `services/NetworkConfiguration.js` | Không đổi |
| `services/spring/SpringConfigHeader.js` | Không đổi — giữ `getAuthorizedAPIClient` (auto-refresh + 401 retry) |
| `services/spring/AdminService.js` | Không đổi (single-flight refresh, guard không rethrow trên 5xx) |
| `services/spring/LoginService.js` | Không đổi |
| `services/spring/EnrollmentService.js` | Không đổi |
| `services/auth/SilentTokenRefresher.js` | Không đổi |
| `repository/AdminRepository.js` | Không đổi (giữ login mapping + logout + auto-login guards) |
| `assets/auth-sync.js` | Không đổi |
| `router/RouterMgmt.js`, `local_storage/AdminStorage.js`, `model/TokenLogin.js` | Không đổi |
| `pages/login-page/LoginPage.vue` | Không đổi (giữ nguyên toàn bộ logic: lock, rememberMe, focus, validation) |
| `pages/page-not-found/PageNotFound.vue` | Không đổi |
| `pages/information-page/card-*` | Chỉ CSS cosmetic (radius-zero, meta center, title 'Thông tin tài khoản · QBank Console') |

### 2.2 Thay đổi / thêm mới (chỉ UI/shell)

- `components/shell/AppShell.vue`, `TopBar.vue`, `SideNav.vue`, `PageHeader.vue`, `BulkBar.vue`, `Toast.vue`, `EmptyState.vue`, `TheIcon.vue` (mới) — shell cho các route có `meta.shell`.
- `assets/design/ql-shell.css` (~1.655 dòng) — design tokens + acctpg/card styles.
- `stores/appStore.ts` (mới) — Pinia store **mô phỏng toàn bộ dữ liệu** cho 10 mock pages. Ghi rõ ở đầu file: "TOÀN BỘ DỮ LIỆU NẰM TRONG TRÌNH DUYỆT... Không được thay đổi logic Spring Boot".
- 10 page mới (DashboardPage, BankPage, CoursesPage, CoursePage, SearchPage, ReviewPage, TasksPage, MoodlePage, AdminPage, LlmPage, SettingsPage) — rendering từ `appStore`, **không gọi Spring Boot**.
- `App.vue` — shell wrapper: `v-if="route.meta.shell"` → `AppShell`. Login + 404 giữ nguyên không shell.
- `InformationPage.vue` — **xoá `Header`/`Footer`** (shell thay thế), đổi `PAGE_TITLE` → `'Thông tin tài khoản · QBank Console'`, thêm class `acctpg`. Logic router/title/2FA giữ nguyên.

---

## 3. Kết luận logic chính — các phát hiện rà soát

### 3.1 🔴 Cấu trúc fidelity (phải chặn trước khi "chuyển giao diện mới hoàn toàn")

| # | Phát hiện | Bằng chứng | Khiến |
|---|---|---|---|
| F1 | **TopBar hardcode identity**: `initials='TN'`, `userName='Từ Quang Nhật'`, `userRole='Quản trị viên'` — không gọi Spring Boot, không đọc từ `AdminRepository`. | `components/shell/TopBar.vue:14-16` | Người dùng thấy tên bịa trong header & account menu → sai nghiệp vụ |
| F2 | **Menu `Cài đặt` trong topbar → `/settings`** là trang mock hoàn toàn (`SettingsPage.vue` + store) — không có "cài đặt" thật nào ở BE. | `TopBar.vue:24-27` | User hiểu menu đã wire → hiểu nhầm là tính năng thật |
| F3 | **10 mock pages ở sidebar** (Tổng quan, Tất cả câu hỏi, Môn học, Tìm kiếm, Xem xét AI, Tác vụ AI, Moodle XML, Quản trị hệ thống, Cài đặt LLM) render dữ liệu `appStore`, không BE. | `SideNav.vue` NAV + `appStore.ts:578` | Giao diện "giống thật" nhưng **không có server**; nguy cơ người dùng tin là đã nối production |
| F4 | **Login + 404 vẫn là UI cũ** (Header/Footer + scss cũ + không shell) trong khi account + stub pages đang dùng shell mới. | `LoginPage.vue`, `PageNotFound.vue` không có `meta.shell` | Hai hệ thị giác đè nhau trong một app |
| F5 | **BulkBar ẩn ở các trang không phải bank/course** (gating qua `store.page`) — OK — nhưng không có way to empty selection on stub pages. | `BulkBar.vue` visible computed | Không có vấn đề lắm nhưng edge case |
| F6 | **CardAccount có nút logout riêng** (trong account page) + **AppShell TopBar có nút logout riêng** (dropdown). Cả hai đều gọi `AdminRepository.getResponseFromLogoutRequest()` → cùng một đường logout. | `AppShell.vue:37`, `CardAccount.vue:161` | Duplication của 2 logout paths (functional OK, nhưng UX ngay dưới account page vẫn thấy 2 nút logout) |

### 3.2 🟡 Giữ nguyên / không đụng (chấp nhận được)

- **Login flow + lock + rememberMe**: ✅ Giữ nguyên 8611e8310. Không phải đã convert nhưng logic đúng.
- **Auto-login + silent refresh**: ✅ Giữ nguyên, single-flight + Web Locks + backoff + expire-only-on-real-401 — đúng nguyên tắc "đừng log out người dùng thật trên mạng blip".
- **2FA + Enrollment + SSE**: ✅ Giữ nguyên, không bị đụng khi convert UI. Đây là luồng phần lớn nhất bị "đập" nếu chuyển UI quá sớm.
- **Route guards**: ✅ Giống logic, guard bằng `matchRoute` thay vì `resolve()` của Vue Router — đủ cho các route hiện có. Lưu ý: `matchRoute` **không hỗ trợ wildcard** `/:path*` — nếu thêm 1 route sau này có wildcard, guard sai (xem F9).
- **Remember-me**: ✅ Giữ nguyên.
- **Cross-tab logout**: ✅ Giữ nguyên.
- **Title/sessionStorage restore**: ✅ Giữ nguyên.

### 3.3 🟢 Vấn đề phụ (cosmetic hoặc edge case)

| # | Phát hiện | Khuyến nghị |
|---|---|---|
| F7 | `main.ts` matchRoute không phân biệt path với query param (chỉ dùng path). | OK cho route hiện tại; nếu thêm `?flag=` route → phải kiểm tra lại. |
| F8 | `AppShell.vue` watch on route → store.setPage: cho bank/courses/course — chuyển page mock khi vào route thật. | OK nhưng `/course/:index` guard `idx !== undefined` sẽ lưu `store.ci`. Có thể hợp nhất với 1 computed helper. |
| F9 | `routers_before_login.ts` có `/information-admin` (allow:false) — tức là route tồn tại nhưng không cho vào. | Giữ nguyên behavior; không chuyển `/information-admin` thành 404. |
| F10 | `appStore.MY_ADMIN_ID = '2500021770'` — không khớp admin thật (BE dùng e.g. `2500021772`). | Nếu hiển thị ở mock page, sẽ trông sai. Chỉ dùng làm seed mock; không dùng để kiểm admin thật. |
| F11 | `LoginPage.vue` dùng `Header/Footer` old-style, không phải shell mới — đồng bộ 2 hệ thị giác trong file này khi "chuyển giao diện". | Cần convert 1 lần, không cần đi từng chunk. |

---

## 4. Những nguyên tắc KHÔNG được phá khi convert UI

1. **Không thay đổi hành vi auto-refresh**: `SilentTokenRefresher.start()` phải được bật ngay sau khi login thành công (hiện tại LoginPage gọi `silentRefresher.start()` sau navigate) — nếu convert, phải giữ việc này.
2. **2FA + Enrollment + SSE**: không được đổi endpoint, không reorder các bước.
3. **Logout phải chạy qua `AdminRepository.getResponseFromLogoutRequest()`** — chỉ 1 đường, không reimplement localStorage xoá.
4. **Remember-me** + **login lock** (persist sessionStorage + localStorage): giữ.
5. **Cross-tab broadcast logout**: giữ nguyên.
6. **Auto-login guard**: `resolveTargetPath` + beforeEach guard — giữ.
7. **BE endpoints** — chỉ gọi theo BE hiện file: không thêm endpoint mới.
8. **Không commit / không push** khi user chưa yêu cầu — đây là nguyên tắc của toàn bộ session.

---

## 5. Đề xuất bước convert (chờ user duyệt)

1. **Bước 0 — Fidelity / dashboards mock**: Đánh đấu tranh cấp độ mock:
   - a) Cập nhật `TopBar.vue`: đọc identity thật từ BE (qua `AdminRepository`) — hoặc tạm hiển thị identity có-thực-chức (`"Tài khoản"`) thay vì tên bịa.
   - b) Clicking menu `Cài đặt` → `/settings`: hiểu "Cài đặt" như stub/placeholder — hoặc gỡ menu hub `Cài đặt` khỏi TopBar nếu BE chưa wire.
   - c) Giữ nguyên account card thật (rename/share identity source với TopBar).
2. **Bước 1 — Convert login page** (giữ toàn bộ logic trong `LoginPage.vue`, chỉ đổi markup/scss): header/footer mới, không nhét shell vào (vd. login vẫn là full-page). **Không sửa `handleLoginEvent`, lock, rememberMe.**
3. **Bước 2 — Convert 404 page** (cũng giữ logic).
4. **Bước 3 — Convert stub pages** — giữ `appStore` mock, chỉ đổi scss/markup. **User có thể yêu cầu chặn trước khi "chuyển" full** vì chúng không có BE — cần user quyết định có giữ là mock hay đổi workflow.
5. **Bước 4 — Verification** — build, jsdom test, rồi hỏi user duyệt trước khi push.
6. **Bước 5 — Baseline reconciliation** — re-`build` + commit.

---

## 6. Câu hỏi cần trả lời trước khi chuyển giao diện

1. UI mock pages (Tổng quan/Tất cả câu hỏi/Môn học/Search/Review/Tasks/Moodle/Admin/Llm) có nên là "stub placeholder" chấp nhận được (như v9 đánh dấu mock) hay user yêu cầu chỉ giữ lại login/account/404 + shell?
2. Cần giữ `appStore` (Pinia mock) hay tạm dừng việc convert các mock pages?
3. Login page + 404: chuyển sang v9 hay giữ nguyên UI cũ?
4. TopBar identity: tạm mock hay wire BE?
5. Menu topbar "Cài đặt" → route `/settings` (mock): giữ hay gỡ?
6. Có cần thay đổi `routers_before_login.ts` (thêm stub pages với `allow:false`) không? — Hiện không có stub pages trong bộ route "trước đăng nhập" nên guard chặn — OK.
