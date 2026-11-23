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

#### Chưa xử lý (ghi nhận, cần làm tiếp)

- **AdminView** vẫn đọc `S.users` từ `src/data/mock.js` — backend **chưa có API liệt kê admin**.
- **CoursesView** tạo/sửa môn vẫn là mock — backend chưa có API CRUD môn/chương.
- **N+1 khi tải câu hỏi**: `store.js` gọi `GET /questions` rồi `GET /questions/{id}` từng câu
  để lấy 4 phương án. Với 500 câu seed, mở màn Ngân hàng câu hỏi sẽ phát sinh ~505 request.
  Hướng sửa: thêm `options[]` vào `QuestionListItem` (xem §5.1
  `docs/review-api-contract-vue3-springboot.md`).

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
