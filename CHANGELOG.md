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
  * UI: tách thành `dupTitle` (chỉ khác `null` khi **thêm mới**) và `dupTitleWarn` (luôn tính).
    Form sửa hiện **cảnh báo mềm** `"Cảnh báo ... — vẫn lưu được, nhưng nên đặt tiêu đề khác."`
    thay vì lỗi chặn.
  * Chế độ mock (`store.js`): thêm kiểm tra ở nhánh thêm mới, vì trước đó mock ghi thẳng —
    lời gọi `saveQuestion` không qua form sẽ lách được quy tắc.
  * Xoá `existsDuplicateTitleInSubject` (đã thành dead code, không nơi nào dùng).

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
