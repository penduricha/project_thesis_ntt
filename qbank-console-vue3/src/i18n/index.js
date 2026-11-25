/**
 * Hệ thống đa ngôn ngữ — KHÔNG dùng thư viện i18n của framework.
 *
 * Vì sao tự viết thay vì dùng vue-i18n:
 *   1. Đề bài yêu cầu rõ "KHÔNG DÙNG THƯ VIỆN DỊCH THUẬT TRONG FRAMEWORK".
 *   2. Nhu cầu ở đây rất hẹp: tra một chuỗi theo khoá, vài tham số nội suy.
 *      vue-i18n nặng ~20KB và có cả trăm tính năng không dùng tới.
 *   3. Tự viết thì hiểu và giải thích được toàn bộ khi bảo vệ đồ án.
 *
 * THIẾT KẾ
 *   - `t(key, params)` : tra theo khoá, hỗ trợ nội suy {name}.
 *   - Khoá dạng dấu chấm: 'nav.dashboard', 'common.save'.
 *   - Thiếu bản dịch -> rơi về tiếng Việt (ngôn ngữ gốc), KHÔNG hiện khoá thô.
 *     Lý do: hiện 'nav.dashboard' cho người dùng là lỗi tệ hơn hiện tiếng Việt.
 *   - Ngôn ngữ lưu trong localStorage, giống cách theme đang làm.
 */

import { reactive, computed } from 'vue'
import vi from './locales/vi.js'
import en from './locales/en.js'
import zh from './locales/zh.js'
import ko from './locales/ko.js'
import ja from './locales/ja.js'
import es from './locales/es.js'

const KEY = 'qbank-locale'
const FALLBACK = 'vi'

/**
 * Các ngôn ngữ có sẵn trong giao diện.
 * `label` là tên ngôn ngữ viết BẰNG CHÍNH ngôn ngữ đó (endonym) — đây là quy
 * ước quốc tế: người Nhật tìm "日本語" dễ hơn tìm "Japanese" trong danh sách.
 */
export const LOCALES = [
  { code: 'vi', label: 'Tiếng Việt', short: 'VI', flag: '🇻🇳' },
  { code: 'en', label: 'English', short: 'EN', flag: '🇬🇧' },
  { code: 'zh', label: '简体中文', short: 'ZH', flag: '🇨🇳' },
  { code: 'ko', label: '한국어', short: 'KO', flag: '🇰🇷' },
  { code: 'ja', label: '日本語', short: 'JA', flag: '🇯🇵' },
  { code: 'es-ES', label: 'Español (España)', short: 'ES', flag: '🇪🇸' },
]

const MESSAGES = { vi, en, zh, ko, ja, 'es-ES': es }

function readSaved() {
  try {
    const v = localStorage.getItem(KEY)
    if (MESSAGES[v]) return v
  } catch {
    // localStorage bị chặn (chế độ riêng tư) -> dùng mặc định
  }
  return FALLBACK
}

/** Ngôn ngữ đang dùng, phản ứng với giao diện. */
export const locale = reactive({ code: readSaved() })

/** Đổi ngôn ngữ: áp ngay + ghi nhớ. */
export function setLocale(code) {
  if (!MESSAGES[code]) return locale.code
  locale.code = code
  try {
    localStorage.setItem(KEY, code)
  } catch {
    // Không lưu được thì vẫn áp cho phiên hiện tại.
  }
  // Cập nhật thuộc tính lang của <html>: cần cho trình đọc màn hình phát âm
  // đúng, và cho trình duyệt chọn font phù hợp (chữ Hàn/Nhật/Trung).
  if (typeof document !== 'undefined') {
    document.documentElement.setAttribute('lang', code === 'zh' ? 'zh-CN' : code)
  }
  return locale.code
}

/** Lấy bảng chuỗi của ngôn ngữ hiện tại. */
function table() {
  return MESSAGES[locale.code] || MESSAGES[FALLBACK]
}

/**
 * Nội suy tham số: t('greeting', { name: 'An' }) với chuỗi 'Chào {name}'.
 * Tham số không truyền thì giữ nguyên {name} — giúp phát hiện lỗi khi dev.
 */
function interpolate(str, params) {
  if (!params) return str
  return str.replace(/\{(\w+)\}/g, (whole, name) =>
    Object.prototype.hasOwnProperty.call(params, name) ? String(params[name]) : whole)
}

/**
 * Dịch một khoá.
 *
 * Thứ tự tra:
 *   1. Ngôn ngữ đang chọn
 *   2. Tiếng Việt (ngôn ngữ gốc)
 *   3. Chính khoá đó (chỉ khi bản dịch bị thiếu ở cả hai nơi — dấu hiệu lỗi
 *      lập trình, không phải trạng thái bình thường)
 */
export function t(key, params) {
  const cur = table()[key]
  if (cur != null) return interpolate(cur, params)
  const fb = MESSAGES[FALLBACK][key]
  if (fb != null) return interpolate(fb, params)
  // Không có bản dịch ở đâu cả: trả khoá để lộ vấn đề ngay khi phát triển,
  // thay vì im lặng trả chuỗi rỗng.
  if (import.meta.env?.DEV) console.warn('[i18n] thiếu chuỗi:', key)
  return key
}

/** Bản dịch dạng computed — dùng khi cần truyền vào chỗ yêu cầu ref. */
export const tr = (key, params) => computed(() => t(key, params))

/** Thông tin ngôn ngữ đang chọn. */
export const currentLocale = computed(() => LOCALES.find((l) => l.code === locale.code) || LOCALES[0])

/**
 * Đăng ký `t` làm thuộc tính toàn cục để template dùng `$t('...')`.
 * Nhờ vậy không phải import `t` vào 31 file .vue.
 */
export const i18n = {
  install(app) {
    app.config.globalProperties.$t = t
    app.config.globalProperties.$locale = locale
    // Đặt lang lúc khởi động, không chỉ khi đổi ngôn ngữ.
    if (typeof document !== 'undefined') {
      document.documentElement.setAttribute('lang', locale.code === 'zh' ? 'zh-CN' : locale.code)
    }
  },
}

/** Kiểm tra thiếu chuỗi — dùng trong script kiểm thử, không chạy lúc thật. */
export function missingKeys() {
  const all = Object.keys(MESSAGES[FALLBACK])
  const report = {}
  for (const code of Object.keys(MESSAGES)) {
    const missing = all.filter((k) => MESSAGES[code][k] == null)
    if (missing.length) report[code] = missing
  }
  return report
}
