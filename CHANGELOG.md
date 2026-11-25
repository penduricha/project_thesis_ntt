# Changelog

Mọi thay đổi đáng chú ý của đồ án được ghi ở đây.

**Phạm vi tài liệu này:** 3 nhánh chính của hệ thống — `qbank-console-vue3` (Vue 3),
`be-web-admin` (Spring Boot) và `fe_web_admin` (bản tham chiếu logic).
`php_moodle_503/` được ghi nhận riêng ở mục "Ngoài phạm vi" khi có thay đổi chạm vào nó.

**Quy ước ghi:** một mục cho mỗi thay đổi **có ý nghĩa với người đọc lại** (reviewer, hội đồng,
thành viên mới). Không liệt kê commit sửa typo. Mỗi mục ghi rõ *vấn đề* và *cách xử lý*, không
chỉ ghi "đã sửa X".

---

## [Chưa phát hành] — 2026-11-23

### QBank Console (Vue 3): nút xoá đỏ, `===` toàn bộ, trang 404

#### Sửa lỗi

- **Nút "Xóa tài khoản" giờ nền đỏ, chữ trắng.** Nút trong hộp thoại Chi tiết tài khoản trước chỉ
  có `btn directory-danger` — mà `.directory-danger` chỉ đổi **màu chữ** sang đỏ, không đổi nền.
  Thêm class `dan` để dùng biến thể có sẵn `.btn.dan` (`background: var(--danger-strong)`,
  `color: #fff`, hover `--danger-hover`). Không viết CSS mới: bảng màu đã có `--danger-strong`
  `#a51818` / `--danger-hover` `#8a1414` nên chỉ cần dùng đúng class.

- **Thanh chọn câu hỏi bị mất toàn bộ style** (hồi quy từ commit `de04940a0` "Formated code").
  Commit đó đổi `id="selbar"` → `id="sideBar"` và biến CSS `--selbar-h` → `--sideBar-h` trong
  `App.vue`, nhưng `main.css` vẫn viết `#selbar` / `var(--selbar-h)` ở 22 chỗ. Hệ quả: thanh chọn
  thành `div` trần nằm cuối trang, và `.page` không còn chừa chỗ nên thanh chọn **che hàng câu hỏi
  cuối**. Đã đưa 22 chỗ trong CSS về đúng tên JS đang dùng.

- **Máy quét `==` → `===` suýt gây lỗi bộ lọc.** Đổi 92 chỗ `==` → `===` và 23 chỗ `!=` → `!==`
  trong 19 tệp. Nhưng **không đổi máy móc**: `q.c`/`q.tp`/`q.l` là **SỐ** (do `mappers.js` sinh),
  còn bộ lọc `S.f.c`/`S.f.tp`/`S.f.l` là **CHUỖI** (`blankF()` trả `''`, chip gán `String(idx)`).
  Nếu để `q.tp === f.tp` thì luôn `false` vì khác kiểu ⇒ **lọc theo môn/chương/mức độ ra rỗng**.
  Đã ép kiểu `+f.c`, `+f.tp`, `+f.l` để so SỐ với SỐ. Kiểm chứng bằng script mô phỏng 6 trường hợp:
  bản `===` trần sai **4/6**, bản có `+f.*` đúng cả 6.

#### Thêm mới

- **Trang 404** (`src/views/NotFoundView.vue`) + route `/:pathMatch(.*)*` đặt **cuối** mảng route.
  Trước đó router chỉ có 13 route cụ thể, không có catch-all ⇒ gõ sai đường dẫn (ví dụ
  `/fwiehgoihergplij`) thì `RouterView` rỗng, **màn hình trắng không lời giải thích**.

  Ba nút của trình duyệt được xử lý riêng vì đây là chỗ dễ hỏng nhất:

  | Nút | Cách xử lý |
  |---|---|
  | **←** | `history.state.back` có giá trị ⇒ `router.back()`. Không có (người dùng **dán thẳng link lỗi**) ⇒ về trang chủ, vì `router.back()` sẽ đẩy họ **ra khỏi website** |
  | **→** | Không cần can thiệp — mọi chuyển trang đi qua router nên entry nào cũng có state |
  | **↻** | `requestedPath` đọc từ `route.path` (không chốt lúc `created()`) nên sau reload vẫn hiện đúng đường dẫn sai |

  Nút "Về trang chủ" dùng `router.replace` chứ không `push`: nếu `push` thì bấm ← sẽ quay lại đúng
  trang 404 vừa rời, thành vòng lặp.

  Guard cho 404 đi qua **trước** nhánh chặn đăng nhập — người gõ sai link mà bị đá về trang đăng
  nhập thì không hiểu mình sai ở đâu. Khi chưa đăng nhập, trang 404 được vẽ **"trần"** (không
  topbar/sidebar) để không lộ menu.

  Kiểm chứng bằng vue-router thật + jsdom, 6 kịch bản:

  ```
  1. gõ /fwiehgoihergplij                    → not-found   /fwiehgoihergplij
  2. gõ /a/b/c/d/e                           → not-found   /a/b/c/d/e
  3. đang ở 404, bấm ← (có trang trước)       → bank        /bank
  4. dán link trực tiếp rồi bấm ←             → dash        /dash      ← không thoát web
  5. CHƯA đăng nhập gõ bậy                    → not-found   /sai-be-bet
  6. ↻ reload                                 → not-found   /fwiehgoihergplij
  ```

- **Favicon riêng** `src/assets/images/favicon-ntt.png`. Bản bạn thêm trỏ thẳng
  `logo-NTT.png` (1200×1170, **176 KB**) — trình duyệt tải lại ảnh này mỗi lần mở tab, và ở
  16–32px thì dòng chữ "NGUYEN TAT THANH UNIVERSITY" không đọc được. Đã cắt sát huy hiệu NTT
  (phần "NTT" vẫn nhận ra ở 64px) và thu về **6,6 KB** — giảm 96%. Thêm `apple-touch-icon` 180×180
  (nền trắng, vì iOS không xử lý alpha tốt) và `meta theme-color #004b87`.

### Flutter `app_mobile`: chặn `periodSeconds` không hợp lệ gây crash tab Xác thực

#### Sửa lỗi

- **`periodSeconds` từ server được dùng trực tiếp, không kiểm.** Chuỗi:
  `server → _fetchPending() → AuthAccount.periodSeconds → _elapsedFraction()` với
  `ms % (period * 1000)`. Nếu server trả `0` (hoặc dữ liệu cũ đã lưu có `0`), phép `%` chia cho 0
  ⇒ **crash cả tab Xác thực**, không chỉ một tài khoản.

  `_clockFor()` cũng tạo `AnimationController(duration: Duration(seconds: 0))` — animation hoàn tất
  tức thì nên `_onClockCompleted()` được gọi lại liên tục: **vòng lặp vô hạn**.

  Các lớp bảo vệ sẵn có **không cứu được**: `assert(period > 0)` trong `TotpGenerator.generate()`
  bị **bỏ ở bản release**; `_generateCodes()` chỉ bắt `FormatException` (secret sai Base32).

  Vá 2 lớp: (1) `_fetchPending()` — biên giới tin cậy, kiểm `periodSeconds > 0` và `digits` trong
  6..9, ném `EnrollmentException` có câu báo tiếng Việt; (2) `_elapsedFraction()`/`_clockFor()` —
  chặn dưới về `TotpGenerator.defaultPeriod`, để cứu các tài khoản **đã lưu từ bản cũ** (không đi
  qua bước kiểm 1 nữa).

- **Kiểm chứng TOTP đúng chuẩn** (không phải sửa, ghi lại để hội đồng biết): đối chiếu 12/12 vector
  RFC 6238 (6 vector SHA-1 + 6 vector SHA-256) — khớp hết. Đáng chú ý: dùng `setUint32` ×2 thay vì
  `setUint64` là **có chủ ý**, vì `setUint64` không chạy trên Flutter Web.

### Giao diện Moodle: tắt hết `:active` và vòng focus do chuột (bản sửa)

#### Sửa lỗi

- **Bấm chuột vẫn làm nút/menu/tab/liên kết "nháy" — bản tắt `:active` trước đó không có tác dụng.**
  Đo bằng Chromium (rê chuột rồi nhấn giữ, so màu nền/viền/chữ/bóng/gạch chân): **49/68** phần tử mẫu
  đổi giao diện khi nhấn ở chế độ sáng (42/68 ở chế độ tối).
  *Nguyên nhân (4 chỗ):*
  1. Bootstrap vẽ `:active` bằng `var(--bs-btn-active-bg)`, không đọc `--bs-btn-bg`. Quy tắc cũ gán
     `--bs-btn-bg` khi `:active` nên vô tác dụng; kèm `$btn-active-bg-shade-amount: 0%` còn làm nút nháy
     từ màu hover **ngược về màu gốc** lúc nhấn.
  2. `.dropdown-item:active` bị đặt nền xanh đặc (coi là "mục đang chọn") nên bấm vào mục menu là nháy
     xanh rồi mới chuyển trang. Đã bỏ `:active` khỏi nhóm này.
  3. Các thành phần khác tô riêng khi nhấn: `.list-group-item-action`, ô chọn/radio (`brightness(90%)`),
     núm thanh trượt, ô chọn kiểu cũ (`.custom-control`), nút Atto, `.mds-*` (nút, phân trang, favourite,
     choicebox).
  4. Không phải `:active` nhưng cùng cảm giác: Moodle vẽ vòng 4px và nền sáng dưới liên kết trên
     `:focus` (không phải `:focus-visible`) nên **bấm chuột** cũng hiện, và mất khi bấm chỗ khác.
  *Xử lý:* `scss/ntt/_no-active.scss` mới (import cuối `post.scss`) + sửa `_buttons.scss`. Nút: gán
  `--bs-btn-active-*` = `--bs-btn-hover-*`. Còn lại: `:active` = giá trị hover; token `--mds-*-active`
  trỏ về token hover; vòng focus chỉ chặn khi `:focus:not(:focus-visible)` nên **bàn phím giữ nguyên vòng
  focus**. Quy tắc `:focus` do chính theme viết (thanh trên, drawer, `.btn-link`, `.btn-icon`, chỉ mục khoá
  học, nút trợ giúp) đổi sang `:focus-visible` để nền hover không "dính" sau khi bấm.
  *Kết quả đo lại:* **0/68** phần tử đổi khi nhấn (sáng và tối); trạng thái nghỉ, hover và focus bàn phím
  của cả 68 phần tử giữ nguyên.
  Thay thế mục "Trạng thái `:active` gây khó chịu" ở bản ghi bên dưới, kể cả đoạn "giữ `:active` ở
  `.dropdown-item`" (đã bỏ).
- **Liên kết lồng trong mục menu bị chữ trắng trên nền hover xanh nhạt (không đọc được).** Boost ép chữ
  trắng khi hover/focus vì giả định nền hover đậm; nền hover của NTT là xanh nhạt. Đã đặt màu chữ theo
  token. Mục đang chọn (nền xanh đặc) vẫn chữ trắng.

#### Chưa xử lý

- Quy tắc `.dropdown-item:hover.text-*` của Boost dùng `!important` ép chữ trắng khi hover (chỉ xảy ra khi
  mục menu mang class `text-*`). Chưa thấy chỗ nào trong dự án dùng, nên để nguyên.
- Nút "bật/tắt" Bootstrap dạng `.btn-check:checked + .btn` khi đang nhấn sẽ hiện màu hover thay vì màu
  "đang bật" (chưa thấy chỗ nào trong dự án dùng).

### Giao diện Moodle: style trang quiz + tắt trạng thái :active

#### Thêm mới

- **`theme/ntt/scss/ntt/_quiz.scss` (209 dòng)** — style trang **làm bài quiz** và **xem lại bài**.
  Đồ án cho sinh viên làm bài thật trên Moodle nên đây là màn hình phải chăm nhất.
  Phủ: thanh điều hướng câu hỏi 5 trạng thái, đồng hồ đếm ngược, khung câu hỏi, đáp án (`.r0/.r1`),
  đánh dấu đúng/sai khi xem lại, thanh nút cuối trang, bảng tóm tắt trước khi nộp.

  Màu gốc của Moodle là **hex cứng không theo chế độ tối** (`correct #cfc`,
  `partiallycorrect #ffa`, `notanswered/incorrect #fcc`, viền `#bbb`) — đã chuyển hết sang token
  `--ntt-*` nên chế độ tối tự theo.

  Giữ nguyên bộ đèn tín hiệu của Moodle (viền 3px trên/dưới) vì đó là tín hiệu thị giác **thứ hai**
  ngoài màu nền — người mù màu vẫn phân biệt được đúng/sai nhờ vị trí viền.

#### Sửa lỗi

- **Trạng thái `:active` gây khó chịu — tắt toàn bộ.**
  *Nguyên nhân:* `_buttons.scss` có `transform: translateY(1px)` khi nhấn, cộng mỗi loại nút còn
  tự làm tối thêm 32% (`$btn-active-bg-shade-amount`), nên khi bấm nút bị "tụt xuống" và đổi màu
  — trông giật, không đồng nhất.
  *Xử lý:* gán biến `--bs-btn-active-*` bằng đúng biến `--bs-btn-hover-*` và đặt
  `$btn-active-bg-shade-amount: 0%`. Nhấn chuột giờ trông y hệt hover — không đổi màu, không đổi
  viền, không đổi chữ. Làm ở một chỗ (`.btn`) là phủ mọi loại nút.
  Giữ `:active` ở `.dropdown-item` vì đó là **mục đang chọn** (trạng thái cố định), không phải
  hiệu ứng nhấn.

#### Ba cái bẫy đã gặp thật khi viết `_quiz.scss`

1. `#quiz-timer` có class `ms-auto` để đẩy sang phải — đổi `display` sang `inline-flex` là
   `margin-left: auto` mất tác dụng, đồng hồ nhảy về trái.
2. `module.js:193` gắn `timeleft<N>` lên **chính** `#quiz-timer`, không phải khung ngoài ⇒
   selector cảnh báo phải là `#quiz-timer[class*="timeleft"]`.
3. `#page-mod-quiz-attempt` / `-review` / `-summary` là ID của **`<body>`**
   (`pagelib.php:magic_get_bodyid` trả `'page-'.$pagetype`). Đặt chúng trong `.path-mod-quiz`
   (cũng trên `<body>`) thì selector **không bao giờ khớp** — một phần tử không thể là tổ tiên
   của chính nó. Đã tách hết ra cấp root.

Biên dịch kiểm chứng bằng dart-sass thật: 220 dòng CSS, **0 lỗi**.

### Khảo sát giao diện Moodle để custom UI

#### Thêm mới

- **`docs/khao-sat-giao-dien-moodle.md`** — khảo sát `theme_ntt` và mã nguồn Moodle 5.3, xác định
  **vùng nào đã style, vùng nào còn trống**, để custom UI mà không chạm logic PHP.

  *Nền móng hiện có (rất tốt, không nên phá):* theme con của Boost, 3 tầng SCSS
  (`pre.scss` đè token Moodle Design System → `post.scss` import 11 tệp ghi đè), phông
  Be Vietnam Pro nhúng sẵn, có biến cho chế độ tối, trang cài đặt đổi màu không cần sửa tệp.
  Chỉ ghi đè **1** template Mustache (`core/login_layout.mustache`) — con số rất tốt.

  *Đã phủ (10 nhóm):* thanh trên, drawer trái/phải, nút, thẻ, form, bảng, huy hiệu/alert/toast,
  breadcrumb/phân trang/tab, ngân hàng câu hỏi, trang đăng nhập.

  *Còn trống:* trang khoá học (`.section-item`, `.activity-grid`…), quiz (làm bài + xem lại),
  diễn đàn, `tertiary-navigation`, hộp thoại/popover, lịch, tin nhắn, filepicker.

  *Phát hiện quan trọng nhất — mỗi module có CSS riêng rất lớn:* `mod/quiz/styles.css`
  **1219 dòng**, `mod/forum/styles.css` **334 dòng**, và theme hiện **không** import/ghi đè
  dòng nào của chúng. Nghĩa là vào trang quiz sẽ thấy Bootstrap của NTT chồng lên 1219 dòng CSS
  gốc của Moodle — giao diện nửa vời. Tài liệu ghi rõ phải đọc CSS gốc **trước** khi ghi đè, và
  mẹo bọc trong `.path-mod-quiz { }` (Moodle tự thêm `path-mod-<tên>` vào `<body>`) để vừa tăng
  độ ưu tiên vừa giới hạn phạm vi.

  *Ba rủi ro:* Theme designer mode rất chậm (phải tắt khi chạy thật), sửa `.mustache` bắt buộc
  xoá cache kể cả khi bật designer mode, và đừng ghi đè template lõi khi CSS làm được.

### Rà soát Spring Boot — vòng 2 (sau khi merge lên `feature`)

#### Thêm mới

- **`docs/review-spring-boot-can-va-vong2.md`** — review lại toàn bộ `be-web-admin` trên
  `feature@c82f0686c`. Không lặp lại tài liệu vòng 1: mọi khẳng định đều kiểm chứng lại trên code
  hiện tại, kèm dòng cụ thể.

  **4 vấn đề MỚI phát hiện:**

  1. 🟠 `AiCheckService.job(taskId)` (dòng 148) và `suggestions(taskId)` (dòng 167) **không nhận
     `adminId`**, không kiểm tra chủ sở hữu. `AiJobController:40-48` cũng không truyền
     `Authentication`. Ai có `taskId` là đọc được đề xuất AI (kèm nội dung câu hỏi + đáp án đúng)
     của môn khác. Trong khi `pushActivity` **có** ghi `adminId` — hệ thống biết ai tạo task,
     chỉ là không dùng.
  2. 🟠 **Không có chức năng khôi phục câu hỏi đã lưu trữ.** `archive()` đặt `ARCHIVED` rồi
     `update()` chặn vĩnh viễn ⇒ lỡ tay "Lưu trữ" (nhất là "Lưu trữ hàng loạt") là mất câu hỏi,
     dữ liệu còn trong DB nhưng không dùng lại được. Đáng chú ý: hằng
     `ChangeSourceD.RESTORE("restore")` **đã có sẵn** trong enum và `SQL_TYPE` đã khai báo
     — CSDL thiết kế sẵn cho việc này, chỉ là chưa cài đặt.
  3. 🟠 **Đăng nhập không giới hạn số lần thử mật khẩu.** Phần TOTP làm rất tốt (khoá 5 phút, và
     có chủ ý chỉ đếm *sau* khi mật khẩu đúng để tránh bị dùng làm công cụ DoS), nhưng bước kiểm
     tra mật khẩu thì **không đếm gì**. `adminId` là số tăng dần và hiện trên form ⇒ dò vét cạn
     được. BCrypt làm chậm nhưng không chặn.
  4. 🟡 `update()` **thiếu kiểm tra môn còn hoạt động**: `create()` có `SUBJECT_INACTIVE` (dòng
     203) và `orElseThrow`, còn `update()` dùng `orElse(null)` (dòng 285) ⇒ vô hiệu hoá môn rồi
     vẫn sửa được câu hỏi của môn đó, và môn bị xoá thì lặng lẽ trả `subjectName = null`.

  **2 lỗ hổng vòng 1 vẫn chưa vá:** `ROLE_ADMIN` hard-code ở `JwtAuthenticationFilter:52` (kiểm
  chứng: `grep -rn "PreAuthorize|hasRole"` → 0 kết quả) và không có phân công môn ở backend.

  **8.3 điểm làm TỐT** đáng ghi vào báo cáo đồ án: chống timing attack khi login (luôn chạy
  BCrypt giả kể cả admin không tồn tại), không ghi mã TOTP vào log, Redis bọc try/catch toàn bộ
  (graceful degradation).

  **2 điểm hiệu năng:** tìm kiếm dùng `LIKE '%x%'` trong khi bảng có `FULLTEXT KEY ft_q_text`
  (không dùng được index, và `title` không nằm trong fulltext); `revNo = max + 1` không khoá.

  **1 điểm nên cân nhắc:** `QbankExceptionHandler` không có fallback
  `@ExceptionHandler(Exception.class)` ⇒ lỗi lạ trả HTML, frontend Vue không parse được.
  Đã ghi 15 hạng mục **kiểm tra và không có vấn đề** (bao gồm việc xác nhận
  `revision.getRevNo() != latestRev` là **đúng** — có 1 vế `int` nên Java unbox, không phải so
  tham chiếu).

### Rà soát Spring Boot — tài liệu "tính năng cần vá"

#### Thêm mới

- **`docs/review-spring-boot-can-va.md`** — rà trực tiếp mã nguồn `be-web-admin`, kết luận 4 việc:

  1. 🔴 **Mọi token đều được cấp `ROLE_ADMIN`.** `security/jwt/JwtAuthenticationFilter.java:52`
     gán cứng `new SimpleGrantedAuthority("ROLE_ADMIN")`, không đọc vai trò thật. Cộng với việc
     `SecurityConfig` chỉ có `anyRequest().authenticated()` và **không một `@PreAuthorize` nào**
     trong toàn bộ `src/main/java` ⇒ tài khoản `LECTURER` gọi được API quản trị (`/web/admin/admins`),
     API tuyển sinh và toàn bộ API mobile. Bẫy: nếu chỉ thêm `@PreAuthorize("hasRole('ADMIN')")`
     mà không sửa filter thì **không chặn được ai**, vì ai cũng mang ROLE_ADMIN.
  2. 🔴 **Không có phân công môn ở backend.** Giao diện đã lọc theo `subjOk` / `myScope`
     (`store.js:534-535`) nhưng dữ liệu lấy từ **mock**; CSDL không có bảng nối giảng viên–môn,
     service không kiểm tra. Gọi API trực tiếp là sửa được câu của môn bất kỳ.
  3. 🟠 **Trùng nội dung chỉ ghi log.** `QuestionService:266-269` tính `duplicate` (theo
     `content_hash`) rồi `log.info`, **không trả cờ** trong `QuestionDetail` ⇒ người dùng tạo câu
     trùng mà không biết. Đề xuất thêm `duplicateCount` + toast cảnh báo mềm (không chặn).
  4. 🟡 **Không có test tầng API.** 6 tệp test đều là unit/smoke, không có `@WebMvcTest` ⇒ các
     quy tắc `TITLE_DUPLICATED`, `VERSION_CONFLICT`, `QUESTION_ARCHIVED` không được bảo vệ.

  Tài liệu cũng ghi rõ **11 hạng mục đã kiểm tra và không có vấn đề** (khoá lạc quan, xoá mềm,
  phân trang có trần, N+1 đã batch, chống gửi trùng AI, timeout worker, FK ghép chặn `topicId`
  khác môn…) để tránh rà lại và để trả lời phản biện.

### Giao diện: icon hamburger

#### Sửa lỗi

- **Icon hamburger ba gạch không đều nhau (thanh thứ ba mảnh hơn).**
  *Triệu chứng:* icon menu ở thanh trên cùng trông "lệch" — đo trên ảnh chụp cho thấy ba gạch
  dài bằng nhau (30px) và cách đều (7px) nhưng **thanh 3 dày 2px trong khi thanh 1–2 dày 3px**.
  *Nguyên nhân:* icon vẽ bằng 3 thẻ `<span>` với `height:2px` + `gap:5px` trong nút cao 44px.
  Tổng cao nội dung 16px, căn giữa còn dư 14px ⇒ mỗi thanh rơi vào toạ độ **nửa pixel**, trình
  duyệt làm tròn khác nhau cho từng thanh.
  *Xử lý:* thay bằng **một** thẻ `<svg viewBox="0 0 24 24">` với **một** `stroke-width="2"` và
  ba đường kẻ tại `y = 7, 12, 17`. Cả ba dùng chung một stroke nên **luôn** dày bằng nhau, ở mọi
  mức zoom và mọi tỉ lệ màn hình (Retina, 125%, 150%…). CSS bỏ rule `.topbar__toggle span`,
  chuyển sang `color:#fff` + `currentColor`.

### Nối API Spring Boot ↔ CSDL và nạp 500 câu hỏi mẫu

#### Thêm mới

- **`be-web-admin/src/main/java/.../data_init/QbankDataInitializer.java`** — seed 5 môn × 3 chương
  × 100 câu = **500 câu trắc nghiệm thật** vào `ntt_subjects`, `ntt_topics`, `ntt_questions`,
  `ntt_question_revisions`.
  * Bật bằng cùng cờ `APP_SEED_ENABLED=true` như `DataInitializer`.
  * **Không ghi đè dữ liệu thật**: nếu `ntt_subjects` đã có bất kỳ môn nào thì thoát ngay.
  * Dùng chính `QuestionContentHasher` và `QuestionSnapshotFactory` của production, nên câu
    seed có đúng `content_hash` và `snapshot_json` như câu tạo qua API.
  * 10 mẫu nội dung/chương được xoay vòng thành 33–34 câu bằng **kỹ thuật đảo phương án**
    (4 kiểu đảo), nên 500 `content_hash` khác nhau hoàn toàn và chống học vẹt theo vị trí
    đáp án. Đáp án đúng được dịch theo phép đảo, đã kiểm chứng không sai lệch.

- **`be-web-admin/sql/2026-11-23-qbank-seed-500-cau.sql`** — bản SQL tương đương (2173 dòng)
  để nạp khi không muốn chạy Spring Boot.
  `mysql -u root -p <db> < be-web-admin/sql/2026-11-23-qbank-seed-500-cau.sql`
  Idempotent bằng `INSERT IGNORE` trên `subject_code`; có sẵn script reset ở cuối file.

#### Sửa lỗi

- **Thông báo lưu câu hỏi lộ thuật ngữ kỹ thuật.**
  *Vấn đề:* toast ghi `"Đã lưu câu hỏi vào cơ sở dữ liệu"` — người dùng cuối không cần biết
  "cơ sở dữ liệu", và câu chữ không theo mẫu của CRUD môn học.
  *Xử lý:* `store.js` đổi thành **"Đã thêm câu hỏi"** / **"Đã cập nhật câu hỏi"** (nhánh thật và
  nhánh mock đều sửa). Môn học giữ nguyên "Đã lưu môn học mới" / "Đã cập nhật thông tin môn học".

- **Tiêu đề câu hỏi trùng trong cùng môn — chặn khi THÊM MỚI, không chặn khi SỬA.**
  *Vấn đề:* trước đây có thể tạo hai câu hỏi cùng môn với tiêu đề giống hệt nhau (khác
  hoa/thường, thừa khoảng trắng), khiến danh sách tra cứu không phân biệt được.
  *Xử lý:*
  * `QuestionRepository.findDuplicateTitleInSubject` — so `lower(q.title)` trong cùng
    `subject_id`, **bỏ qua câu `ARCHIVED`**, trả về danh sách để service báo được mã câu trùng.
  * `QuestionService.assertTitleAvailable(...)` ném **409 `TITLE_DUPLICATED`** kèm câu trùng
    (`"Tiêu đề "X" đã được dùng cho câu hỏi #N trong cùng môn học"`). **Chỉ gọi từ `create()`**.
  * `QuestionDialog.vue` chặn trước khi gọi API (`dupTitle`), báo lỗi ngay tại ô tiêu đề.
  * Tiêu đề **để trống không coi là trùng** — cột `title` nullable và không bắt buộc.
  * *Ghi chú kỹ thuật:* tham số `excludeId` **phải truyền `-1`, không được truyền `null`**,
    vì `question_id <> NULL` trong SQL luôn cho `UNKNOWN` ⇒ query trả rỗng ⇒ việc chặn trùng
    mất tác dụng trong im lặng.

- **Sửa lỗi logic: form SỬA câu hỏi vẫn báo trùng tiêu đề, khoá không lưu được câu cũ.**
  *Triệu chứng:* mở form "Sửa CH010" với tiêu đề `Interface` thì báo đỏ
  `"Tiêu đề này đã dùng cho CH030 trong cùng môn"` và **nút Lưu câu hỏi không có tác dụng**.
  *Nguyên nhân:* quy tắc chặn trùng bị áp cho **cả `update()`**, trong khi yêu cầu chỉ là
  *"câu hỏi **thêm mới** phải khác tiêu đề câu khác"*. Những câu đã trùng tiêu đề từ **trước**
  khi có quy tắc (CH010 và CH030 cùng tên `Interface`) rơi vào thế khoá vĩnh viễn: mở câu nào
  cũng bị chính câu kia chặn, muốn sửa nội dung buộc phải đổi tiêu đề.
  *Xử lý:*
  * Backend: bỏ hẳn `assertTitleAvailable(...)` khỏi `update()`, chỉ giữ ở `create()`
    (có chú thích giải thích tại sao **không** được kiểm tra khi cập nhật).
  * UI: `dupTitle` và `dupStem` chỉ tính khi `isNew` (form **thêm mới**). Form sửa **không
    kiểm tra, không cảnh báo** trùng lặp với câu khác — kể cả trùng câu dẫn. Sửa nội dung một
    câu có sẵn không tạo ra bản trùng mới, và chính câu đó đang được sửa.
  * Chế độ mock (`store.js`): thêm kiểm tra ở nhánh thêm mới, vì trước đó mock ghi thẳng —
    lời gọi `saveQuestion` không qua form sẽ lách được quy tắc.
  * Xoá `existsDuplicateTitleInSubject` (đã thành dead code, không nơi nào dùng).

- **Cảnh báo trùng lặp hiện cả ở form SỬA — phải chỉ hiện ở form THÊM.**
  *Triệu chứng:* mở `Sửa CH003` thấy **hai** dòng cảnh báo: ô tiêu đề báo
  `"Cảnh báo CH033 trong cùng môn học — vẫn lưu được…"` và ô câu dẫn báo
  `"Câu dẫn trùng với CH033 trong cùng môn học…"`, trong khi người dùng chỉ định sửa nội dung.
  *Nguyên nhân:* bước sửa trước đã gỡ phần **chặn** nhưng để lại phần **cảnh báo** trong form
  sửa (`dupTitleWarn`, `dupStem` tính cho mọi chế độ). Yêu cầu chỉ đặt ra cho câu **thêm mới**.
  *Xử lý:* gộp về một chỗ — `isNew = !props.id`; cả `dupTitle` (chặn) và `dupStem` (cảnh báo mềm)
  đều `return null` khi không phải thêm mới. Form sửa không còn dòng cảnh báo trùng nào.
  Giữ nguyên cảnh báo **hai phương án giống nhau** ở cả hai form vì đó là kiểm tra nội dung
  của chính câu đang nhập, không phải trùng lặp giữa các câu.

- **Môn học / chương: chuẩn hoá cả hai phía khi so trùng.**
  *Vấn đề:* `CourseDialog.vue` và `TopicManagerDialog.vue` chỉ `trim()` dữ liệu **vừa nhập**,
  còn dữ liệu **đã lưu** thì so nguyên bản ⇒ một môn đã lưu kèm khoảng trắng thừa
  (`"Cấu trúc  dữ liệu "`) vẫn lọt qua bước kiểm tra trùng.
  *Xử lý:* thêm `keyOf()` / `key()` chuẩn hoá (trim + gộp khoảng trắng + lowercase) áp cho
  **cả hai vế** của phép so sánh.

- **`AnswerNumberingD` thiếu 2 giá trị của Moodle.**
  *Vấn đề:* Moodle hỗ trợ **6** kiểu đánh số đáp án
  (`question/type/multichoice/questiontype.php:324` —
  `abc`, `ABCD`, `123`, `iii`, `IIII`, `none`), nhưng enum và cột
  `ntt_moodle_profiles.answer_numbering` chỉ có **4**. Profile đọc từ Moodle dùng `iii`/`IIII`
  sẽ ném `IllegalArgumentException` trong `fromDbValue()` ⇒ HTTP 500; nếu DB chạy non-strict
  thì giá trị bị ghi rỗng và mất dữ liệu âm thầm.
  *Xử lý:* thêm `LOWER_ROMAN("iii")`, `UPPER_ROMAN("IIII")`; cập nhật `AnswerNumberingD.SQL_TYPE`
  và `sql/2026-11-19-02-business-tables.sql:290`; thêm migration
  `sql/2026-11-23-answer-numbering-moodle-6-gia-tri.sql` cho CSDL đang chạy.

- **Dữ liệu seed có tiêu đề trùng, vi phạm quy tắc vừa thêm.**
  *Vấn đề:* `QbankDataInitializer` gán `q.setTitle(bp.title())`, mà mỗi chương chỉ có 10 mẫu
  nội dung cho 33–34 câu ⇒ mỗi tiêu đề lặp 3–4 lần trong cùng môn.
  *Xử lý:* thêm hậu tố ` – câu <n>` với `n` chạy **liên tục trong toàn môn**
  (`created + 1`, không phải `i + 1` — dùng `i + 1` sẽ reset ở mỗi chương và lại trùng).
  Đã kiểm chứng: 100 tiêu đề/môn, **0 trùng** ở cả 5 môn.


- **`Column 'question_id' cannot be null` — ứng dụng KHÔNG khởi động được.**
  *Triệu chứng:* log Spring ném `DataIntegrityViolationException` khi insert
  `ntt_question_revisions` (SQLState 23000, errno 1048), tại `QbankDataInitializer.createQuestion`.
  *Nguyên nhân:* `Question` có `@Version`; Spring Data chọn `persist()` hay `merge()` theo
  `isNew()`, mà với `@Version` thì `isNew() = (version == null)`. Code cũ gán
  `question.setVersion(0)` **trước** khi lưu, khiến Spring Data coi entity đã tồn tại và gọi
  `merge()`. `merge()` **không gán id vào entity gốc** — nó trả về bản copy. Vì vậy
  `question.getQuestionId()` vẫn `null`, và bước tạo revision ghi NULL vào cột `question_id`.
  *Cách sửa:* bỏ `setVersion(0)` để Hibernate tự khởi tạo version khi `persist()`, và luôn dùng
  entity **trả về** từ `save()` (`Question saved = questions.saveAndFlush(question)`).
  *Phạm vi quan trọng:* lỗi này **không chỉ ở seed** — `QuestionService.create()` có cùng
  đoạn code, nghĩa là tạo câu hỏi qua `POST /web/admin/questions` **cũng hỏng y như vậy**.
  Đã sửa cả hai nơi. Khóa lạc quan vẫn hoạt động bình thường.

- **Phân quyền phía UI sai: mọi tài khoản đều thấy menu Quản trị.**
  `GET /information/basic` trước đây **không trả `role`**, nên `authStore.js` luôn để
  `auth.role = 'admin'` và `App.vue` hiện mọi mục cho cả giảng viên.
  *Backend:* `AdminServiceImpl.getAdminInfo()` trả thêm `role` = `ADMIN` | `LECTURER`
  (lấy từ cột `ntt_admins.role`).
  *Frontend:* `authStore.loadMe()` ánh xạ `ADMIN → 'admin'`, `LECTURER → 'gv'`.
  Không có trường `role` (backend cũ) thì **giữ nguyên** giá trị hiện tại — không hạ quyền ngầm.

- **Tìm kiếm chưa dùng API thật.**
  `SearchView` chỉ lọc mảng đã tải trong bộ nhớ. Nay khi `dataMode === 'real'` nó gọi
  `GET /questions?q=…&subjectId=…` trên Spring Boot rồi lấy chi tiết từng câu; chế độ demo
  vẫn giữ nguyên đường phân tích cục bộ. Giao diện có nhãn phân biệt "kết quả từ máy chủ"
  với kết quả cục bộ, và hiện lỗi API nếu gọi thất bại.
  *Lưu ý:* tham số tìm kiếm của backend tên là **`q`** (`@RequestParam(name = "q")`),
  **không phải `keyword`** — gửi sai tên sẽ bị bỏ qua im lặng vì `required = false`.

#### Hiệu năng — giảm 99% số request

- **N+1 request khi tải ngân hàng câu hỏi.**
  *Vấn đề:* `store.js` tải danh sách rồi gọi `GET /questions/{id}` cho **từng** câu để lấy 4
  phương án (vì `QuestionListItem` chỉ trả `stemPreview` cắt ngắn). Với 500 câu: 5 request danh
  sách + 500 request chi tiết = **505 request**; trình duyệt giới hạn ~6 kết nối/domain nên
  phải chạy ~83 lượt tuần tự, màn hình treo hàng chục giây.
  *Cách xử lý:* thêm `stem`, `options[]`, `correctLabel`, `generalFeedback`, `contentFormat`
  vào `QuestionListItem`, kèm tham số `includeOptions` (mặc định `false` để không đổi kích
  thước response cho nơi chỉ cần xem nhanh). Giữ constructor 13 tham số cũ nên code đang gọi
  không vỡ. Vue gửi `includeOptions=true` và dùng thẳng dữ liệu trả về.
  *Kết quả:* **505 → 5 request (−99%)**. Vẫn gọi `GET /questions/{id}` khi cần làm mới **một**
  câu sau khi sửa — đúng thiết kế.
  *Kiểm chứng:* 18 component của record / 13 tham số constructor phụ / 18 đối số `this(...)`
  khớp nhau; Vue build 139 modules, `[OK] 55 file`.

#### Chưa xử lý (ghi nhận, cần làm tiếp)

- **AdminView** vẫn đọc `S.users` từ `src/data/mock.js` — backend **chưa có API liệt kê admin**.
- **CoursesView** tạo/sửa môn vẫn là mock — backend chưa có API CRUD môn/chương.
- ~~**N+1 khi tải câu hỏi**~~ — **đã sửa** (xem mục Hiệu năng bên dưới).

## [Chưa phát hành] — 2026-11-23

### Thêm mới

- **`docs/review-api-contract-vue3-springboot.md`** — tài liệu đối chiếu hợp đồng API giữa
  Vue 3 và Spring Boot. Kiểm chứng **5 nhóm endpoint**, **4 hợp đồng DTO**, cơ chế khóa đăng nhập
  hai phía, và **24 bảng entity**. Ghi nhận 7 vấn đề thiết kế kèm hướng khắc phục, trong đó
  nghiêm trọng nhất là **N+1 request** khi tải ngân hàng câu hỏi (§5.1).

- **`qbank-console-vue3/tools/check-build-syntax.mjs`** — công cụ kiểm tra cú pháp dùng **chính
  compiler của dự án** (`@vue/compiler-sfc` cho `.vue`, `node --check` cho `.js`).
  Thêm hai script npm: `npm run check:syntax` và `npm run fix:syntax`.

### Sửa lỗi

- **Công cụ phát hiện smart quote cũ báo động giả 34 lần**
  (`tools/fix-smart-quotes.py`, thêm ở `74d252d07`).
  *Vấn đề:* script coi **mọi** dấu `"…"` (U+201C/U+201D) là lỗi, trong khi dự án **cố ý** dùng
  chúng để hiển thị tiếng Việt cho đẹp (`toast('Đã thêm chương "Toán"…')`, template, `log`).
  Ngược lại, nó bỏ sót smart quote nằm trong regex literal.
  *Cách xử lý:* thay bằng `check-build-syntax.mjs` — chỉ báo khi **compiler thật sự từ chối**
  file, định vị theo `dòng:cột` như Vite. Chế độ `--fix` chỉ đổi smart quote **trên đúng dòng
  compiler phàn nàn**, nên sau khi sửa khôi phục **byte-for-byte** (đã verify sha256
  `ef2bd6f7…` của `AccountView.vue`).
  *Đã kiểm chứng:* 55 file sạch không báo động giả; tái hiện đúng lỗi `'admin'` → `'admin'`
  thì báo `AccountView.vue:30 Unexpected character '…' (30:38)`.

- **Mất nhánh `arena/…` trên remote** — nhánh từng không còn trong `git ls-remote`.
  *Cách xử lý:* đưa nhánh lên đúng bằng `feature` bằng fast-forward rồi push lại.

### Thay đổi đáng chú ý trước đó trong đợt này

- **`48806e6da`** — 3 góp ý sau khi test form đăng nhập:
  phím **ENTER** chạy đúng hàm của nút Đăng nhập; title tab = **`Đăng nhập`** (bỏ dấu chấm dư,
  sửa cả gốc ở `App.vue`); bỏ mũi tên `arrow-right` trong nút.
  Logic đăng nhập vẫn giữ nguyên 100% so với `fe_web_admin`.

- **`499287002`** — thiết kế lại form đăng nhập chuẩn UI/UX (layout 2 cột, 6 ô OTP riêng biệt,
  autofocus, khóa form khi `LOCKED` có đếm ngược lưu `localStorage`).
  **Không đổi logic**: cùng luồng `OK | TOTP_REQUIRED | TOTP_INVALID | LOCKED | FAILED`,
  cùng `withTimeout(20 000)` và cùng validation.

- **`b530db9c9`** — ghép đăng nhập thật + API Spring Boot thay lớp mock.

- **`f2ca32e2a`** — dùng `===` thay `==` khi so sánh vai trò, để nhánh arena và nhánh feature
  không xung đột khi merge (`AccountView.vue`, `UserDetailDialog.vue`).

### Ngoài phạm vi (PHP Moodle)

- **`49b17e288`, `8534fd173`** — chỉnh style quiz và logout của `php_moodle_503/`.
  Không ảnh hưởng hợp đồng API Vue 3 ↔ Spring Boot. Merge vào `feature` không đụng tới
  `qbank-console-vue3/`.

---

## Ghi chú kỹ thuật cho người đọc lại

### Hai tầng tiền tố URL của Spring Boot

Đây là chỗ dễ nhầm nhất khi thêm endpoint mới:

```
server.servlet.context-path: /api          (application.yaml:8)
@RequestMapping("/web/admin/questions")     (QuestionBankController:37)
Vue: ADMIN = '/admin'                       (qbankApi.js:12)
```

⇒ URL đầy đủ: `/api` + `/web` + `/admin` + `/questions`
= **`/api/web/admin/questions`**

Với base mặc định `VITE_SPRING_API_PREFIX=/api/web`, `qbankApi.js` chỉ cần thêm `/admin`.

### Đừng tin `origin/develop` trong môi trường sandbox

`git fetch` trong môi trường này không cập nhật ref của `origin/develop` và `origin/master`,
dẫn tới `git merge-base --is-ancestor` trả **kết quả sai** (báo "thiếu" dù đã merge).

Nguồn chuẩn để kiểm tra trạng thái remote:

```bash
git ls-remote --heads origin        # hỏi thẳng GitHub, không qua ref local
```

### Smart quote làm vỡ build — cách nhận biết

Khi copy code từ chat/Word, `'` có thể bị đổi thành `'` (U+2019) và gây:

```
[plugin:vite:vue] …AccountView.vue:45:87
SyntaxError: Error parsing JavaScript expression: Unterminated string constant
```

Chạy trước khi build để biết ngay:

```bash
cd qbank-console-vue3
npm run check:syntax     # [OK] 55 file sach cu phap
npm run fix:syntax       # tự sửa đúng dòng bị lỗi
```

Sau khi ghi đè file, nhớ xoá cache Vite — nếu không, lỗi cũ vẫn hiện ra:

```bash
rm -rf node_modules/.vite
```

### `q` không phải `keyword`

`QuestionBankController` khai báo `@RequestParam(name = "q", …)`. Gửi `?keyword=…` sẽ bị
**bỏ qua im lặng** vì tham số là `required=false` — xem §5.2 của tài liệu review API.
