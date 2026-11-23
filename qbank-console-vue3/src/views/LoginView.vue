<script setup>
// Form đăng nhập ghép từ fe_web_admin/pages/login-page/LoginPage.vue,
// viết lại bằng <script setup> + CSS của QBank Console (không dùng Bootstrap/Vuetify/SCSS của fe).
// Logic giữ nguyên: validate từng ô, TOTP tùy chọn (server quyết định bắt buộc hay không),
// khóa form khi LOCKED (đếm ngược + lưu localStorage), timeout 20s, remember-me chỉ lưu adminId.
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '../auth/authStore.js'
import { REQUEST_STATUS } from '../auth/AdminRepository.js'
import AdminStorage from '../auth/AdminStorage.js'
import { DEMO_MODE } from '../config.js'

const MESSAGE = {
  ADMIN_ID_REQUIRED: 'Vui lòng nhập mã quản trị viên.',
  PASSWORD_REQUIRED: 'Vui lòng nhập mật khẩu.',
  WRONG_CREDENTIALS: 'Mã quản trị viên hoặc mật khẩu không đúng. Vui lòng kiểm tra lại.',
  SERVER_ERROR: 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra mạng rồi thử lại.',
  TOTP_REQUIRED: 'Tài khoản đã bật xác thực 2 lớp. Nhập mã 6 số đang hiển thị trong ứng dụng Authenticator.',
  TOTP_FORMAT: 'Mã xác thực gồm đúng 6 chữ số.',
  TOTP_INVALID: 'Mã xác thực không đúng hoặc đã hết hạn. Hãy nhập mã mới đang hiển thị trên ứng dụng.',
  LOCKED: 'Nhập sai quá nhiều lần. Biểu mẫu tạm khóa, sẽ tự mở lại khi hết thời gian đếm ngược.',
}
const LOGIN_TIMEOUT_MS = 20000
const LOCK_DEFAULT_SECONDS = 5 * 60
const LOCK_STORAGE_KEY = 'login.totpLock'

const router = useRouter()
const adminId = ref('')
const password = ref('')
const totp = ref('')
const rememberMe = ref(false)
const showPassword = ref(false)
const isSubmitting = ref(false)
const isInitializing = ref(true)
const errors = ref({ adminId: '', password: '', totp: '' })
const validateLogin = ref('')
const loginFailed = ref(false)
const lockUntil = ref(0)
const lockRemainingSeconds = ref(0)
let lockTimer = null

const isLocked = computed(() => lockRemainingSeconds.value > 0)
const isFormDisabled = computed(() => isInitializing.value || isSubmitting.value || isLocked.value)
const lockCountdownLabel = computed(() => {
  const t = Math.max(0, lockRemainingSeconds.value)
  return `${String(Math.floor(t / 60)).padStart(2, '0')}:${String(t % 60).padStart(2, '0')}`
})
const isCredentialError = computed(() => loginFailed.value && !!validateLogin.value)
const invalidAdminId = computed(() => !!errors.value.adminId || isCredentialError.value)
const invalidPassword = computed(() => !!errors.value.password || isCredentialError.value)

const boxAdminId = ref(null), boxPassword = ref(null), boxTotp = ref(null)
const inputAdminId = ref(null), inputPassword = ref(null), inputTotp = ref(null)

async function focusField(field) {
  await new Promise((r) => requestAnimationFrame(r))
  ;({ adminId: inputAdminId.value, password: inputPassword.value, totp: inputTotp.value })[field]?.focus()
}
function shake(el) {
  if (!el || typeof el.animate !== 'function') return
  if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return
  el.animate(
    [{ transform: 'translateX(0)' }, { transform: 'translateX(-6px)' }, { transform: 'translateX(6px)' }, { transform: 'translateX(-4px)' }, { transform: 'translateX(4px)' }, { transform: 'translateX(0)' }],
    { duration: 400, easing: 'ease-in-out' },
  )
}
function clearFieldError(field) {
  errors.value[field] = ''
  loginFailed.value = false
  validateLogin.value = ''
}
function setInputAdminId(e) {
  adminId.value = e.target.value.replace(/[^0-9]/g, '')
  clearFieldError('adminId')
}
function setInputTotp(e) {
  totp.value = e.target.value.replace(/[^0-9]/g, '').slice(0, 6)
  errors.value.totp = ''
  validateLogin.value = ''
}
function togglePassword() {
  showPassword.value = !showPassword.value
  focusField('password')
}
function validateForm() {
  const id = String(adminId.value ?? '').trim()
  errors.value.adminId = id ? '' : MESSAGE.ADMIN_ID_REQUIRED
  errors.value.password = password.value ? '' : MESSAGE.PASSWORD_REQUIRED
  if (errors.value.adminId) { shake(boxAdminId.value); focusField('adminId'); return false }
  if (errors.value.password) { shake(boxPassword.value); focusField('password'); return false }
  const code = String(totp.value ?? '').trim()
  if (code !== '' && !/^\d{6}$/.test(code)) {
    errors.value.totp = MESSAGE.TOTP_FORMAT
    shake(boxTotp.value); focusField('totp'); return false
  }
  return true
}
function showWrongCredentials() {
  loginFailed.value = true
  validateLogin.value = MESSAGE.WRONG_CREDENTIALS
  password.value = ''
  showPassword.value = false
  shake(boxAdminId.value); shake(boxPassword.value)
}
function withTimeout(promise, ms) {
  let timer
  const timeout = new Promise((_, reject) => { timer = setTimeout(() => reject(new Error('Login request timeout')), ms) })
  return Promise.race([promise, timeout]).finally(() => clearTimeout(timer))
}
// --- Khóa form khi LOCKED (giữ nguyên fe_web_admin) ---
function persistLock() {
  try { localStorage.setItem(LOCK_STORAGE_KEY, JSON.stringify({ adminId: adminId.value ? String(adminId.value) : null, until: lockUntil.value })) } catch { /* bỏ qua */ }
}
function clearPersistedLock() {
  try { localStorage.removeItem(LOCK_STORAGE_KEY) } catch { /* bỏ qua */ }
}
function stopLockTimer() { if (lockTimer) { clearInterval(lockTimer); lockTimer = null } }
function onLockTick() {
  const remaining = Math.ceil((lockUntil.value - Date.now()) / 1000)
  if (remaining <= 0) releaseLock()
  else lockRemainingSeconds.value = remaining
}
function runLockTimer() { stopLockTimer(); lockTimer = setInterval(onLockTick, 1000) }
function startLock(seconds) {
  const total = Math.max(1, Math.ceil(Number(seconds) || LOCK_DEFAULT_SECONDS))
  lockUntil.value = Date.now() + total * 1000
  lockRemainingSeconds.value = total
  persistLock(); runLockTimer()
}
function releaseLock() {
  stopLockTimer()
  lockUntil.value = 0; lockRemainingSeconds.value = 0
  clearPersistedLock()
  validateLogin.value = ''; errors.value.totp = ''; totp.value = ''
  focusField('totp')
}
function restoreLock() {
  try {
    const raw = localStorage.getItem(LOCK_STORAGE_KEY)
    if (!raw) return
    const saved = JSON.parse(raw)
    const remaining = Math.ceil((Number(saved?.until) || 0) - Date.now()) / 1000
    if (remaining > 0) {
      if (!adminId.value && saved.adminId) adminId.value = saved.adminId
      lockUntil.value = Number(saved.until)
      lockRemainingSeconds.value = Math.ceil(remaining)
      runLockTimer()
    } else clearPersistedLock()
  } catch { clearPersistedLock() }
}
function onLockVisibilityChange() {
  if (document.visibilityState === 'visible' && lockUntil.value > 0) onLockTick()
}

async function handleLoginEvent() {
  if (isFormDisabled.value) return
  errors.value = { adminId: '', password: '', totp: '' }
  loginFailed.value = false
  validateLogin.value = ''
  if (!validateForm()) return

  isSubmitting.value = true
  let fieldToFocus = null
  try {
    const result = await withTimeout(
      login(adminId.value, password.value, String(totp.value ?? '').trim() || null),
      LOGIN_TIMEOUT_MS,
    )
    switch (result.getStatus()) {
      case REQUEST_STATUS.OK:
        if (rememberMe.value) new AdminStorage().saveLocalStorageRememberMe(adminId.value)
        else new AdminStorage().removeLocalStorageRememberMe()
        await router.push('/dash')
        return
      case REQUEST_STATUS.TOTP_REQUIRED:
        errors.value.totp = MESSAGE.TOTP_REQUIRED
        shake(boxTotp.value); fieldToFocus = 'totp'
        break
      case REQUEST_STATUS.TOTP_INVALID:
        errors.value.totp = MESSAGE.TOTP_INVALID
        totp.value = ''
        shake(boxTotp.value); fieldToFocus = 'totp'
        break
      case REQUEST_STATUS.LOCKED:
        totp.value = ''; errors.value.totp = ''
        validateLogin.value = ''
        startLock(result.getRetryAfterSeconds())
        break
      case REQUEST_STATUS.FAILED:
        showWrongCredentials(); fieldToFocus = 'password'
        break
      default:
        validateLogin.value = MESSAGE.SERVER_ERROR
    }
  } catch (e) {
    console.error('Login error:', e)
    validateLogin.value = MESSAGE.SERVER_ERROR
  } finally {
    isSubmitting.value = false
  }
  if (fieldToFocus) focusField(fieldToFocus)
}

onMounted(() => {
  document.title = 'Đăng nhập · QBank Console'
  document.addEventListener('visibilitychange', onLockVisibilityChange)
  const saved = new AdminStorage().getLocalStorageRememberMe()
  if (saved?.adminId) { rememberMe.value = true; adminId.value = String(saved.adminId) }
  restoreLock()
  isInitializing.value = false
  const isDesktopLike = window.matchMedia?.('(hover: hover) and (pointer: fine)').matches
  if (isDesktopLike && !isLocked.value) focusField(adminId.value ? 'password' : 'adminId')
})
onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', onLockVisibilityChange)
  stopLockTimer()
})
</script>

<template>
  <div class="login-page">
    <div class="login-top">
      <span class="logo-badge">QB</span>
      <span class="logo-text"><strong>QBank Console</strong><span>NTTU · Kiểm định LLM</span></span>
    </div>

    <form class="card login-card" novalidate :aria-busy="isSubmitting" @submit.prevent="handleLoginEvent">
      <header class="login-heading">
        <h1>Đăng nhập</h1>
        <p class="mut">Dành cho quản trị viên hệ thống</p>
      </header>

      <div v-if="DEMO_MODE" class="login-banner login-banner--demo" role="status">
        <b>Chế độ demo (VITE_DEMO_MODE=true):</b> đăng nhập bằng bất kỳ mã/mật khẩu nào, dùng dữ liệu mẫu. Production phải tắt.
      </div>

      <div v-if="validateLogin" class="login-banner login-banner--error" role="alert">
        <span aria-hidden="true">!</span><p>{{ validateLogin }}</p>
      </div>
      <div v-if="isLocked" class="login-banner login-banner--locked" role="alert">
        <span aria-hidden="true">!</span><p>{{ MESSAGE.LOCKED }}</p><strong>{{ lockCountdownLabel }}</strong>
      </div>

      <fieldset class="login-fieldset" :disabled="isFormDisabled">
        <div class="login-fields">
          <div class="field-group">
            <div ref="boxAdminId" :class="{ 'has-error': invalidAdminId }">
              <label for="login-admin-id">Mã quản trị viên</label>
              <input id="login-admin-id" ref="inputAdminId" type="text" inputmode="numeric" autocomplete="username"
                     maxlength="10" placeholder="Mã quản trị viên" :value="adminId"
                     :class="{ invalid: invalidAdminId }" :aria-invalid="invalidAdminId"
                     :aria-describedby="errors.adminId ? 'errorAdminId' : null" @input="setInputAdminId">
            </div>
            <p v-if="errors.adminId" id="errorAdminId" class="fe" role="alert">{{ errors.adminId }}</p>
          </div>

          <div class="field-group">
            <div ref="boxPassword" :class="{ 'has-error': invalidPassword }">
              <label for="login-password">Mật khẩu</label>
              <div class="login-pw">
                <input id="login-password" ref="inputPassword" :type="showPassword ? 'text' : 'password'"
                       autocomplete="current-password" placeholder="Mật khẩu" v-model="password"
                       :class="{ invalid: invalidPassword }" :aria-invalid="invalidPassword"
                       :aria-describedby="errors.password ? 'errorPassword' : null" @input="clearFieldError('password')">
                <button type="button" class="btn sm" :aria-label="showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'"
                        :aria-pressed="showPassword" @click="togglePassword">{{ showPassword ? 'Ẩn' : 'Hiện' }}</button>
              </div>
            </div>
            <p v-if="errors.password" id="errorPassword" class="fe" role="alert">{{ errors.password }}</p>
          </div>

          <div class="field-group">
            <div ref="boxTotp" :class="{ 'has-error': !!errors.totp }">
              <label for="login-totp">Mã xác thực 6 số</label>
              <input id="login-totp" ref="inputTotp" type="text" inputmode="numeric" autocomplete="one-time-code"
                     maxlength="6" placeholder="Mã xác thực 6 số" :value="totp"
                     :class="{ invalid: !!errors.totp }" :aria-invalid="!!errors.totp"
                     :aria-describedby="errors.totp ? 'errorTotp' : 'hintTotp'" @input="setInputTotp">
            </div>
            <p v-if="!errors.totp" id="hintTotp" class="mut login-hint">Để trống nếu tài khoản chưa bật xác thực 2 lớp (2FA).</p>
            <p v-if="errors.totp" id="errorTotp" class="fe" role="alert">{{ errors.totp }}</p>
          </div>

          <label class="qall login-remember">
            <input id="rememberMe" type="checkbox" v-model="rememberMe">
            <span>Ghi nhớ đăng nhập</span>
          </label>

          <button type="submit" class="btn pri w100 login-submit" :disabled="isFormDisabled">
            <template v-if="isSubmitting"><i class="spin" aria-hidden="true"></i> Đang đăng nhập…</template>
            <template v-else-if="isLocked">Thử lại sau {{ lockCountdownLabel }}</template>
            <template v-else>Đăng nhập</template>
          </button>
        </div>
      </fieldset>

      <span class="visually-hidden" role="status" aria-live="polite">
        {{ isSubmitting ? 'Đang đăng nhập...' : (isLocked ? 'Biểu mẫu đang tạm khóa do nhập sai quá nhiều lần.' : '') }}
      </span>
    </form>

    <footer class="login-foot">©{{ new Date().getFullYear() }} - Nguyen Tat Thanh University - All Rights Reserved.</footer>
  </div>
</template>
