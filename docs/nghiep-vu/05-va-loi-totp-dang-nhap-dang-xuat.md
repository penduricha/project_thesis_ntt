# Vá logic TOTP + đăng nhập / đăng xuất (Vue · Spring Boot · Flutter)

Tài liệu bàn giao cho đợt sửa lỗi trên nhánh `arena/3c7327e5-project-thesis-ntt` (gốc `2fe2bf49c`).
Mỗi mục dưới đây là **một lỗi cụ thể**, không phải đề xuất tính năng mới.

> **Mức độ kiểm chứng** (ghi rõ trong từng mục):
> - `[ĐỌC]` — đã đối chiếu mã nguồn trước/sau khi sửa, suy luận luồng chạy bằng tay.
> - `[PARSE]` — đã kiểm cú pháp tự động: Java bằng `tree-sitter-java` (80/80 tệp, 0 lỗi), Dart bằng
>   `tree-sitter-dart` (48/48 tệp, 0 lỗi), Vue/JS build thật bằng `vite build` (thành công).
> - `[CHƯA CHẠY]` — **không** compile/chạy được trong môi trường này: không có JDK, không có Flutter SDK,
>   không có MariaDB/Redis. Toàn bộ hành vi runtime (đăng nhập, 401, SSE, luồng TOTP) **chưa được chạy thử**.

---

## 0. Ba lỗi chí mạng (nếu chỉ đọc một mục thì đọc mục này)

| # | Lỗi | Hệ quả thực tế | Nơi sửa |
|---|-----|----------------|---------|
| 1 | `AdminAccessToken` có `@RedisHash(timeToLive = 10)` — **10 giây** | JWT sống 300 giây nhưng bản ghi Redis biến mất sau 10 giây ⇒ **mọi API cần đăng nhập trả 401 ngay sau khi đăng nhập**. Không phải lỗi "thỉnh thoảng", mà là lỗi 100% sau 10 giây. | `model/redis/AdminAccessToken.java`, `AdminRefreshToken.java` |
| 2 | `JwtAuthenticationFilter` là **stub rỗng** (chỉ cho OPTIONS đi qua, không đọc header) + `SecurityConfig` để `anyRequest().permitAll()` | **Toàn bộ API không cần đăng nhập**, kể cả `/web/admin/information/*`. Mọi controller phải tự kiểm token bằng tay, hoặc quên — như hai endpoint thông tin admin (IDOR). | `security/jwt/JwtAuthenticationFilter.java`, `security/SecurityConfig.java` |
| 3 | Khóa ký JWT **hard-code trong mã nguồn** | Ai đọc repo cũng tự ký được `token_type=ACCESS` cho **bất kỳ** `adminId` nào ⇒ bỏ qua cả mật khẩu lẫn TOTP, chiếm tài khoản quản trị. | `AdminJwtTokenProvider`, `application.yaml`, `security/JwtSecretGuard.java` |

---

## 1. Backend — Spring Boot (`be-web-admin`)

### 1.1 TTL token Redis lệch với hạn JWT `[ĐỌC][PARSE]`

- **Trước:** `@RedisHash(timeToLive = 10)` (access) và `2_592_000` (refresh) là hằng số, không liên quan tới `exp`
  của JWT. Tệp cũng import `jakarta.persistence.*`, `org.hibernate.annotations.*`, dùng `@Cacheable`/`@Cache`
  trên một entity Redis và `@Indexed` sai package (Spring Data Mongo thay vì Redis).
- **Sau:** `@TimeToLive(unit = SECONDS) private Long ttlSeconds;` — TTL tính bằng
  `AdminJwtTokenProvider.getRemainingSeconds(token)`, luôn khớp `exp` thật (mặc định 300 s / 30 ngày, đọc từ
  `app.jwt.access-token-ttl-seconds`, `app.jwt.refresh-token-ttl-seconds`).
  `@Indexed` chuyển sang `org.springframework.data.redis.core.index.Indexed` (nếu sai package thì
  `findAdminAccessTokenByJwtToken` ném lỗi lúc chạy chứ không trả `null`).

### 1.2 Token mồ côi khi đăng nhập lại `[ĐỌC]`

`AdminTokenServiceImpl.createTokenAdmin` sinh thêm cặp token mới nhưng **không xóa cặp cũ** ⇒ mỗi lần đăng nhập
lại có thêm một cặp còn hiệu lực. Nay: xóa toàn bộ access/refresh của admin trước khi cấp cặp mới (khớp chính
sách "1 tài khoản / 1 phiên mobile" đang có), và trả `expiresInSeconds` cho client hẹn giờ làm mới.
Thêm `removeAllTokensOfAdmin(adminId)` + `findAllByAdminId`/`deleteAllByAdminId` trong hai repository.

Trước đây khi lỗi, hàm trả `Map` rỗng ⇒ controller trả 401 "Can't create new tokens" mà không nói vì sao;
nay `AdminLoginServiceImpl.issueTokens` ném `IllegalStateException` có nội dung để log rõ nguyên nhân.

### 1.3 Không kiểm tra `account_status` `[ĐỌC]`

`enums/AccountStatus` (`PENDING/ACTIVE/LOCKED/DISABLED`) **không được đọc ở bất kỳ filter/service nào** ⇒ tài
khoản bị khóa/đã vô hiệu hóa vẫn đăng nhập được, và token cũ vẫn dùng được sau khi bị khóa.

Nay kiểm tra ở **hai chỗ**:
- `AdminLoginServiceImpl.rejectionFor()`: `ACTIVE`/`PENDING` cho vào; `LOCKED` → `ACCOUNT_LOCKED`;
  `DISABLED`/`null` → `ACCOUNT_INACTIVE`. Đặt **sau** bước kiểm mật khẩu để không ai dò được trạng thái tài khoản
  của người khác khi chỉ có `adminId`.
- `JwtAuthenticationFilter`: `LOCKED`/`DISABLED` bị chặn ngay ở mọi request, nên khóa tài khoản có hiệu lực tức
  thì với token đã cấp.

`DataInitializer` được bổ sung `setAccountStatus(ACTIVE)`: 10 tài khoản seed đang tạo ra ở trạng thái mặc định
`PENDING` ⇒ với bước kiểm tra mới, **cả 10 tài khoản mẫu sẽ không đăng nhập được**. `EnrollmentConfirmServiceImpl`
cũng chuyển `PENDING → ACTIVE` khi mobile xác nhận mã 6 số lần đầu (trước đây trạng thái này không bao giờ được
cập nhật).

> Quyết định thiết kế: `PENDING` **vẫn đăng nhập được** nhưng token chỉ có `ROLE_PENDING`, chỉ gọi được nhóm API
> thiết lập 2FA (`SecurityConfig.PENDING_ALLOWED_ENDPOINTS`). Nếu chặn hẳn thì admin mới không có đường nào bật
> 2FA (con gà – quả trứng).

### 1.4 Filter xác thực + SecurityConfig `[ĐỌC][PARSE]`

`JwtAuthenticationFilter` nay kiểm 4 lớp: chữ ký/hạn → đúng là **ACCESS** token → còn tồn tại trong Redis (chưa bị
thu hồi) → tài khoản còn dùng được; sau đó gắn `Authentication` (`ROLE_ADMIN`/`ROLE_PENDING`) và
`request.setAttribute("adminId", ...)`.

- **Lỗi cũ:** filter gọi `filterChain.doFilter()` **hai lần** cho request OPTIONS (return sau `doFilter` rồi lại
  `doFilter` ở cuối).
- **Lỗi cũ:** `SecurityConfig` khai báo **hai** `SecurityFilterChain`; bean đang bật là `permitAll`, bean "secured"
  bị comment. Nay chỉ còn một bean: `anyRequest().authenticated()`, `PUBLIC_ENDPOINTS` (login, logout, refresh,
  actuator) `permitAll`, không dùng `HttpSession` (`STATELESS`), 401/403 trả JSON thay vì redirect.
- CORS chuyển từ `localhost:*` hard-code sang `app.cors.allowed-origins` (biến `APP_CORS_ALLOWED_ORIGINS`).
- Bỏ `@CrossOrigin(origins="*")` trên 5 controller: `allowCredentials=true` cộng `origin="*"` sẽ **bị trình duyệt
  từ chối**, và khai báo CORS rải rác 5 nơi thì không thể siết ở một chỗ.

### 1.5 Refresh token dùng được như access token `[ĐỌC]`

`validateToken` chỉ kiểm chữ ký + hạn, nên **refresh token (30 ngày) gửi vào header `Authorization` vẫn qua được
mọi API**. Nay `isAccessToken()` / `isRefreshToken()` chặn ở filter, ở 4 endpoint auto-login và ở logout.

### 1.6 Đăng xuất web `[ĐỌC]`

`/web/admin/auth/logout` bản cũ: đọc thẳng hai chuỗi từ body (**không kiểm null**), `System.out.println`, không
biết token thuộc admin nào nên không dọn phiên mobile, và trả 401 khi lỗi (khiến Vue không dọn được phiên cục bộ).
Nay: kiểm đầu vào → suy `adminId` từ access/refresh token → `removeAllTokensOfAdmin` + `removeByAdminId` (phiên
mobile) + audit `LOGOUT` → **luôn 200** để client luôn dọn được state.

### 1.7 IDOR ở thông tin admin `[ĐỌC]`

`/web/admin/information/{basic,detail}` nhận `?adminId=` và tin tuyệt đối ⇒ đọc hồ sơ của bất kỳ admin nào (họ
tên, CCCD, địa chỉ, ngày sinh). Nay `adminId` lấy từ token (`@RequestAttribute`), tham số `adminId` chỉ được
chấp nhận **nếu trùng**, khác thì 403.

### 1.8 Audit `[ĐỌC]`

`AuthEvent` có `LOGIN_OK`, `LOGIN_FAIL_PASSWORD`, `LOGOUT`, `REFRESH_TOKEN`, `ACCOUNT_LOCKED` nhưng **không nơi
nào phát** (chỉ có sự kiện TOTP) ⇒ bảng `ntt_auth_audit_logs` không có dữ liệu đăng nhập. Nay ghi đủ ở cả web và
mobile.

### 1.9 Kiểm tra token bằng tay trong 4 controller `[ĐỌC]`

`EnrollmentController`, `EnrollSessionControllerWeb`, `EnrollmentConfirmControllerMobile`,
`TotpDisableControllerMobile` mỗi nơi có một bản `resolveAdminId(authHeader)` chép tay ⇒ sửa/kiểm tra sót một chỗ
là hở, và không kiểm `account_status`. Nay tất cả dùng `@RequestAttribute("adminId")` do filter gắn, xóa 4 hàm
trùng lặp và các field/import không còn dùng.

### 1.10 Đăng nhập mobile: đăng nhập lại cùng thiết bị `[ĐỌC]`

`AdminControllerMgmtMobile` trả 409 nếu đã có phiên mobile — **kể cả khi chính máy đó đăng nhập lại**. Người dùng
gỡ app / xóa dữ liệu app trong khi phiên Redis chưa hết hạn sẽ bị khóa vĩnh viễn ở màn "đã đăng nhập thiết bị
khác". Nay: cùng `deviceName` thì cho đăng nhập đè, khác thiết bị thì vẫn 409 (giữ chính sách 1 thiết bị).
Mobile logout cũng được thêm null-check (body rỗng trước đây gây 500) + thu hồi toàn bộ token + audit.

### 1.11 Logging / mã lỗi `[ĐỌC]`

`catch-all` trả "sai mật khẩu" cho cả tài khoản `LOCKED`/`DISABLED`; nay `LoginStatus` có `ACCOUNT_LOCKED` /
`ACCOUNT_INACTIVE` và cả web lẫn mobile trả `data.code` tương ứng. `CryptoService.CryptoTamperedException` khi
giải mã secret TOTP được ghi audit thay vì lộ stack trace ra ngoài.

---

## 2. Web quản trị — Vue (`fe_web_admin`)

| # | Lỗi | Sửa |
|---|-----|-----|
| 2.1 | `AdminRepository.getResponseFromLogoutRequest` chỉ xóa phiên cục bộ khi backend trả 200 ⇒ **bấm Đăng xuất, gặp lỗi mạng là phiên vẫn còn**, F5 vào lại được | Dọn phiên trong `finally`, **luôn** dọn; 401 coi như đã đăng xuất `[ĐỌC]` |
| 2.2 | `CardAccount.beforeRouteLeave` gọi `next(false)` khi logout lỗi ⇒ **người dùng bị kẹt ở trang thông tin** | Cho rời trang nếu không còn token nào sót lại `[ĐỌC]` |
| 2.3 | `handleLogout` chỉ điều hướng khi `status === "OK"` ⇒ lỗi mạng thì nút quay mãi, vẫn ở trang đã đăng nhập | Điều hướng trong mọi trường hợp `[ĐỌC]` |
| 2.4 | `routers_after_login.js` vẫn có `/login` (`allow: true`) ⇒ sau đăng xuất, router coi `/login` là route hợp lệ của app đã đăng nhập, guard đăng xuất bị bỏ qua | Bỏ `/login` khỏi bộ route "sau đăng nhập"; điều hướng về login bằng `window.location.replace` để `main.js` nạp lại đúng bộ route `[ĐỌC][PARSE]` |
| 2.5 | `main.js#clearSessionAndGoToLogin` (đường "phiên hết hạn") **không xóa token**, chỉ xóa path ⇒ F5 tiếp theo auto-login lại thành công, người dùng bị đá ngược vào trang trong | Xóa token + adminId trước khi rời trang `[ĐỌC]` |
| 2.6 | Thông tin admin gọi `getAPIClientNoHeaders()` + `?adminId=` (IDOR cả phía client) | Dùng `getAuthorizedAPIClient()` (tự gắn Bearer + tự refresh khi 401), bỏ query `adminId` `[ĐỌC][PARSE]` |
| 2.7 | `AdminService.#clearSession` khi refresh token bị 401 chỉ xóa token/adminId, **không xóa path, không dừng timer** ⇒ lần sau vào thẳng trang trong, timer cũ bắn 401 liên tục | Dùng chung một đường dọn phiên với repository `[ĐỌC]` |
| 2.8 | Mã lỗi mới `ACCOUNT_LOCKED` / `ACCOUNT_INACTIVE` rơi vào nhánh mặc định ⇒ hiện "sai mật khẩu", người dùng thử lại vô ích | Thêm nhánh + câu thông báo riêng trong `LoginPage.vue` `[ĐỌC][PARSE]` |
| 2.9 | `TokenLogin` lưu token raw trong `localStorage` | **Chưa sửa** (xem mục 4) |

---

## 3. App mobile — Flutter (`app_mobile`)

| # | Lỗi | Sửa |
|---|-----|-----|
| 3.1 | `LoginService.login` chỉ gửi `{adminId, password}` cho **controller gọi**, nên tài khoản đã bật 2FA **không thể đăng nhập trên mobile** (server trả `TOTP_REQUIRED` mà app không có đường gửi mã) | `LoginController` gửi `totpCode` sinh từ khóa trên máy ngay lần đầu (backend coi là tùy chọn) `[ĐỌC][PARSE]` |
| 3.2 | `_currentTotpCode` lấy `accounts.first` mà **không kiểm chủ sở hữu** ⇒ máy còn khóa của tài khoản trước thì gửi mã sai, và vì mỗi lần sai đều bị đếm, tài khoản đúng bị khóa tạm | Chỉ dùng khóa có `account == adminId`, không khớp thì coi như chưa có khóa `[ĐỌC][PARSE]` |
| 3.3 | `LoginRejected` chỉ có một câu "Login failed", mất `data.code` ⇒ không phân biệt được "máy chưa có khóa" (`TOTP_REQUIRED`) với "mã bị từ chối" (`TOTP_INVALID`) và "bị khóa tạm" (`LOCKED`) | Thêm `code`/`retryAfterSeconds`, giữ nguyên qua `LoginService`, màn đăng nhập hiện tiêu đề + icon theo từng mã `[ĐỌC][PARSE]` |
| 3.4 | Mã vừa dùng / chu kỳ vừa đổi ⇒ `TOTP_INVALID`, người dùng phải tự bấm Đăng nhập lần hai | Thử lại **một** lần với mã mới nếu mã khác `[ĐỌC]` |
| 3.5 | `LogoutController` trả `LogoutFailure` và **không xóa phiên** khi server lỗi ⇒ bấm Đăng xuất, mở lại app vẫn vào trong nhà; phiên mobile trên server vẫn bị chiếm nên máy khác không đăng nhập được | Dọn phiên cục bộ **trước**, gọi server sau (best-effort); lỗi mạng vẫn là `LogoutSuccess` `[ĐỌC][PARSE]` |
| 3.6 | `LogoutConfirmDialog` giữ dialog mở + báo lỗi khi thất bại ⇒ người dùng kẹt ở popup dù phiên đã bị xóa | Luôn đóng dialog và về màn đăng nhập `[ĐỌC][PARSE]` |
| 3.7 | `LogoutService` coi 401 là lỗi; `LogoutController` giữ spinner vô hạn khi thành công (component bị replace) | 401 = đã đăng xuất; controller + dialog phối hợp đóng dialog `[ĐỌC]` |
| 3.8 | `TokenStorage` và `AuthStorage` **cùng** ghi 2 khóa `access_token`/`refresh_token` ⇒ hai nguồn sự thật, xóa một nửa (mất token còn adminId) | `TokenStorage` thành adapter mỏng trên `AuthStorage`; `clearSession()` là điểm xóa duy nhất `[ĐỌC][PARSE]` |

---

## 4. Điều kiện chạy & việc còn lại

### Phải làm trước khi chạy
1. Tạo `be-web-admin/.env` từ `.env.example`; **bắt buộc** đặt `JWT_SECRET` (≥ 32 ký tự) và
   `TOTP_MASTER_KEY_V2`. Chạy với profile không phải `dev/test/local` mà vẫn dùng khóa mặc định thì ứng dụng
   **không khởi động** (`JwtSecretGuard`).
2. Dữ liệu cũ: tài khoản nào đang ở `account_status = PENDING` mà thực tế đã dùng được thì cần cập nhật `ACTIVE`
   trước khi bật `app.login.require-active-account` (mặc định đã bật).
3. `spring.jpa.hibernate.ddl-auto` đang là `update`: các cột mới của tài liệu SQL 01–04 phải được áp bằng script,
   không dựa vào `update` (nó không tạo index/khóa ngoại như thiết kế).

### Chưa sửa (ngoài phạm vi đợt vá này)
- **Token raw trong `localStorage`** (Vue) và **mật khẩu "ghi nhớ" lưu chữ thuần** (`AdminStorage`): XSS đọc được.
  Hướng xử lý đúng là refresh token vào cookie `HttpOnly; SameSite=Strict` — cần đổi cả backend (đọc cookie thay
  body), nên tách thành việc riêng.
- `BlackListedToken` vẫn không dùng: cơ chế thu hồi hiện tại đã dựa trên "token còn trong Redis hay không", đủ cho
  yêu cầu. Giữ nguyên để tránh hai cơ chế thu hồi song song.
- `fe_web_admin/.env` vẫn có `VITE_AUTH_PASSWORD` được **track trong Git**; nên xoay giá trị và thêm `.env` vào
  `.gitignore`.
- Chưa có test tự động nào cho các luồng trên (không có JDK/Flutter SDK trong môi trường này). Đề nghị tối thiểu
  4 kịch bản: (a) đăng nhập rồi gọi API sau 11 giây, (b) refresh token dùng như access token phải 401,
  (c) tài khoản `LOCKED` bị chặn cả khi đang có token, (d) đổi `?adminId=` phải 403.
