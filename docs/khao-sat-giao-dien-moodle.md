# Khảo sát giao diện Moodle 5.3 để custom UI (`theme_ntt`)

> Rà trực tiếp `php_moodle_503/moodle-latest-503/moodle/public/theme/ntt/` và mã nguồn
> Moodle. Ngày 2026-11-23.
> **Phạm vi: chỉ giao diện** (màu, bo góc, hover, focus, bố cục, phông).
> Không sửa một dòng PHP nghiệp vụ nào, không sửa layout Mustache của lõi (trừ 1 tệp đã có).

---

## 0. Kết luận ngắn

Theme `ntt` **đã có sẵn nền móng rất tốt**: theme con của Boost, 13 tệp SCSS, bảng màu đầy đủ
theo Moodle Design System, phông Be Vietnam Pro nhúng sẵn (không gọi Google Fonts), có hỗ trợ
chế độ tối, có trang cài đặt đổi màu không cần sửa tệp.

Nhưng độ phủ **chưa đều**. Đã làm kỹ phần "khung" (thanh trên, drawer, nút, thẻ, bảng, form,
huy hiệu, ngân hàng câu hỏi, đăng nhập). **Còn trống hoàn toàn** những trang người dùng ở lâu
nhất và nhìn nhiều nhất:

| Vùng | Trạng thái | Ưu tiên |
|---|---|---|
| Trang khoá học (danh sách chương, thẻ hoạt động) | ❌ chưa có | 🔴 cao |
| Trang làm bài quiz + xem lại bài | ❌ chưa có | 🔴 cao |
| Diễn đàn (danh sách thảo luận, bài viết) | ❌ chưa có | 🟠 vừa |
| Thanh điều hướng bậc 3 (`tertiary-navigation`) | ❌ chưa có | 🟠 vừa |
| Hộp thoại / modal / popover của Moodle | ⚠️ chỉ có `.modal-content` | 🟠 vừa |
| Lịch, sự kiện, tin nhắn, thông báo đầy đủ | ⚠️ chỉ có phần trong navbar | 🟡 thấp |
| Trình chọn tệp, soạn thảo TinyMCE | ❌ chưa có | 🟡 thấp |

---

## 1. Nền móng hiện có

### 1.1 Kiến trúc — đúng chuẩn, **không nên phá**

Theme con của Boost (`theme/ntt/config.php` khai báo `parents`). Ba tầng SCSS:

```
scss/pre.scss          ← chạy TRƯỚC Boost + Bootstrap: bảng màu, phông, biến Bootstrap
scss/post.scss         ← chạy SAU: chỉ import các tệp ghi đè
scss/ntt/*.scss        ← 11 tệp ghi đè theo nhóm thành phần
```

Cách này rất đúng: `pre.scss` đè **token** (`$mds-color-*`) nên **một chỗ đổi là cả trang đổi
theo**, còn `scss/ntt/*` chỉ xử lý chỗ token không đủ. Mọi biến đều `!default` nên trang cài
đặt vẫn đè được.

### 1.2 Những gì đã phủ (10 nhóm)

| Tệp | Phủ |
|---|---|
| `_navbar.scss` | Thanh trên cùng xanh đặc, menu thả xuống, hộp thông báo |
| `_drawers.scss` | Thanh bên trái (chỉ mục khoá học) + phải (block), nút đóng/mở |
| `_buttons.scss` | Nút chính, phụ dạng viền, nguy hiểm, nút biểu tượng |
| `_cards.scss` | Thẻ, thẻ hoạt động, `list-group`, `modal-content`, `modal-footer` |
| `_forms.scss` | Ô nhập, `form-select`, `form-check`, trạng thái `:invalid`, `mform` |
| `_tables.scss` | `.table`, `flexible`, hover hàng, cuộn ngang |
| `_feedback.scss` | Huy hiệu (7 biến thể), `.alert` (4), `.toast` |
| `_page.scss` | Tiêu đề trang, breadcrumb, phân trang, tab, `nav-pills` |
| `_question.scss` | Bảng ngân hàng câu hỏi, khung câu hỏi `.que`, đáp án đúng/sai |
| `_login.scss` + `templates/core/login_layout.mustache` | Trang đăng nhập dạng thẻ giữa trang |

### 1.3 Bảng màu đã chốt (`pre.scss`)

```
Xanh thương hiệu   #004b87 (chính) · #003a69 (hover) · #e8f1fa (nền nhạt)
Xám pha xanh       #f8faff (nền) · #e3ebf5 (viền) · #1b2a41 (chữ chính) · #5b6b82 (chữ phụ)
Đỏ / Lục / Vàng    #a51818 · #166534 · #9a5b00 (chữ cảnh báo)
Bo góc             8px (nút, ô nhập) · 12px (thẻ)
Phông              Be Vietnam Pro (400/500/600/700, có tập tiếng Việt)
```

---

## 2. Vùng **chưa** có style — cần bổ sung

### 2.1 🔴 Trang khoá học — nơi người dùng ở nhiều nhất

Đây là màn hình trung tâm của Moodle (mở khoá học → thấy các chương → thấy hoạt động).
Bootstrap lo phần cơ bản, nhưng chưa có gì mang dấu ấn NTT.

Class thật của Moodle 5.3 (đọc từ `course/format/templates/local/content/`):

```
.section-item                     khung một chương
.activity-grid                    lưới của một hoạt động
.activity-altcontent              mô tả hoạt động
.activity-dates                   ngày mở/đóng
.activity-completion              ô đánh dấu hoàn thành
.activity-actions                 menu sửa/xoá (chỉ giáo viên)
.activity-groupmode-info          thông tin nhóm
.courseindex-section-title        tiêu đề chương trên drawer trái (đã style)
```

Đề xuất: thêm `scss/ntt/_course.scss` —

```scss
.section-item {
    border: 1px solid var(--ntt-border);
    border-radius: $ntt-radius-card;
    background: var(--ntt-surface);
    // Không dùng !important: Bootstrap của Boost đã đặt margin, chỉ cần đè khoảng cách
}

.activity-grid {
    padding: .625rem .75rem;
    border-radius: $ntt-radius;
    transition: background-color .15s ease;

    &:hover { background-color: var(--ntt-primary-soft); }
}

// Hoạt động đã hoàn thành: đánh dấu bằng màu lục nhạt thay đổi cả nền
.activity-completion .completion-info[data-status="complete"] { color: var(--ntt-success); }

.activity-dates { color: var(--ntt-text-muted); font-size: .8125rem; }
```

> ⚠️ Lưu ý khi làm: `.activity-grid` là **lưới**, không phải thẻ. Đừng bọc viền cho từng
> `.activity-grid` — sẽ ra cảm giác "bảng trong bảng". Nên style ở cấp `.section-item` và chỉ
> cho `.activity-grid` nền khi hover.

### 2.2 🔴 Trang làm bài quiz + xem lại

`mod/quiz/` là hoạt động chính nếu đồ án demo việc đẩy câu hỏi từ ngân hàng sang Moodle.
Hiện **chưa có style nào**. Các vùng cần chăm:

| Vùng | Class / cấu trúc | Việc cần làm |
|---|---|---|
| Khung câu hỏi khi làm bài | `.que` (đã có style cơ bản) | Kiểm tra lại: khi *làm bài* `.que` nằm trong `.quizattempt`, khác ngữ cảnh xem trước |
| Thanh điều hướng câu hỏi | `.qn_buttons`, `.qnbutton` | Style giống chip; câu đã làm vs chưa làm phải phân biệt **bằng cả màu và hình dạng**, không chỉ màu (người mù màu) |
| Đồng hồ đếm ngược | `#quiz-timer` | Làm nổi bật, dùng `--ntt-warning-*`; tránh đỏ vì đỏ = lỗi |
| Nút "Câu tiếp" | `.submitbtns` | Nút chính, và phải **cố định/không bị che** khi cuộn |
| Trang xem lại bài | `.reviewsummary`, `.que .history` | Phân biệt rõ đúng/sai/một phần |

Đề xuất thêm `scss/ntt/_quiz.scss` (khoảng 60–80 dòng).

> 📌 Ghi chú nghiệp vụ: nếu đồ án **chỉ đẩy câu hỏi sang ngân hàng Moodle, không cho sinh viên
> làm bài trên Moodle**, thì vùng 2.2 **không cần** — hãy xác nhận trước khi làm, đỡ tốn công.

### 2.3 🟠 Diễn đàn

```
.forum-post-container      khung một bài viết
.forum-post-core           nội dung
.discussion-list           danh sách thảo luận
.firstpost                 bài đầu (thường đóng vai trò chủ đề)
.discussion-nav            điều hướng bài trước/sau
```

Đề xuất: bài viết là thẻ, ảnh đại diện bo tròn, `.firstpost` có vạch màu thương hiệu bên trái để
phân biệt với trả lời.

### 2.4 🟠 `tertiary-navigation` — thanh điều hướng bậc 3

Moodle 5.3 dùng rất nhiều (trang người dùng tham gia khoá, quản lý danh mục, ngân hàng câu hỏi…).
Chưa có style. Đây là thanh dễ tạo ấn tượng tốt với ít công: chỉ cần nền nhạt, viền dưới, tab
đang chọn có vạch màu.

### 2.5 🟠 Hộp thoại và popover

Hiện chỉ có `.modal-content` + `.modal-footer` trong `_cards.scss`. Còn thiếu:
`.moodle-dialogue` (hộp thoại YUI cũ — vẫn dùng ở vài chỗ), `.popover`, `.tooltip`,
`.dropdown-menu` trong ngữ cảnh thẻ (đã có trong navbar nhưng phạm vi hẹp).

### 2.6 🟡 Còn lại

- **Lịch / sự kiện**: `.calendar`, `.event`, `.block_calendar_month`. Rất nhiều trang có block lịch.
- **Tin nhắn**: `.message-app`, `.message-drawer` (Moodle 5.3 có app tin nhắn riêng).
- **Trình chọn tệp**: `.filepicker`, `.fp-*` — hiện ra khi chèn ảnh/tệp.
- **TinyMCE**: `#fitem_id_*`, `.tox-tinymce` — nếu giáo viên soạn nội dung trực tiếp trên Moodle.

---

## 3. Ba rủi ro cần biết **trước** khi custom

### 3.1 Theme designer mode rất chậm

Bật **Site administration > Appearance > Themes > Theme settings > Theme designer mode** để
mỗi lần tải trang biên dịch lại SCSS — bắt buộc khi đang chỉnh, nhưng phải **tắt khi chạy thật**.
Nếu quên, trang sẽ chậm gấp nhiều lần.

### 3.2 Xoá cache sau mỗi lần sửa template / SCSS

```bash
php admin/cli/purge_caches.php
```

Chỉ sửa SCSS thì designer mode lo; **sửa `.mustache` thì bắt buộc xoá cache** kể cả khi đang bật
designer mode.

### 3.3 Đừng ghi đè template của lõi nếu chỉ cần đổi CSS

Mỗi template bị ghi đè là một điểm hỏng khi nâng cấp Moodle. Hiện chỉ có **1** tệp bị ghi đè
(`templates/core/login_layout.mustache`) — con số rất tốt, nên giữ. Nếu cần đổi bố cục mà CSS
không làm được, cân nhắc kỹ trước.

### 3.4 ⚠️ Mỗi hoạt động của Moodle có **CSS riêng rất lớn** — phải đọc trước khi ghi đè

Đây là điều dễ đánh giá sai nhất. Moodle không chỉ style bằng Bootstrap: **mỗi module có một
`styles.css` riêng**, được nạp tự động khi mở module đó.

| Module | Tệp | Số dòng |
|---|---|---|
| Quiz | `mod/quiz/styles.css` | **1219** |
| Diễn đàn | `mod/forum/styles.css` | **334** |

Theme `ntt` **hiện không import hay ghi đè một dòng nào** của hai tệp này (kiểm chứng:
`grep -rn "mod/quiz\|quiz/styles" theme/ntt/scss/` → rỗng).

Hệ quả: khi vào trang quiz, người dùng thấy **Bootstrap của NTT + 1219 dòng CSS gốc của quiz**
chồng lên nhau. Giao diện vì thế **không đồng bộ**: phần khung đẹp theo NTT, phần nội dung quiz
vẫn là giao diện Moodle mặc định.

Đúng cách làm: mở `mod/quiz/styles.css` và `mod/forum/styles.css` **trước**, tìm selector mình
cần đổi, rồi viết ghi đè trong `scss/ntt/_quiz.scss` / `_forum.scss`. Nếu chỉ đoán tên class rồi
style, rất dễ ra kết quả nửa vời vì CSS gốc có độ ưu tiên cao hơn (nhiều chỗ dùng
`.path-mod-quiz .que .content` — ba cấp).

> 💡 Mẹo nhanh: bọc ghi đè trong `.path-mod-quiz { … }` hoặc `.path-mod-forum { … }`.
> Moodle tự thêm `path-mod-<tên>` vào `<body>`, nên vừa tăng độ ưu tiên vừa **giới hạn phạm vi**
> (không làm hỏng trang khác).

---

## 4. Đề xuất thứ tự làm

| Bước | Việc | Tệp mới/sửa | Công sức |
|---|---|---|---|
| **1** | Trang khoá học | `_course.scss` (mới) + 1 dòng `@import` | ~60 dòng |
| **2** | `tertiary-navigation` + hộp thoại/popover | `_page.scss`, `_cards.scss` (sửa) | ~30 dòng |
| **3** | Quiz (nếu có demo làm bài) | `_quiz.scss` (mới) | ~70 dòng |
| **4** | Diễn đàn | `_forum.scss` (mới) | ~50 dòng |
| **5** | Lịch, tin nhắn, filepicker | `_blocks.scss` (mới) | ~50 dòng |

Sau mỗi bước: `php admin/cli/purge_caches.php` rồi kiểm tra ở cả **chế độ sáng và tối**
(theme đã có sẵn biến cho chế độ tối, nên dùng `--ntt-*` là tự theo).

---

## 5. Bảng tra nhanh vị trí mã nguồn

| Cần xem gì | Tệp |
|---|---|
| Cấu hình theme con, thứ tự SCSS | `theme/ntt/config.php`, `theme/ntt/lib.php` |
| Bảng màu và biến Bootstrap | `theme/ntt/scss/pre.scss` |
| Danh sách ghi đè | `theme/ntt/scss/post.scss` |
| Biến CSS + chế độ tối | `theme/ntt/scss/ntt/_tokens.scss` |
| Trang đăng nhập | `theme/ntt/scss/ntt/_login.scss`, `theme/ntt/templates/core/login_layout.mustache` |
| Trang cài đặt theme | `theme/ntt/settings.php` |
| Class của một hoạt động (khoá học) | `course/format/templates/local/content/cm/activity.mustache` |
| Class của một chương | `course/format/templates/local/content/section.mustache` |
| Class của quiz | `mod/quiz/templates/` |
| **CSS gốc của quiz (1219 dòng — đọc trước khi ghi đè)** | `mod/quiz/styles.css` |
| Class của diễn đàn | `mod/forum/templates/` |
| **CSS gốc của diễn đàn (334 dòng)** | `mod/forum/styles.css` |
| Thư viện SCSS của Boost (để biết cái gì đã có) | `theme/boost/scss/` |
