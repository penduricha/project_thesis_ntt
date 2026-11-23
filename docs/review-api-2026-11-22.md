# Rà soát tầng API — commit mới nhất `88e40f4`

> **Bản dành cho nền `8bc435e`** (nhánh `feature/apply-ui-project-thesis` hoặc commit UI_v10 của bạn):
> phần P1-4 (`System.out` → `log`) **không áp dụng** vì bản này đã có `@Slf4j` + `log.error`; patch chỉ thêm
> bộ xử lý lỗi, `permitAll` cho ERROR dispatch, ràng buộc dữ liệu cho `AdminLoginDTO` và `@Valid`.

- **Ngày**: 2026-11-22 · **Phạm vi**: toàn bộ API HTTP của `be-web-admin` (Server 1) tại commit mới nhất của repo: **`88e40f4`** (`feature/thesis-qbank-v9`, 2026-11-22 15:56 UTC). Đây vẫn **chưa phải** commit UI_v10 + web Vue bạn nhắc ở lượt trước — commit đó chưa có trên GitHub (đã kiểm 5 nhánh + cây file).
- **Cách rà**: đọc mã tĩnh (sandbox không có JDK/Gradle/MariaDB/Redis nên **không chạy build**), đối chiếu mapping với `SecurityConfig`, `application.yaml` và cách frontend `fe_web_admin` đang gọi.
- **Kết quả**: 34 mapping API, **4 lỗi P1 đã vá trong patch kèm theo**, 6 điểm P2 và 5 điểm P3 cần bạn quyết định.

---

## 1. Bản đồ API hiện có

| Nhóm | Method | Đường dẫn (sau `/api`) | Xác thực |
|---|---|---|---|
| web | `POST` | `/web/admin/auth/login`, `/web/admin/auth/login/totp` | công khai |
| web | `POST` | `/web/admin/auth/logout` | công khai (token trong body) |
| web | `GET` | `/web/admin/auth/login/auto/access-token` | công khai (token trong header) |
| web | `GET` | `/web/admin/auth/login/auto/refresh-token` | công khai (token trong header) |
| web | `GET` | `/web/admin/information/basic` | đã đăng nhập, chỉ dữ liệu của chính mình |
| web | `GET` | `/web/admin/information/detail` | đã đăng nhập, chỉ dữ liệu của chính mình |
| web | `POST` | `/web/admin/2fa/enrollment` | đã đăng nhập |
| web | `DELETE` | `/web/admin/2fa/enrollment` | đã đăng nhập |
| web | `POST` | `/web/admin/2fa/enrollment/activate` | đã đăng nhập |
| web | `DELETE` | `/web/admin/2fa/enrollment/pending` | đã đăng nhập |
| web | `GET` | `/web/admin/2fa/status` | đã đăng nhập |
| web | `GET` | `/web/admin/2fa/events` (SSE, dùng `fetch`, không dùng `EventSource`) | đã đăng nhập |
| web (dev) | `POST` | `/web/admin` | `hasRole('ADMIN')` + profile `dev/local/test` |
| web (dev) | `GET` | `/web/admin/all` | `hasRole('ADMIN')` + profile `dev/local/test` |
| mobile | `POST` | `/mobile/admin/auth/login` | công khai |
| mobile | `POST` | `/mobile/admin/auth/logout` | công khai (token trong body) |
| mobile | `GET` | `/mobile/admin/auth/login/auto/access-token` | công khai (token trong header) |
| mobile | `GET` | `/mobile/admin/auth/login/auto/refresh-token` | công khai (token trong header) |
| mobile | `GET` | `/mobile/admin/information/admin-name` | đã đăng nhập |
| mobile | `GET` | `/mobile/admin/2fa/enrollment/pending` | đã đăng nhập |
| mobile | `POST` | `/mobile/admin/2fa/enrollment/confirm` | đã đăng nhập |
| mobile | `POST` | `/mobile/admin/2fa/disable` | đã đăng nhập |
| chat | `GET` | `/mobile/chat/admins` | đã đăng nhập |
| chat | `GET/POST/DELETE` | `/mobile/chat/conversations…` (9 API) | đã đăng nhập |
| chat | `POST` | `/mobile/chat/ws-ticket` | đã đăng nhập |
| chat | — | `/mobile/chat/ws` (WebSocket, vé dùng một lần) | vé bắt tay |

**Đánh giá tổng quát**: khung bảo mật đã tốt sau v9 — JWT có vai (`RoleAuthorities` fail-closed về LECTURER), kiểm phiên trên Redis, khóa đoán mật khẩu theo (IP, adminId), 2FA mã hoá secret, `password` là `WRITE_ONLY`, `course` `@JsonIgnore`, `open-in-view: false`, dev API có `@Profile`. Các lỗi còn lại nằm ở **tầng xử lý lỗi và hợp đồng dữ liệu đầu vào**, không phải ở xác thực.

---

## 2. P1 — Đã vá trong patch `project-thesis-ntt-api-hardening-2026-11-22.patch`

### P1-1. Lỗi thật bị che thành `401 Unauthorized`
`SecurityConfig` chỉ `permitAll` cho `DispatcherType.ASYNC`. Khi controller ném lỗi mà không có `@ExceptionHandler`, Spring chuyển tiếp nội bộ sang `/error` với `DispatcherType.ERROR`; bước này **không** khớp `anyRequest().authenticated()` với người chưa đăng nhập, nên:

- `POST /api/web/admin/auth/login` với body `{}` hoặc JSON hỏng → **401** thay vì 400;
- gọi sai method/đường dẫn khi chưa có token → **401** thay vì 405/404.

Client không phân biệt được "sai dữ liệu" với "hết phiên" nên FE cũ dễ kết luận sai là token hết hạn rồi làm mới vô ích.
**Vá**: thêm `.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()`.

### P1-2. Web/mobile không có bộ xử lý lỗi dùng chung
Chỉ `chat` có `ChatExceptionHandler` (đã giới hạn đúng `basePackageClasses`). Với web/mobile: `MethodArgumentNotValidException`, `HttpMessageNotReadableException`, `MissingServletRequestParameterException`, `HttpRequestMethodNotSupportedException`, `NoResourceFoundException` đều rơi về `/error` với thân `{timestamp,status,error,path}` — khác hoàn toàn `{status,message,data}` mà FE đang đọc.
**Vá**: thêm `config/ApiExceptionHandler` (`@Order(LOWEST_PRECEDENCE)`, không tranh chỗ với chat/IOException), trả thống nhất `{success:false, code, message, at}`:

| Tình huống | Trước | Sau |
|---|---|---|
| Body thiếu/sai trường (`@Valid`) | 401 (bị che) | `400` `VALIDATION_FAILED` + tên trường |
| JSON hỏng / sai kiểu | 401 (bị che) | `400` `MALFORMED_BODY` |
| Thiếu/sai tham số query | 401 (bị che) | `400` `INVALID_PARAMETER` |
| Sai method | 401 (bị che) | `405` `METHOD_NOT_ALLOWED` |
| Sai `Content-Type` | 415 mặc định | `415` `UNSUPPORTED_MEDIA_TYPE` |
| Không có API | 404 mặc định | `404` `ENDPOINT_NOT_FOUND` |
| Vi phạm ràng buộc DB | `500` | `409` `DB_CONSTRAINT` (không lộ tên bảng/cột) |

### P1-3. Đăng nhập không có hợp đồng dữ liệu đầu vào
`AdminLoginDTO` không có ràng buộc nào và controller không dùng `@Valid`, nên: `adminId` thiếu → lặng lẽ trả "sai mật khẩu"; mật khẩu dài bất kỳ vẫn được băm (BCrypt chỉ dùng 72 byte đầu → phần dư bị bỏ âm thầm); `totpCode` không kiểm định dạng.
**Vá**: `@NotNull adminId`, `@NotNull @Size(max=100) password`, `@Pattern("^$|^\\d{6}$") totpCode` + `@Valid` ở `/auth/login` (và alias `/auth/login/totp`). Chuỗi rỗng vẫn hợp lệ để không phá client cũ đang gửi `totpCode: ""`.

### P1-4. Ghi log bằng `System.out` trong luồng đăng xuất
`AdminControllerMgmtWeb.logoutAdmin` dùng `System.out.println`, không vào file log, không có mức log, không có ngữ cảnh.
**Vá**: `@Slf4j` + `log.warn(...)`.

---

## 3. P2 — Nên vá, nhưng cần sửa frontend cùng lúc (chưa làm)

1. **Đăng nhập luôn trả `200`** kể cả sai mật khẩu, bị khóa, bị lock. Đây là quyết định tương thích: `fe_web_admin` đọc `data.code` (`TOTP_REQUIRED`, `LOCKED`, …). Đổi sang 401/423 sẽ đúng chuẩn nhưng **phải sửa `AdminRepository.js` cùng lúc**. Nếu giữ, nên thêm header `X-Auth-Result` để gateway/metrics đếm được.
2. **`/information/basic` và `/information/detail` trả `200` + `data` rỗng** khi không tìm thấy admin, thay vì `404`. FE hiện coi `data` rỗng là "không có". Nếu đổi phải sửa `InformationPage`.
3. **`GET /web/admin/all` không phân trang** và trả entity `Admin` (password đã `WRITE_ONLY` nên không lộ hash). Chỉ tồn tại ở profile dev/local/test, nhưng nếu chuyển lên prod phải phân trang + DTO riêng.
4. **Trùng chức năng huỷ đăng ký 2FA**: `DELETE /web/admin/2fa/enrollment` (EnrollSessionControllerWeb) và `DELETE /web/admin/2fa/enrollment/pending` (EnrollmentController); mobile có `GET /mobile/admin/2fa/enrollment/pending`. Nên chọn một đường dẫn chuẩn và giữ bản còn lại làm alias có `@Deprecated`.
5. **Mobile dùng `Map<String,Object>` cho login/logout**: không có schema, sai kiểu dữ liệu (ví dụ `adminId: "abc"`) chỉ được phát hiện bằng `toStringOrNull` thủ công. Nên tạo `AdminLoginMobileDTO` + `@Valid` như web.
6. **Khóa giới hạn đăng nhập dùng `getRemoteAddr()`**: đã đặt `server.forward-headers-strategy: native`, nhưng chỉ đúng khi reverse proxy (nginx/Cloudflare) truyền `X-Forwarded-For` tin cậy. Khi deploy thật phải kiểm: nếu `getRemoteAddr()` trả IP của proxy thì mọi người dùng **dùng chung một bộ đếm** → khoá oan.

---

## 4. P3 — Ghi nhận, không cần sửa ngay

1. Ba định dạng lỗi song song: `{status,message,data}` (web/mobile cũ), `{success,message,data}` (login/info), `{code,message}` (chat). Đề xuất chuyển dần sang `{success,code,message}` khi có dịp sửa từng module.
2. Toàn bộ controller khai báo `ResponseEntity<?>` → không có hợp đồng kiểu, không sinh được tài liệu API tự động. Nếu làm OpenAPI, đây là việc lớn nhất.
3. Chỉ `AdminControllerTest` có `@PreAuthorize`; các API còn lại "ai đăng nhập cũng gọi được" — đúng với phạm vi hiện tại (thông tin cá nhân, 2FA của chính mình), nhưng khi thêm module quản trị phải đặt `@PreAuthorize("hasRole('ADMIN')")` ngay từ đầu.
4. Các controller 2FA tự đọc header `Authorization` và tự giải mã adminId thay vì dùng `Authentication`/principal đã có trong `SecurityContext` → lặp logic ở 4 file, dễ lệch khi đổi định dạng token.
5. `chat.searchAdmins` trả danh sách admin cho mọi tài khoản đã đăng nhập — chấp nhận được với tính năng chat nội bộ, nhưng nên giới hạn theo từ khoá tối thiểu (đã có `q`) và số kết quả.

---

## 5. Cách áp patch và kiểm thử lại

```bash
git fetch origin feature/thesis-qbank-v9 && git switch feature/thesis-qbank-v9
git log --oneline -1          # phải là 88e40f4
git apply --check project-thesis-ntt-api-hardening-2026-11-22.patch
git apply project-thesis-ntt-api-hardening-2026-11-22.patch
cd be-web-admin && ./gradlew test    # patch không đổi API công khai, chỉ thêm ràng buộc
```

Kiểm thử tay sau khi chạy server (đường dẫn có tiền tố `/api`):

| # | Lệnh | Kỳ vọng |
|---|---|---|
| 1 | `curl -i -X POST localhost:8080/api/web/admin/auth/login -H 'Content-Type: application/json' -d '{}'` | `400` `VALIDATION_FAILED` (trước: 401) |
| 2 | `... -d '{"adminId":'` | `400` `MALFORMED_BODY` |
| 3 | `curl -i localhost:8080/api/web/admin/khong-ton-tai` | `401` (chưa có token) |
| 4 | như #3 nhưng kèm `Authorization: Bearer <token>` | `404` `ENDPOINT_NOT_FOUND` |
| 5 | `curl -i -X PUT localhost:8080/api/web/admin/auth/login` | `405` `METHOD_NOT_ALLOWED` |
| 6 | login đúng `{"adminId":..., "password":"..."}` | `200` `success:true` như cũ |
| 7 | login kèm `"totpCode":"12345"` | `400` `VALIDATION_FAILED` |
| 8 | login kèm `"totpCode":""` | chạy như cũ (không bị chặn) |
| 9 | (profile dev) tạo admin trùng `idCard` | `409` `DB_CONSTRAINT` (trước: 500) |

---

## 6. Giới hạn của lần rà này

- **Không chạy được build/test**: sandbox không có JDK, Gradle, MariaDB, Redis. Patch mới chỉ được kiểm bằng đọc mã + kiểm cú pháp tĩnh (cân bằng ngoặc, import giải được). Bắt buộc `./gradlew test` và bấm thử 9 ca ở §5 trước khi merge.
- **Không rà** `app_mobile` (Flutter) và `server-ai-worker` (Python) ở lượt này — chỉ rà API của Server 1 như bạn yêu cầu.
- **Chưa thấy commit UI_v10 + web Vue**: khi bạn push, tôi sẽ rà tiếp phần API mà màn hình mới cần (chủ yếu là 19 endpoint ngân hàng câu hỏi trong patch ORM/Redis) và đối chiếu với hợp đồng dữ liệu của `fe_web_admin`.
