/**
 * Quản lý giao diện sáng/tối.
 *
 * Ba lựa chọn: 'light' | 'dark' | 'auto' (theo hệ điều hành).
 *
 * Vì sao cần module riêng thay vì chỉ dùng @media (prefers-color-scheme):
 *   1. Người dùng phải CHỌN ĐƯỢC. Nếu chỉ dựa vào media query thì ai để máy ở chế độ tối cũng
 *      buộc phải dùng bản tối, không tắt được.
 *   2. Lựa chọn phải NHỚ giữa các lần mở. Không nhớ thì mỗi lần F5 theme nhảy về mặc định.
 *   3. Phải CHỐNG NHÁY (FOUC): script trong index.html chạy trước khi vẽ nên đọc localStorage và
 *      đặt data-theme ngay; module này chỉ đồng bộ lại sau khi Vue khởi động.
 *
 * Ghi vào <html data-theme> chứ không phải <body>: biến CSS nằm ở :root, và html cần biết theme
 * để đặt `background` cho thanh cuộn / vùng overscroll.
 */

const KEY = 'qbank-theme'
const OPTIONS = ['light', 'dark', 'auto']
const media = typeof matchMedia === 'function' ? matchMedia('(prefers-color-scheme: dark)') : null

/* Lựa chọn đang có hiệu lực, giữ trong bộ nhớ. KHÔNG đọc lại localStorage mỗi lần cần: khi bộ nhớ
   trình duyệt bị chặn (chế độ riêng tư) thì localStorage luôn rỗng, đọc lại sẽ tưởng là 'auto' và
   ghi đè lựa chọn người dùng vừa bấm mỗi khi hệ điều hành đổi sáng/tối. */
let current = 'auto'

/** Giá trị người dùng đã chọn (có thể là 'auto'). */
export function getPreference() {
  try {
    const saved = localStorage.getItem(KEY)
    if (OPTIONS.includes(saved)) { current = saved; return saved }
  } catch {
    // localStorage bị chặn (chế độ riêng tư của một số trình duyệt): dùng bộ nhớ trong module.
  }
  return current
}

/** Theme đang thực sự hiển thị sau khi đã giải 'auto' theo hệ điều hành. */
export function resolveTheme(pref = getPreference()) {
  if (pref === 'auto') return media && media.matches ? 'dark' : 'light'
  return pref
}

/**
 * Áp theme. `pref` là lựa chọn của người dùng; giá trị ghi lên DOM là lựa chọn đó (kể cả 'auto'),
 * để khối CSS `:root[data-theme="auto"]` + media query tự xử lý phần còn lại — nhờ vậy khi hệ
 * điều hành đổi sáng/tối, giao diện đổi theo mà không cần chạy lại JS.
 */
export function applyTheme(pref = getPreference()) {
  const value = OPTIONS.includes(pref) ? pref : 'auto'
  current = value
  document.documentElement.setAttribute('data-theme', value)
  // Trình duyệt dùng `color-scheme` để tô thanh cuộn, ô nhập, autofill… cho khớp theme.
  document.documentElement.style.colorScheme = resolveTheme(value)
  return value
}

/** Người dùng chọn theme mới: ghi nhớ + áp ngay. */
export function setPreference(pref) {
  const value = OPTIONS.includes(pref) ? pref : 'auto'
  try {
    localStorage.setItem(KEY, value)
  } catch {
    // Không lưu được thì vẫn áp cho phiên hiện tại.
  }
  return applyTheme(value)
}

/**
 * Theo dõi thay đổi theme của hệ điều hành — chỉ có tác dụng khi đang ở 'auto'.
 * Trả về hàm huỷ để gọi trong onBeforeUnmount (tránh rò rỉ listener).
 */
export function watchSystemTheme(onChange) {
  if (!media) return () => {}
  const handler = () => {
    if (getPreference() === 'auto') {   // getPreference() cập nhật `current` từ localStorage nếu đọc được
      applyTheme('auto')
      if (onChange) onChange(resolveTheme('auto'))
    }
  }
  // addEventListener('change') được hỗ trợ ở mọi trình duyệt hiện đại; addListener là API cũ.
  if (media.addEventListener) {
    media.addEventListener('change', handler)
    return () => media.removeEventListener('change', handler)
  }
  media.addListener(handler)
  return () => media.removeListener(handler)
}

export const THEME_OPTIONS = OPTIONS

/* Đồng bộ lựa chọn trong bộ nhớ với giá trị script chống nháy đã đặt trên <html> — nhờ vậy
   watchSystemTheme biết đúng người dùng đang chọn gì ngay từ đầu. */
export function syncFromDom() {
  const v = document.documentElement.getAttribute('data-theme')
  if (OPTIONS.includes(v)) current = v
  return current
}
