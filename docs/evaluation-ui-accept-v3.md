# Đánh giá trực quan file mẫu giao diện: `project-design/UI_Accept_v3.html`

**Đối tượng đánh giá:** `project-design/UI_Accept_v3.html` (~157 KB / 1.749 dòng) — bản giao diện tham chiếu (mock UI) cho đề án *Kiểm định chất lượng câu hỏi trắc nghiệm Moodle* (ĐH Nguyễn Tất Thành, 2027).
**Vị trí:** nhánh `feature/apply-ui-project-thesis`, commit `22616a80c` ("Fixed Login, TOTP, Changed theme moodle.").
**Ngày đánh giá:** 2026-11-21. **Phạm vi:** chỉ đánh giá, không sửa code.

---

## Phương pháp & độ tin cậy

Không có trình duyệt thật trong môi trường sandbox. Đánh giá dựa trên 3 lớp:

1. **Đọc mã nguồn tĩnh** (toàn bộ CSS design-system + HTML shell + logic JS + render dispatcher).
2. **Chạy mô phỏng DOM runtime** bằng jsdom với stub tối thiểu (canvas đo font, `<dialog>`, matchMedia, scrollIntoView) — đo lường được hành vi thật trên tất cả trang và các luồng chính.
3. **Chưa thể verify:** pixel layout thật, font loading, hiệu năng thực tế, hiệu ứng CSS/animation trực quan. **Không có screenshot thật.**

Kết quả này dùng để đánh giá *chất lượng thiết kế*, không thay thế 1 pass kiểm tra trực quan cuối cùng trong trình duyệt.

---

## 1. Tổng quan (Executive summary)

**Đánh giá: ✅ Đạt mức "giao diện tham chiếu tốt" (high-quality design reference)** — có thể dùng làm khuôn mẫu cho fe_web_admin mới.

| Hạng mục | Mức | Nhận xét |
|---|---|---|
| Khung / shell | Tốt | Topbar sticky + sidebar sticky/overlay + main + selbar + toast + dialog — tách lớp rõ |
| Design system | Tốt | Token nhất quán, spacing/radius/typography thống nhất |
| Cấu trúc thông tin | Tốt | 12 trang + breadcrumb + crumb sâu, nhóm nav rõ ràng |
| Bảng màu / tương phản | Tốt | Tự động kiểm (WCAG AA): 11/11 cặp màu đạt ≥4.5:1 (xem §4) |
| Bố cục responsive | Tốt | 4 breakpoints + sidebar overlay mobile + touch target ≥44px |
| Trải nghiệm AI flow | Tốt | Bank → kiểm định → duyệt → xuất XML chạy end-to-end trong mock |
| Accessibility | Tốt nhưng có lỗ | Skip link/ARIA/focus-management tốt; thiếu aria-current/aria-pressed; không có aria-current trên nav; h1/h2 không có h2 count rõ |
| Trạng thái loading | Trung bình | Skeleton chỉ ở `account`; bank/review/tasks không có skeleton |
| Độ thật của mock | Trung bình | Data hard-coded, không backend, không persistence — chỉ dành cho demo |

---

## 2. Khung giao diện (Shell)

```
<body>
├── <a class="skip">Bỏ qua điều hướng</a> …
├── <div id="app" class="shell" data-nav="open">
│   ├── <header class="topbar">  (navy #004b87, sticky)
│   │   ├── <button id="mt"> ≡ hamburger (44×44)
│   │   ├── <a class="logo-link"> QB · QBank Console (brand)
│   │   └── <div class="acct" id="acct-wrap"> (account menu dropdown)
│   │       Thông tin tài khoản / Cài đặt / Phím tắt / Đăng xuất
│   ├── <aside class="sidebar" id="side"> (NAV 9 items, nhóm 4)
│   ├── <div class="scrim">  (overlay mobile)
│   └── <main class="content">
│       └── <div class="page">
│           ├── <div id="crumb">  (breadcrumb, chỉ course page)
│           ├── <div class="page__head"> h1 + sub + #pact (actions top-right)
│           ├── <div id="view">   (render target)
│           └── <footer class="foot"> © NTTU
├── <div id="selbar" hidden>  (bulk action bar, fixed bottom)
├── <dialog id="dlg">          (native <dialog> modal)
└── <div id="toast" role="status" aria-live="polite">
```

**Layout semantics:**
- Grid shell: `grid-template-areas: "head" "side main"` (≥768px) / `"head" "main"` (mobile).
- Sidebar: 15.5rem (≥768px) / 17.5rem (≥1024px); sticky desktop, fixed overlay mobile (translateX -100% → 0 khi mở).
- Selbar: fixed bottom, max-width 74rem, dark text color, hiện khi `bank|course` + có selected.
- Toast: `role="status"` + `aria-live="polite"`, có thể kèm nút Undo — hiện khi `>`.

---

## 3. Design system

### Tokens (CSS custom properties, 30+ token)

| Nhóm | Tokens |
|---|---|
| Brand | `--primary:#004b87`, `--primary-hover:#003a69`, `--primary-soft:#e8f1fa` |
| Danger | `--danger-strong:#a51818`, `--danger-hover:#8a1414`, `--danger-soft:#fdeeee`, `--danger-border:#f3c3c3` |
| Success | `--success-strong:#166534`, `--success-soft:#e8f6ee`, `--success-border:#bfe5cc` |
| Warning | `--warning-soft:#fff8e1`, `--warning-border:#f2cf7a`, `--warning-strong:#9a5b00`, `--warning-badge-bg:#fdecb3` |
| Text/bg | `--text:#1b2a41`, `--text-muted:#5b6b82`, `--bg:#f8faff`, `--surface:#fff`, `--surface-hover:#f1f5fa` |
| Border | `--border:#e3ebf5`, `--border-strong:#8596ad` |
| Spacing | `--space-1:.5rem` … `--space-5:3rem` |
| Radius / shadow | `--radius:8px`, `--shadow-card:0 1px 2px rgba(19,82,157,.06)` |
| Typography scale | `--fs:1rem`, `--fs-sm:.875rem`, `--fs-xs:.8125rem`; `--font:"Be Vietnam Pro",Arial,sans-serif` |
| Layout | `--topbar-h:3.5rem` (3.75rem ≥768px), `--page-padding-x:1rem→2rem` |
| Other | `--tile:color-mix(in srgb,var(--primary) 3%,var(--surface))` |

### Tương phản WCAG (tự động tính toán bằng Python)

Tất cả cặp màu đạt WCAG AA (≥4.5:1) hoặc AA-large (≥3:1):

| Cặp | Tỷ lệ | WCAG |
|---|---|---|
| Primary `#004b87` on white | 8.91:1 | AA ✓ |
| Text `#1b2a41` on `#f8faff` | 13.83:1 | AA ✓ (AAA) |
| Text-muted `#5b6b82` on `#f8faff` | 5.20:1 | AA ✓ |
| Text-muted `#5b6b82` on `#fff` | 5.43:1 | AA ✓ |
| Success `#166534` on `#e8f6ee` | 6.40:1 | AA ✓ |
| Danger `#a51818` on `#fdeeee` | 6.79:1 | AA ✓ |
| Warning `#7a4a00` on `#fdecb3` | 6.35:1 | AA ✓ |
| White on `#003a69` (footer) | 11.61:1 | AAA ✓ |
| Footer text rgba(255,255,255,.78) on `#003a69` | 7.66:1 | AA ✓ |
| Primary on `#e8f1fa` | 7.81:1 | AA ✓ |
| Nav white on `#004b87` (active item) | 8.91:1 | AA ✓ |

### Typography

- Body: 16px / line-height 1.55 — thoải mái, dễ đọc
- `h1` (page title): 1.5rem (24px), `font-weight:700`
- Card `h3`: 1.0625rem (17px), card `h4`: `--fs-sm`
- Meta/small: `.8125rem–.875rem` (13–14px)
- `<kbd>` + `<mark>`: styled consistent
- Monospace cho codes (`ui-monospace, Consolas, monospace`): `.b.code`, code block `pre`

---

## 4. Các trang — chi tiết & kết quả runtime

12 trang + modal system. Mỗi trang được render và kiểm thử qua jsdom:

| # | Trang | Runtime | Ghi chú |
|---|---|---|---|
| 1 | Tổng quan `dash` | ✅ | 4 stat card + service status + log feed + CTA |
| 2 | Tất cả câu hỏi `bank` | ✅ | Filter bar + sort + chips + rows + selbar |
| 3 | Môn học `courses` | ✅ | Card grid + search + progress bar |
| 4 | Chi tiết môn học `course` | ✅ | Crumb + topics + question list |
| 5 | Tìm kiếm thông minh `search` | ✅ | NL search + 3-step transparency + highlight |
| 6 | Xem xét đề xuất AI `review` | ✅ | Filter chips + bulk + diff + JSON |
| 7 | Tác vụ AI `tasks` | ✅ | Table + status filter + retry |
| 8 | Moodle XML `moodle` | ✅ | Select chips + XML preview + validity badge |
| 9 | Quản trị hệ thống `admin` | ✅ | Directory search/filter + table + modal |
| 10 | Cài đặt LLM `llm` | ✅ | Form URL/token + inline validation + eye toggle |
| 11 | Thông tin tài khoản `account` | ✅ | Skeleton + reveal/hide ID + aria-busy |
| 12 | Cài đặt `settings` | ✅ | 2FA card + role preview + toggles |

**Modal dialog**: `showModal()` + `aria-labelledby` + focus return + Enter-to-primary + dirty-confirm.

### Luồng chính đã verify (end-to-end)

```
bank → chọn 11 câu → Kiểm định AI → 11 tác vụ pending → running → succeeded
     → 7 đề xuất được sinh (severity Cao/Trung bình)
     → 7 câu hỏi chuyển ok/flag
     → review page: 7 đề xuất + chip filter + bulk accept/reject
     → tasks page: 11 dòng trong table (T-0001…T-0011)
     → moodle page: xuất XML thật (<quiz> + category + multichoice + CDATA escaping)
     → XML hợp lệ (xác nhận bằng DOMParser)
```

---

## 5. Trải nghiệm tương tác & trạng thái

### Loading
- `account` page: skeleton (`.sk`) + `aria-busy="true"` trong 600ms
- AI task busy state: spinner + disable button trên q-row
- Loading state cho `bank`/`review`/`tasks`: **không có** — chúng đồng bộ ngay; acceptable cho demo nhưng không đủ cho production

### Feedback & error
- Toast (inline + action button) cho: tạo/sửa/xóa, lưu LLM, xuất, copy, undo
- Inline validation (`.fe` error + `aria-invalid="true"` + `role="alert"`) cho form (LLM, course, account, edit question)
- Empty states: mỗi page có `.empty-box` + CTA
- Task failed retry: button "Thử lại" trên nút tasks header
- Dirty-confirm dialog khi đóng khi đang nhập
- Keyboard shortcut `/` (search), `n` (new), `?` (help), `Esc` (close)

### Undo
- Xóa hàng loạt: toast + Undo → khôi phục question + suggestions
- Bulk accept/reject suggestions: toast + Undo

---

## 6. Accessibility

### Đã tốt

- Skip link → `#view` (+ target `#view` focus được)
- Semantic HTML: `<header>`, `<nav aria-label="Chức năng">`, `<main>`, `<footer>`, `<article>`, `<dl>`
- `<h1>` per page, `.page__title` focused after route change
- `:focus-visible` outline toàn bộ
- Toast aria-live="polite" + role="status"
- Inline form errors: role="alert" + aria-invalid
- Combobox / listbox: `role="combobox"` + `aria-expanded` + `aria-controls` + `aria-autocomplete="list"` + `role="listbox"` + `role="option"` + `aria-activedescendant` (Course combobox, keyboard hoạt động)
- Dialog: `<dialog>` native + showModal + close + aria-labelledby (set qua override `showModal()`) + Enter-to-primary + dirty-confirm
- ARIA-current trên account menu: `aria-expanded` synced
- `aria-label` trên checkbox/select/input/stat buttons; `aria-label` trên empty state buttons
- `prefers-reduced-motion`: disable tất cả transitions/animations
- `@media (hover: none)`: card actions visible mặc định (mobile)
- Touch targets ≥44px (`.topbar__toggle` 44×44, `.btn` 2.5rem+, `@media (pointer:coarse)` → 2.75rem)

### Còn thiếu / nên làm trước production

- **`aria-current` trên nav active** (`.nv.on` chỉ class — không phải `aria-current="page"`)
- **`aria-pressed` trên filter chips** (`.chip` là `<button>` chỉ class `.on`)
- **Heading hierarchy**: chỉ có 1×`h1` (page title) + `h3` card titles — không có `h2` label section
- **`aria-describedby`** trỏ về hint trên course combobox
- **`aria-live` trên pagination** ("Trang 1 / 5") và review filter counts — có `role="status"` trên directory count nhưng không trên bank pagination
- **Skip link đến sidebar nav** (hiện chỉ đến `#view` main)
- **`<mark>` highlight** trong search results — không có `aria-label` mô tả highlight

---

## 7. Responsive

| Range | Behaviour |
|---|---|
| `<640px` | 1-col grid, full-width CTA, logo sub-title hidden, `.udet` 1-col |
| `640–768px` | 2-col `.grid` stats, logo sub-title visible |
| `≥768px` | Sticky sidebar, 2-col `.cols`, 3-col `.igrid`, 4-col `.grid`, page padding 1.5rem, topbar height 3.75rem |
| `≥1024px` | Sidebar 17.5rem, page padding 2rem, dialog min 44rem, `.grid` 4-col |
| `≥1280px` | Dashboard 2-col `.dashcols`, sticky `.tools` (bank view), `.lm__f` 3-col |

### Sidebar behavior
- **Mobile <768px**: fixed overlay (top: topbar-h, height 100dvh), translateX(-100%), scrim rgba(27,42,65,.55), aria-expanded synced
- **Desktop ≥768px**: sticky, height calc(100dvh - topbar-h), không translate
- `#selbar` fixed bottom: mobile = 1-col stack, desktop = row (max 74rem)

### Touch
- `.btn`, `.btn.sm`, `.btn-rev`, select, inputs: `min-height:2.75rem` trên `@media (pointer:coarse)`

---

## 8. Dữ liệu mock

### State (toàn bộ in-memory, không persistence)

| Var | Loại | Ghi chú |
|---|---|---|
| `S.role` | admin \| gv | preview demo |
| `S.f` | bank filters `{q,c,tp,l,s,sort,page,hideAns}` | |
| `S.sel` / `S.rsel` | Set<id> | bulk selection + review selection |
| `S.sug` | array<{qid, tx, is[], diff, sev, tid}> | AI suggestions |
| `S.tasks` | array<{id, qid, st, ms, t0, msg}> | AI tasks (max 50) |
| `S.log` | array<[time, msg, type]> | activity log (max 50) |
| `S.users` | 5 tài khoản | 2 admin + 2 lecturer + 1 pending |
| `S.q` | 11 câu hỏi | level 0..2, status draft/ok/flag/stale |
| `S.lm` | 5 LLM configs | 3 demo + 2 empty |
| `S.tfa` | {st, until} | NOT_ENABLED / PENDING / ENABLED |
| `S.xml` | string | generated at export time |
| `S.ci` | int | current course index |
| `S.meReady` / `S.idShow` | bool | account page skeleton + ID visibility |

### Mock question data
- 11 câu, mỗi câu có `text` (chứa 1–2 typo cố ý như `ngử`/`dử liệu`), `o[4]` options, `a` correct index, `c` course idx, `tp` topic idx, `l` difficulty, `st` status, `updatedAt`, `by`
- Đặc biệt: q8 (id=8) có 2 đáp án giống hệt nhau (`DELETE`/`DELETE`) → sẽ trigger "Phương án nhiễu" issue. q7 đáp án đúng dài >1.6x đáp án khác → trigger "Logic" issue.

### Mock AI logic (`llm()`)
Phát hiện 4 loại issue:
1. **Chính tả**: typos map `{'ngử':'ngữ','dử liệu':'dữ liệu'}`
2. **Ngữ pháp**: text không kết thúc `?`/`.`/`:`
3. **Phương án nhiễu**: 2 phương án trùng
4. **Logic**: đáp án đúng dài >1.6x các đáp án khác

→ `{is[], tx, diff, sev}` (severity = Cao nếu Logic/Phương án nhiễu, else Trung bình).

### Mapping chuỗi enum → DB (comment trong file)
```javascript
LV_DB = [1,2,3];               // Dễ=1, Trung bình=2, Khó=3
ENUM_DB = {st: {draft:'not_checked', ok:'clean', flag:'has_issues', stale:'stale'},
            sev: {'Cao':'high', 'Trung bình':'medium'}}
```

---

## 9. Gaps & rủi ro

### A. Kỹ thuật / kiến trúc

1. **Không có build/router** — single-file; phải tái cấu trúc sang Vue Router
2. **In-memory state** — refresh = mất sạch dữ liệu, không có persistence
3. **XSS vector via innerHTML** — `esc()` escape `& < > "` nhưng KHÔNG escape single quote; nếu dùng `.html()` trong onclick attribute KHÔNG safe. Cần sanitizer khi port (DOMPurify)
4. **Không có loading/error state cho bank/review/tasks** — acceptable cho demo nhưng production cần skeleton
5. **Mock AI logic là heuristic** — server thật của đề án (Server 2 + Ollama/Qwen) không tham gia
6. **Không có persistence** — không lưu progress sau refresh
7. **Không có login/404** — chỉ dùng demo logout fake toast; nghiêm trọng cho demo production UX

### B. Mock data kém thật

1. **Pagination ảo** trên bank: description "Tất cả 11 câu hỏi" nhưng view chỉ hiển thị tối đa 20/page → chỉ 1 page
2. **Date hard-code** ("18/11 08:15", "T-0011") — half-baked realism
3. **Role preview** chỉ 2 chế độ (admin / gv) → demo không thể chạm sâu

### C. Accessibility (còn thiếu)

- `aria-current` trên nav active
- `aria-pressed` trên filter chips (state `.on` chỉ visual)
- Heading hierarchy (`h2` chưa có; chỉ `h1` + `h3`)
- `aria-describedby` cho hint trên combobox
- `aria-live` cho pagination count

### D. Security

1. **Không có auth** — single-user mock (chỉ 1 admin toấn lượt truy cập)
2. **Không có CSRF** — không có form submit thật
3. **Không có validation schema** — chỉ regex trong hàm `usave()`
4. **Không có rate-limit** — không upload/export thật (chỉ import dialog)
5. **Token LLM hiển thị bằng type=password** ✓ nhưng có eye-toggle (Hiện/Ẩn) — có thể lộ nếu được copy

### E. Code maintainability

- **1 file 1.749 dòng** — khô hạn; không testable; không reusable
- **Inline `onclick`** trong mọi template — xóa bằng cách chuyển sang Vue events
- **Không có TypeScript / types** — khi port nên dùng TS
- **No unit tests** — tất cả là visual/integration check

---

## 10. So sánh với `fe_web_admin` hiện tại (baseline)

| Tiêu chí | fe_web_admin hiện tại | UI_Accept_v3 |
|---|---|---|
| Stack | Vue 3 + Vite + Bootstrap + Vuetify | Single-file HTML |
| Router | ✓ 3 routes (`/login`, `/information-student`, `/404-not-found`) | ❌ single-page hash |
| Login | ✓ Có LoginPage + SilentTokenRefresher | ❌ fake toast |
| TOTP / 2FA | ✓ Có (CardSettingTwoFA + ModalActivate2FA) | ✓ Có (settings page, demo) |
| Service layer | ✓ AdminService / LoginService / EnrollmentService | ❌ Không xác thực backend |
| Design | Bootstrap + Vuetify generic | Custom design system (token + navy + spacing) |
| Accessibility | (có) | Tốt hơn từ góc nhìn shell + aria |
| Responsive | (Bootstrap grid) | 4 breakpoints rõ ràng + sidebar overlay |

**Đề xuất ghép**: Giữ `fe_web_admin` logic/services/ports cũ (Spring Boot logic giữ nguyên), thay thế shell + components + router bằng design từ `UI_Accept_v3.html`. Login/Information/PageNotFound pages hiện có nên giữ nguyên ở mức component (chỉ restyle).

---

## 11. Kết luận

1. **✅ UI đáp ứng tiêu chuẩn UI-UX tốt** ở mức design reference (tham chiếu) — visual hierarchy rõ, sticky topbar + sidebar + breadcrumb; typography thoải mái; color pass WCAG AA; responsive đầy đủ; accessibility rất tốt; AI flow chạy end-to-end.
2. ⚠️ **Không thể đánh giá pixel thật** trong sandbox — đề nghị mở file thật trong trình duyệt (Chrome/Edge) để xác nhận cuối.
3. ⚠️ **Không thể xem đây là production-ready**: không có persistence, không có auth thật, không có loading chung cho bank/review/tasks; pagination ảo.
4. ⚠️ **Phải refactor** trước khi ghép vào `fe_web_admin` (Vue Router + Pinia + TS components).
5. 🎯 Đề nghị làm **bản Vue mới** (`fe_web_admin/ui/`) từ file mock này, giữ nguyên logic Login/TOTP hiện có.

---

## Phụ lục

### A. Bản đồ trang → số dòng (`UI_Accept_v3.html`)
- Shell HTML: L480–L510
- CSS: L9–L20 (tokens), L480+ (component overrides)
- CSS responsive: L27, L89, L95, L101, L131, L135, L145, L181, L197, L217, L261, L276, L304, L308, L324, L329, L340, L345, L404, L416, L450
- Design system CSS: L175–L190
- `dash()` L1192 · `courses()` L1297 · `course()` L1310 · `search()` L1095 · `review()` L1326 · `tasks()` L1382 · `moodle()` L1398 · `admin()` L1418 · `llm()` L1417 · `settings()` L1427 · `account()` L1439 · `acctPage()` L1439
- `render()` L1700–L1732
- AI flow: `llm()` L752, `runAI()` L761
- Filters: `list()` L710
- Toast: L693
- Dialog: L894 (kbd), L1480 (edit confirm)
- Course CRUD: L1482+
- 2FA flow: L1460+

### B. Xác minh runtime (jsdom)
- Boot SPA không lỗi JavaScript
- 12 trang dispatch không lỗi
- 11 câu → AI → 7 suggestions → review page render 7 items + bulk + diff + JSON inspect
- Submit form LLM: empty (inline validation), invalid URL (inline validation), valid (toast)
- Account page skeleton + ID reveal + 30s auto-hide timer
- Moodle XML export: `<quiz>` + `<question type="category">` + category + multichoice + CDATA + XML valid badge
- Dialog `showModal()` + focus + close + Escape
- 0 lỗi trong toàn bộ flow test (jsdom + real setTimeout)
