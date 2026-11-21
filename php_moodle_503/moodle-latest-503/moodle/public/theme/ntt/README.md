# Theme NTT (`theme_ntt`)

Giao diện Moodle 5.3 theo thiết kế của đề án (`project-design/UI_Accept_v2.html`): xanh `#004b87`, chữ Be Vietnam Pro,
bo góc 8px (thẻ 12px), huy hiệu dạng viên thuốc nhạt màu, thanh trên cùng xanh đặc.

Đây là **theme con của Boost**. Không có code nghiệp vụ nào bị đổi: không sửa PHP của Moodle, không sửa template, không
sửa layout. Mọi thứ khác Boost nằm trong `scss/` và `fonts/`.

## Cài đặt

Chép thư mục này vào `moodle/public/theme/ntt` (hoặc `git apply` bản patch đi kèm), rồi:

```bash
php admin/cli/upgrade.php --non-interactive      # đăng ký plugin theme_ntt (hoặc vào Site administration > Notifications)
php admin/cli/cfg.php --name=theme --set=ntt     # bật theme (hoặc Site administration > Appearance > Themes > Theme selector)
php admin/cli/purge_caches.php                   # xoá cache CSS để Moodle biên dịch lại SCSS
```

Trong lúc chỉnh SCSS, bật **Site administration > Appearance > Themes > Theme settings > Theme designer mode**, mỗi lần tải trang
Moodle biên dịch lại SCSS, không cần xoá cache. Nhớ tắt khi chạy thật vì rất chậm.

## Cấu trúc

| Tệp | Vai trò |
|---|---|
| `config.php`, `lib.php`, `version.php` | Khai báo theme con (parents = Boost), ghép SCSS. Không có nghiệp vụ. |
| `scss/pre.scss` | **Bảng màu và biến**. Chạy trước Bootstrap. Muốn đổi màu hay cỡ chữ thì sửa ở đây. |
| `scss/post.scss` | Danh sách import các tệp ghi đè trong `scss/ntt/`. |
| `scss/ntt/_tokens.scss` | Biến CSS `--ntt-*` (có bản chế độ tối). |
| `scss/ntt/_fonts.scss` | `@font-face` Be Vietnam Pro (tập `vietnamese`, `latin`, `latin-ext`; đậm 400/500/600/700). |
| `scss/ntt/_navbar.scss` | Thanh trên cùng xanh đặc, menu thả xuống. |
| `scss/ntt/_drawers.scss` | Thanh bên trái (chỉ mục khoá học) và phải (block). |
| `scss/ntt/_buttons.scss`, `_cards.scss`, `_forms.scss`, `_tables.scss`, `_feedback.scss`, `_page.scss` | Thành phần dùng chung. |
| `scss/ntt/_question.scss` | Ngân hàng câu hỏi: bảng danh sách, xem trước câu trắc nghiệm (đáp án đúng/sai). |
| `scss/ntt/_login.scss` | Trang đăng nhập. |
| `fonts/be-vietnam-pro/` | Phông (giấy phép OFL 1.1, xem `OFL.txt` và `thirdpartylibs.xml`). |
| `settings.php` | Trang cài đặt: màu thương hiệu, SCSS thô (để chỉnh nhanh mà không sửa tệp). |

## Cách hoạt động

Moodle 5.3 dựng màu từ bộ token Design System (`$mds-color-*`). Boost đọc token này để ra `$primary`, `$success`, `$gray-*`
của Bootstrap, nên `pre.scss` chỉ cần đè token (mọi biến đều có `!default`, nên trang cài đặt vẫn đè được). Phần
`scss/ntt/*` chỉ làm những chỗ đổi token không đủ (thanh trên, nút phụ dạng viền, huy hiệu nhạt, đáp án câu hỏi...).

Bảng đối chiếu thiết kế sang Moodle:

| Thiết kế (`:root`) | Moodle |
|---|---|
| `--primary #004b87` | `$mds-color-blue-500` (→ `$primary`, liên kết, nút chính) |
| `--primary-hover #003a69` | `$mds-color-blue-600`, nút hover (`$btn-hover-bg-shade-amount: 22%`) |
| `--primary-soft #e8f1fa` | `$mds-color-blue-50`, biến `--ntt-primary-soft` |
| `--text #1b2a41` / `--text-muted #5b6b82` | `$mds-color-gray-900` / `gray-600` |
| `--bg #f8faff` / `--border #e3ebf5` / `--border-strong #8596ad` | `gray-100` / `gray-300` / `gray-500` |
| `--danger-strong #a51818` | `$mds-color-red-500` (→ `$danger`) |
| `--success-strong #166534` | `$mds-color-green-500` (→ `$success`) |
| `--warning-strong #9a5b00` | `$mds-color-yellow-700` (chữ cảnh báo: `yellow-800`) |
| `--radius 8px`, thẻ 12px | `$border-radius`, `$card-border-radius` |

## Đổi màu nhanh, không sửa tệp

Site administration > Appearance > Themes > NTT: ô **Màu thương hiệu** đổi `$primary`; **SCSS thô khởi tạo** dùng để đè
token, ví dụ `$mds-color-blue-500: #0b5cab;`. Sau khi lưu Moodle tự xoá cache CSS.

## Chế độ tối

Boost 5.3 có chế độ tối (`data-bs-theme="dark"`); các quy tắc của NTT dùng biến `--ntt-*` và `--bs-*` nên theo được chế độ này.
Thanh trên cùng giữ màu thương hiệu ở cả hai chế độ.

## Gỡ theme

Đổi theme về Boost (`php admin/cli/cfg.php --name=theme --set=boost`), rồi gỡ plugin ở Site administration > Plugins >
Plugins overview. Vì theme chỉ có SCSS, không có bảng dữ liệu nào cần dọn.
