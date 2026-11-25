<script setup>
import { computed, onMounted, ref, onBeforeUnmount } from 'vue'
import { TFA_VIEW, TFA_ICON } from '../data/account.js'
import { S, asUser, previewRole, log, toast, tfaOpen, tfaAsk, tfaDone, loadTfaStatus } from '../store.js'
import { auth } from '../auth/authStore.js'
import Ico from '../components/Ico.vue'
import { getPreference, setPreference, resolveTheme, watchSystemTheme, syncFromDom, THEME_OPTIONS } from '../theme.js'
import { LOCALES, locale, setLocale, t as tr } from '../i18n/index.js'

const st = computed(() => S.tfa.st), v = computed(() => TFA_VIEW[st.value])
const left = computed(() => { const t = Math.max(0, Math.ceil((S.tfa.until - S.now) / 1000)); return String(Math.floor(t / 60)).padStart(2, '0') + ':' + String(t % 60).padStart(2, '0') })
const gv = computed(() => (S.role === 'gv' && asUser() ? `Đang mô phỏng phạm vi môn của ${asUser().name}.` : ''))
// Chỉ chế độ demo mới có nút "mô phỏng ứng dụng đã xác nhận"; bản thật chờ Flutter xác nhận qua SSE/poll.
const demo = computed(() => auth.isDemo || S.dataMode === 'mock')
onMounted(loadTfaStatus)

/* ---------- Giao diện sáng/tối ----------
   `pref` là lựa chọn ĐÃ LƯU (có thể là 'auto'); `effective` là theme đang thực sự hiển thị, dùng
   cho câu mô tả dưới nhóm nút để người dùng biết "theo hệ thống" đang ra sáng hay tối. */
const pref = ref(syncFromDom() || getPreference())
const effective = ref(resolveTheme())
const unWatchSystem = watchSystemTheme((t) => { effective.value = t })
onBeforeUnmount(unWatchSystem)

const THEME_LABEL = { light: 'Sáng', dark: 'Tối', auto: 'Theo hệ thống' }
const THEME_HINT = {
  light: 'Nền sáng, phù hợp phòng làm việc nhiều ánh sáng.',
  dark: 'Nền tối, giảm chói khi làm việc lâu hoặc ban đêm.',
  auto: 'Tự đổi theo cài đặt sáng/tối của hệ điều hành.',
}
function pickTheme(v) {
  if (!THEME_OPTIONS.includes(v)) return
  pref.value = setPreference(v)     // ghi nhớ + áp ngay, không cần tải lại
  effective.value = resolveTheme(v)
  log(`Đổi giao diện sang ${THEME_LABEL[v].toLowerCase()}`, 'info')
  toast('Đã lưu giao diện', 'ok')
}
const themeOptions = THEME_OPTIONS.map((v) => ({ v, label: THEME_LABEL[v], hint: THEME_HINT[v] }))

/* ---------- Ngôn ngữ ----------
   Dùng đúng cơ chế của theme: chọn xong áp ngay và ghi nhớ, không cần tải lại trang.
   `locale` là reactive nên mọi $t() trong toàn ứng dụng tự cập nhật. */
function pickLocale(code) {
  if (setLocale(code) !== code) return
  log(`Đổi ngôn ngữ sang ${LOCALES.find((l) => l.code === code)?.label || code}`, 'info')
  toast(tr('settings.languageSaved'), 'ok')
}
const locales = LOCALES
const opts = [
  ['notifyTasks', 'Thông báo khi tác vụ AI hoàn tất', 'Hiện thông báo khi một yêu cầu kiểm định kết thúc (lỗi luôn được báo).', 'Đổi tùy chọn thông báo'],
  ['confirmDelete', 'Hỏi xác nhận trước khi xóa câu hỏi', 'Tắt đi thì câu hỏi bị xóa ngay và vẫn có nút Hoàn tác. Xóa nhiều câu cùng lúc luôn hỏi xác nhận.', 'Đổi tùy chọn xác nhận xóa'],
]
function setOpt(k, on, msg) { S[k] = on; log(msg, 'info'); toast('Đã lưu tùy chọn', 'ok') }
</script>

<template>
  <div class="card set">
    <section class="set__row" aria-labelledby="tfa-t">
      <div class="set__intro"><h3 id="tfa-t" class="set__t">{{ $t('auth.otpTitle') }}</h3><p class="set__d">{{ $t('tfa.desc') }}</p></div>
      <div class="set__body">
        <div class="set__tfa">
          <div class="tfa" :class="'tfa--' + v.cls">
            <div class="tfa__main">
              <span class="tfa__ic"><Ico :d="TFA_ICON[st]" /></span>
              <div class="tfa__txt">
                <p class="tfa__st" role="status"><span>{{ $t('tfa.status') }}</span><span class="ubd" :class="'ubd--' + v.bd">{{ v.text }}</span></p>
                <p class="tfa__desc">{{ v.desc }}</p>
                <p v-if="st === 'PENDING'" class="tfa__hint">{{ $t('tfa.countdownLeft') }} <b id="tfa-cd">{{ left }}</b> {{ $t('tfa.countdownRest') }}</p>
              </div>
            </div>
            <button v-if="st === 'NOT_ENABLED'" type="button" class="btn pri" @click="tfaOpen">{{ $t('tfa.enable') }}</button>
            <button v-else-if="st === 'PENDING'" type="button" class="btn" @click="tfaAsk">{{ $t('dialog.cancelAndReset') }}</button>
          </div>
          <p v-if="st === 'PENDING' && demo" class="mut" style="margin:0;font-size:var(--fs-sm)">{{ $t('brand.demo') }} <button type="button" class="btn sm" @click="tfaDone">{{ $t('tfa.simulate') }}</button></p>
          <p v-else-if="st === 'PENDING'" class="mut" style="margin:0;font-size:var(--fs-sm)">{{ $t('tfa.waiting') }}</p>
        </div>
      </div>
    </section>
    <section class="set__row" aria-labelledby="role-t">
      <div class="set__intro"><h3 id="role-t" class="set__t">{{ $t('admin.previewRole') }}</h3><p class="set__d">{{ $t('admin.previewRoleHint') }}</p></div>
      <div class="set__body">
        <label for="preview-role">{{ $t('admin.previewRoleLabel') }}</label>
        <select id="preview-role" :value="S.role" @change="previewRole($event.target.value)"><option value="admin">{{ $t('admin.roleAdmin') }}</option><option value="gv">{{ $t('admin.roleLecturer') }}</option></select>
        <small v-if="gv">{{ gv }}</small>
      </div>
    </section>
    <section class="set__row" aria-labelledby="theme-t">
      <div class="set__intro">
        <h3 id="theme-t" class="set__t">{{ $t('settings.appearance') }}</h3>
        <p class="set__d">{{ $t('settings.appearanceDesc') }}</p>
      </div>
      <div class="set__body">
        <!-- radio thật (ẩn thị giác, vẫn dùng được bằng bàn phím) + nhãn là thẻ trực quan.
             Dùng radio thay vì nút: đây là chọn MỘT trong ba, và trình đọc màn hình đọc đúng
             "1 trên 3" kèm trạng thái chọn. -->
        <fieldset class="theme-pick">
          <legend class="sr-only">{{ $t('settings.selectTheme') }}</legend>
          <label v-for="o in themeOptions" :key="o.v" class="theme-opt" :class="{ on: pref === o.v }">
            <input type="radio" name="theme" :value="o.v" :checked="pref === o.v" @change="pickTheme(o.v)">
            <span class="theme-opt__swatch" :class="'theme-opt__swatch--' + o.v" aria-hidden="true"></span>
            <span class="theme-opt__txt"><b>{{ o.label }}</b><small>{{ o.hint }}</small></span>
          </label>
        </fieldset>
        <p class="theme-now">
          {{ $t('settings.currentlyShowing') }} <b>{{ THEME_LABEL[effective] }}</b>
          <template v-if="pref === 'auto'"> {{ $t('settings.byOs') }}</template>
        </p>
      </div>
    </section>
    <section class="set__row" aria-labelledby="lang-t">
      <div class="set__intro">
        <h3 id="lang-t" class="set__t">{{ $t('settings.language') }}</h3>
        <p class="set__d">{{ $t('settings.languageDesc') }}</p>
      </div>
      <div class="set__body">
        <!-- Nút chọn thay vì <select>: chỉ 5 lựa chọn, hiện được tên ngôn ngữ bằng chính
             ngôn ngữ đó cùng mã 2 ký tự -> người dùng nhận ra ngay cả khi không đọc được
             ngôn ngữ đang hiển thị. -->
        <div class="lang-pick" role="radiogroup" :aria-label="$t('settings.language')">
          <label v-for="l in locales" :key="l.code" class="lang-opt" :class="{ on: locale.code === l.code }">
            <input type="radio" name="lang" :value="l.code" :checked="locale.code === l.code" @change="pickLocale(l.code)">
            <span class="lang-opt__code" aria-hidden="true">{{ l.short }}</span>
            <span class="lang-opt__txt"><b>{{ l.label }}</b><small>{{ l.flag }}</small></span>
          </label>
        </div>
      </div>
    </section>
    <section class="set__row" aria-labelledby="opt-t">
      <div class="set__intro"><h3 id="opt-t" class="set__t">{{ $t('settings.notify') }}</h3><p class="set__d">{{ $t('settings.notifyDesc') }}</p></div>
      <div class="set__body">
        <label v-for="[k, t, d, msg] in opts" :key="k" class="sopt">
          <span class="sopt__t"><b>{{ t }}</b><small>{{ d }}</small></span>
          <span class="sw"><input type="checkbox" role="switch" :checked="S[k]" @change="setOpt(k, $event.target.checked, msg)"><span class="sw__ui" aria-hidden="true"></span></span>
        </label>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* Bộ chọn giao diện: 3 thẻ ngang, mỗi thẻ có ô xem trước màu. Ô xem trước vẽ bằng CHÍNH các biến
   của theme nên luôn khớp giao diện thật; riêng thẻ "Sáng"/"Tối" cố ý dùng màu cứng để người
   dùng thấy trước kết quả kể cả khi đang ở theme ngược lại. */
.theme-pick{display:grid;grid-template-columns:repeat(auto-fit,minmax(11rem,1fr));gap:.65rem;margin:0;padding:0;border:0}
.theme-opt{position:relative;display:flex;align-items:center;gap:.75rem;padding:.7rem .8rem;border:1px solid var(--border-strong);border-radius:var(--radius);cursor:pointer;transition:border-color .15s,background-color .15s}
.theme-opt:hover{background:var(--surface-hover)}
/* Dấu "đang chọn" phải LUÔN hiện: dùng vòng 1px đặc cùng màu viền (tổng 2px) thay vì color-mix
   trong suốt — trình duyệt cũ không hỗ trợ color-mix sẽ bỏ cả khai báo và mất dấu hiệu chọn. */
.theme-opt.on{border-color:var(--primary-ink);box-shadow:0 0 0 1px var(--primary-ink)}
.theme-opt input{position:absolute;opacity:0;width:1px;height:1px;margin:0}
/* focus bằng bàn phím: viền quanh cả thẻ, không chỉ quanh input đã ẩn */
.theme-opt:has(input:focus-visible){outline:2px solid var(--primary-ink);outline-offset:2px}

.theme-opt__swatch{flex:none;width:2.5rem;height:2rem;border-radius:6px;border:1px solid var(--border-strong);position:relative;overflow:hidden}
/* "Sáng": nền trắng + thanh xanh phía trên, mô phỏng topbar */
.theme-opt__swatch--light{background:#fff}
.theme-opt__swatch--light::before{content:"";position:absolute;inset:0 0 auto;height:33%;background:#004b87}
/* "Tối": nền navy + thanh sáng hơn */
.theme-opt__swatch--dark{background:#0b1220}
.theme-opt__swatch--dark::before{content:"";position:absolute;inset:0 0 auto;height:33%;background:#175f9f}
/* "Theo hệ thống": nửa sáng nửa tối, thể hiện ý "cả hai" */
.theme-opt__swatch--auto{background:linear-gradient(100deg,#fff 0 50%,#0b1220 50% 100%)}
.theme-opt__swatch--auto::before{content:"";position:absolute;inset:0 0 auto;height:33%;background:linear-gradient(100deg,#004b87 0 50%,#175f9f 50% 100%)}

/* Bộ chọn ngôn ngữ: 5 thẻ, mỗi thẻ có mã 2 ký tự + tên ngôn ngữ viết bằng chính nó.
   Dùng chung ngôn ngữ thị giác với .theme-opt để hai mục cài đặt trông cùng một hệ. */
.lang-pick{display:grid;grid-template-columns:repeat(auto-fit,minmax(9.5rem,1fr));gap:.6rem}
.lang-opt{position:relative;display:flex;align-items:center;gap:.6rem;padding:.6rem .7rem;border:1px solid var(--border-strong);border-radius:var(--radius);cursor:pointer;transition:border-color .15s,background-color .15s}
.lang-opt:hover{background:var(--surface-hover)}
.lang-opt.on{border-color:var(--primary-ink);box-shadow:0 0 0 1px var(--primary-ink)}
.lang-opt input{position:absolute;opacity:0;width:1px;height:1px;margin:0}
.lang-opt:has(input:focus-visible){outline:2px solid var(--primary-ink);outline-offset:2px}
.lang-opt__code{flex:none;display:grid;place-items:center;width:2.1rem;height:2.1rem;border-radius:6px;background:var(--primary-fill-soft,var(--surface-sunken));color:var(--primary-ink);font-size:var(--fs-xs);font-weight:700;letter-spacing:.02em}
.lang-opt.on .lang-opt__code{background:var(--primary-fill-hover);color:var(--on-primary)}
.lang-opt__txt{display:grid;gap:.1rem;min-width:0}
.lang-opt__txt b{font-size:var(--fs-sm);color:var(--text);font-weight:600}
.lang-opt__txt small{font-size:var(--fs-xs);color:var(--text-muted);line-height:1}

.theme-opt__txt{display:grid;gap:.1rem;min-width:0}
.theme-opt__txt b{font-size:var(--fs-sm);color:var(--text)}
.theme-opt__txt small{font-size:var(--fs-xs);color:var(--text-muted);line-height:1.35}
.theme-now{margin:.75rem 0 0;font-size:var(--fs-sm);color:var(--text-muted)}
.theme-now b{color:var(--text)}
</style>
