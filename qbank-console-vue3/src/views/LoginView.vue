<script setup>
// =============================================================================
// TRANG ĐĂNG NHẬP — giao diện mới (UI/UX pass), LOGIC GIỮ NGUYÊN 100% fe_web_admin.
//
// Luồng nghiệp vụ không đổi:
//   login(adminId, password, totp|null) -> AdminRepository -> AdminService
//     POST /api/web/admin/auth/login        (bước 1: mã + mật khẩu)
//     POST /api/web/admin/auth/login/totp   (bước 2: kèm mã 6 số khi TOTP_REQUIRED)
//   Kết quả: OK | TOTP_REQUIRED | TOTP_INVALID | LOCKED(retryAfterSeconds) | FAILED
//   Giữ nguyên: validate từng ô, timeout 20s, khóa form khi LOCKED + đếm ngược lưu localStorage,
//   remember-me chỉ lưu adminId, focus/aria cho trình đọc màn hình.
//
// Vòng đời Vue 3 được dùng đúng chỗ:
//   onMounted              : khôi phục "ghi nhớ đăng nhập" + khôi phục khóa còn hiệu lực,
//                            gắn listener visibilitychange, focus ô đầu tiên (chỉ desktop).
//   onBeforeUnmount        : gỡ listener + dừng interval đếm ngược (không rò rỉ timer khi rời route).
//   watch(isLocked)        : mở khóa -> tự focus lại ô mã xác thực.
//   nextTick / rAF         : chờ DOM cập nhật rồi mới focus/select (tránh focus vào node cũ).
//   computed               : trạng thái suy ra từ ref, không giữ state trùng lặp.
// =============================================================================
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '../auth/authStore.js'
import { REQUEST_STATUS } from '../auth/AdminRepository.js'
import AdminStorage from '../auth/AdminStorage.js'
import { DEMO_MODE } from '../config.js'

const MESSAGE = {
  ADMIN_ID_REQUIRED: 'login.error.idRequired',
  PASSWORD_REQUIRED: 'login.error.passwordRequired',
  WRONG_CREDENTIALS: 'login.error.credentials',
  SERVER_ERROR: 'login.error.server',
  TOTP_REQUIRED: 'login.error.totpRequired',
  TOTP_FORMAT: 'login.error.totpFormat',
  TOTP_INVALID: 'login.error.totpInvalid',
  LOCKED: 'login.error.locked',
}
const LOGIN_TIMEOUT_MS = 20000
const LOCK_DEFAULT_SECONDS = 5 * 60
const LOCK_STORAGE_KEY = 'login.totpLock'
const OTP_LENGTH = 6
// admin_id là BIGINT (tối đa 19 chữ số) — không giới hạn 10 như dữ liệu mẫu.
const ADMIN_ID_MAX = 19

const router = useRouter()
const adminId = ref('')
const password = ref('')
const rememberMe = ref(false)
const showPassword = ref(false)
const capsLockOn = ref(false)
const isSubmitting = ref(false)
const isInitializing = ref(true)
const errors = ref({ adminId: '', password: '', totp: '' })
const validateLogin = ref('')
const loginFailed = ref(false)
const lockUntil = ref(0)
const lockRemainingSeconds = ref(0)
let lockTimer = null
// Cờ sống/chết của component: request đăng nhập có thể trả về SAU khi người dùng rời route
// (timeout 20s). Không được gán state cho component đã unmount (Vue sẽ cảnh báo + rò rỉ).
let alive = true

// --- Mã xác thực: 6 ô nhưng chỉ một nguồn dữ liệu (mảng ký tự) -> không bao giờ lệch `totp` ---
const otpChars = ref(Array(OTP_LENGTH).fill(''))
const otpValue = computed(() => otpChars.value.join(''))
const lockStartedAt = ref(0)
const isLocked = computed(() => lockRemainingSeconds.value > 0)
const isFormDisabled = computed(() => isInitializing.value || isSubmitting.value || isLocked.value)
const lockCountdownLabel = computed(() => {
  const t = Math.max(0, lockRemainingSeconds.value)
  return `${String(Math.floor(t / 60)).padStart(2, '0')}:${String(t % 60).padStart(2, '0')}`
})
// Vòng tiến trình cho đồng hồ đếm ngược (0 -> 100%)
const lockProgress = computed(() => {
  if (!isLocked.value || !lockUntil.value) return 0
  const total = Math.max(1, Math.round((lockUntil.value - lockStartedAt.value) / 1000))
  return Math.min(100, Math.max(0, (lockRemainingSeconds.value / total) * 100))
})
const isCredentialError = computed(() => loginFailed.value && !!validateLogin.value)
const invalidAdminId = computed(() => !!errors.value.adminId || isCredentialError.value)
const invalidPassword = computed(() => !!errors.value.password || isCredentialError.value)
const otpFilled = computed(() => otpChars.value.filter(Boolean).length)

const boxAdminId = ref(null), boxPassword = ref(null), boxTotp = ref(null)
const inputAdminId = ref(null), inputPassword = ref(null)
const otpRefs = ref([])
function setOtpRef(el, i) { if (el) otpRefs.value[i] = el }

/* ------------------------------ Tiện ích UI ------------------------------ */
async function focusField(field) {
  await new Promise((r) => requestAnimationFrame(r))
  if (field === 'totp') {
    const i = otpChars.value.findIndex((c) => !c)
    return otpRefs.value[i === -1 ? OTP_LENGTH - 1 : i]?.focus()
  }
  ;({ adminId: inputAdminId.value, password: inputPassword.value })[field]?.focus()
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
function togglePassword() {
  showPassword.value = !showPassword.value
  focusField('password')
}
function onPasswordKey(e) {
  if (typeof e.getModifierState !== 'function') return
  capsLockOn.value = e.getModifierState('CapsLock')
}

/* --------------------------- Mã xác thực 6 ô --------------------------- */
function setOtp(value) {
  const s = String(value ?? '').replace(/\D/g, '').slice(0, OTP_LENGTH)
  otpChars.value = Array.from({ length: OTP_LENGTH }, (_, i) => s[i] || '')
  errors.value.totp = ''
  validateLogin.value = ''
}
function commitOtp(chars) {
  otpChars.value = Array.from({ length: OTP_LENGTH }, (_, i) => (chars[i] || '').slice(-1))
  errors.value.totp = ''
  validateLogin.value = ''
}
function onOtpInput(i, e) {
  const raw = String(e.target.value || '').replace(/\D/g, '')
  if (!raw) { const next = [...otpChars.value]; next[i] = ''; commitOtp(next); return }
  // Dán / autofill dồn nhiều số vào 1 ô -> rải tiếp sang các ô sau
  if (raw.length > 1) {
    const next = [...otpChars.value]
    for (let k = 0; k < raw.length && i + k < OTP_LENGTH; k++) next[i + k] = raw[k]
    commitOtp(next)
    return focusField('totp')
  }
  const next = [...otpChars.value]
  next[i] = raw
  commitOtp(next)
  if (i < OTP_LENGTH - 1) otpRefs.value[i + 1]?.focus()
  else e.target.blur()
}
function onOtpKey(i, e) {
  if (e.key === 'Backspace') {
    if (otpChars.value[i]) return // để input tự xóa rồi @input xử lý
    e.preventDefault()
    if (i === 0) return
    const next = [...otpChars.value]
    next[i - 1] = ''
    commitOtp(next)
    return otpRefs.value[i - 1]?.focus()
  }
  if (e.key === 'ArrowLeft' && i > 0) { e.preventDefault(); otpRefs.value[i - 1]?.focus() }
  if (e.key === 'ArrowRight' && i < OTP_LENGTH - 1) { e.preventDefault(); otpRefs.value[i + 1]?.focus() }
  if (e.key === 'Delete') { e.preventDefault(); const next = [...otpChars.value]; next[i] = ''; commitOtp(next) }
}
function onOtpPaste(e) {
  const text = (e.clipboardData || window.clipboardData)?.getData('text') || ''
  const digits = text.replace(/\D/g, '')
  if (!digits) return
  e.preventDefault()
  const start = Math.max(0, otpRefs.value.indexOf(document.activeElement))
  const next = [...otpChars.value]
  for (let k = 0; k < digits.length && start + k < OTP_LENGTH; k++) next[start + k] = digits[k]
  commitOtp(next)
  focusField('totp')
}
function onOtpFocus(e) { e.target.select?.() }

/* ------------------------------- Validate ------------------------------- */
function validateForm() {
  const id = String(adminId.value ?? '').trim()
  errors.value.adminId = id ? '' : MESSAGE.ADMIN_ID_REQUIRED
  errors.value.password = password.value ? '' : MESSAGE.PASSWORD_REQUIRED
  if (errors.value.adminId) { shake(boxAdminId.value); focusField('adminId'); return false }
  if (errors.value.password) { shake(boxPassword.value); focusField('password'); return false }
  const code = otpValue.value.trim()
  if (code !== '' && !new RegExp(`^\\d{${OTP_LENGTH}}$`).test(code)) {
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

/* ---------------------- Khóa form khi LOCKED (nguyên bản) ---------------------- */
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
  const now = Date.now()
  lockUntil.value = now + total * 1000
  lockStartedAt.value = now
  lockRemainingSeconds.value = total
  persistLock(); runLockTimer()
}
function releaseLock() {
  stopLockTimer()
  lockUntil.value = 0; lockRemainingSeconds.value = 0; lockStartedAt.value = 0
  clearPersistedLock()
  validateLogin.value = ''; errors.value.totp = ''
  setOtp('')
}
function restoreLock() {
  try {
    const raw = localStorage.getItem(LOCK_STORAGE_KEY)
    if (!raw) return
    const saved = JSON.parse(raw)
    const until = Number(saved?.until) || 0
    const remaining = Math.ceil((until - Date.now()) / 1000)
    if (remaining > 0) {
      if (!adminId.value && saved.adminId) adminId.value = String(saved.adminId)
      lockUntil.value = until
      lockStartedAt.value = until - LOCK_DEFAULT_SECONDS * 1000
      lockRemainingSeconds.value = Math.ceil(remaining)
      runLockTimer()
    } else clearPersistedLock()
  } catch { clearPersistedLock() }
}
function onLockVisibilityChange() {
  if (document.visibilityState === 'visible' && lockUntil.value > 0) onLockTick()
}

/* ------------------------------ Gửi đăng nhập ------------------------------ */
// Luôn đi qua cùng sự kiện submit như nút đăng nhập. Chỉ xử lý các ô nhập
// thông tin; nút và checkbox giữ thao tác bàn phím mặc định của chúng.
function onFormEnter(e) {
  if (e.isComposing || e.keyCode === 229) return
  const target = e.target
  if (!(target instanceof HTMLInputElement) || !['text', 'password'].includes(target.type)) return
  e.preventDefault()
  if (!e.repeat && !isFormDisabled.value) e.currentTarget.requestSubmit()
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
      login(adminId.value, password.value, otpValue.value.trim() || null),
      LOGIN_TIMEOUT_MS,
    )
    if (!alive) return
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
        setOtp('')
        shake(boxTotp.value); fieldToFocus = 'totp'
        break
      case REQUEST_STATUS.LOCKED:
        setOtp(''); errors.value.totp = ''
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
    if (!alive) return
    console.error('Login error:', e)
    validateLogin.value = MESSAGE.SERVER_ERROR
  } finally {
    if (alive) isSubmitting.value = false
  }
  if (alive && fieldToFocus) focusField(fieldToFocus)
}

/* ------------------------------- Lifecycle ------------------------------- */
watch(isLocked, (locked) => { if (!locked) nextTick(() => focusField('totp')) })

onMounted(() => {
  document.title = 'Đăng nhập'
  document.addEventListener('visibilitychange', onLockVisibilityChange)
  const saved = new AdminStorage().getLocalStorageRememberMe()
  if (saved?.adminId) { rememberMe.value = true; adminId.value = String(saved.adminId) }
  restoreLock()
  isInitializing.value = false
  const isDesktopLike = window.matchMedia?.('(hover: hover) and (pointer: fine)').matches
  if (isDesktopLike && !isLocked.value) focusField(adminId.value ? 'password' : 'adminId')
})
onBeforeUnmount(() => {
  alive = false
  document.removeEventListener('visibilitychange', onLockVisibilityChange)
  stopLockTimer()
})
</script>

<template>
  <div class="login" data-login-ui="v2">
    <!-- Cột thương hiệu: chỉ hiện từ 960px, thuần trang trí (aria-hidden) -->
    <aside class="login__brand" aria-hidden="true">
      <div class="brand__glow"></div>
      <div class="brand__top">
        <span class="brand__mark">QB</span>
        <span class="brand__wordmark">
          <strong>{{ $t('brand.name') }}</strong>
          <small>{{ $t('auth.brandFoot') }}</small>
        </span>
      </div>

      <div class="brand__body">
        <p class="brand__eyebrow">{{ $t('admin.systemTitle') }}</p>
        <h2 class="brand__title">{{ $t('auth.brandEyebrow') }}<br>{{ $t('brand.quizTagline') }}</h2>
        <p class="brand__lead">{{ $t('brand.tagline') }}</p>
        <ul class="brand__list">
          <li>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3l7 4v5c0 4.4-3 8.3-7 9-4-.7-7-4.6-7-9V7z"/><path d="M9 12l2 2 4-4"/></svg>
            <span>{{ $t('auth.twoLayer') }}</span>
          </li>
          <li>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 5h16v12H4z"/><path d="M8 21h8"/><path d="M12 17v4"/></svg>
            <span>{{ $t('ai.flowTitle') }}</span>
          </li>
          <li>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 6v12"/><path d="M6 12h12"/><circle cx="12" cy="12" r="9"/></svg>
            <span>{{ $t('bank.versionHistory') }}</span>
          </li>
        </ul>
      </div>

      <p class="brand__foot">©{{ new Date().getFullYear() }} Nguyen Tat Thanh University</p>
    </aside>

    <!-- Cột form -->
    <main class="login__panel">
      <div class="panel__inner">
        <div class="mobile-brand">
          <span class="brand__mark brand__mark--sm">QB</span>
          <span class="brand__wordmark">
            <strong>{{ $t('brand.name') }}</strong>
            <small>{{ $t('brand.sub') }}</small>
          </span>
        </div>

        <form class="lform" novalidate :aria-busy="isSubmitting"
          @submit.prevent="handleLoginEvent" @keydown.enter="onFormEnter">
          <header class="lform__head">
            <p class="lform__eyebrow">{{ $t('auth.welcomeBack') }}</p>
            <h1 class="lform__title">{{ $t('auth.submit') }}</h1>
            <p class="lform__sub">{{ $t('llm.adminOnly') }}</p>
          </header>

          <div v-if="DEMO_MODE" class="note note--info" role="status">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 8h.01M11 12h1v4h1"/></svg>
            <p><b>{{ $t('auth.demoMode') }}</b> {{ $t('auth.demoData') }} <code>VITE_DEMO_MODE</code>{{ $t('auth.demoProd') }}</p>
          </div>

          <Transition name="alert">
            <div v-if="validateLogin && !isLocked" class="note note--error" role="alert">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 8v5M12 16h.01"/></svg>
              <p>{{ $t(validateLogin) }}</p>
            </div>
          </Transition>

          <div v-if="isLocked" class="note note--locked" role="alert">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="4" y="10" width="16" height="10" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/></svg>
            <div class="note__body">
              <p>{{ $t(MESSAGE.LOCKED) }}</p>
              <div class="locked-timer">
                <span class="locked-timer__value">{{ lockCountdownLabel }}</span>
                <span class="locked-timer__track"><i :style="{ width: lockProgress + '%' }"></i></span>
              </div>
            </div>
          </div>

          <fieldset class="lform__fields" :disabled="isFormDisabled">
            <!-- Mã quản trị viên -->
            <div class="field" :class="{ 'is-error': invalidAdminId }">
              <label for="login-admin-id">{{ $t('admin.adminId') }} <span class="req" aria-hidden="true">*</span></label>
              <div ref="boxAdminId" class="control">
                <svg class="control__ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
                <input id="login-admin-id" ref="inputAdminId" type="text" inputmode="numeric" autocomplete="username"
                       :maxlength="ADMIN_ID_MAX" :placeholder="$t('validation.exampleId')" :value="adminId"
                       :aria-invalid="invalidAdminId" :aria-describedby="errors.adminId ? 'errorAdminId' : null"
                       @input="setInputAdminId">
              </div>
              <p v-if="errors.adminId" id="errorAdminId" class="fe" role="alert">{{ $t(errors.adminId) }}</p>
            </div>

            <!-- Mật khẩu -->
            <div class="field" :class="{ 'is-error': invalidPassword }">
              <div class="field__row">
                <label for="login-password">{{ $t('auth.password') }} <span class="req" aria-hidden="true">*</span></label>
                <span v-if="capsLockOn && !isFormDisabled" class="caps" role="status">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 4l7 8h-4v4H9v-4H5z"/><path d="M9 20h6"/></svg>
                  {{ $t('auth.capsLock') }}
                </span>
              </div>
              <div ref="boxPassword" class="control">
                <svg class="control__ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="4" y="10" width="16" height="10" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/></svg>
                <input id="login-password" ref="inputPassword" :type="showPassword ? 'text' : 'password'"
                       autocomplete="current-password" :placeholder="$t('auth.passwordPlaceholder')" v-model="password"
                       :aria-invalid="invalidPassword" :aria-describedby="errors.password ? 'errorPassword' : null"
                       @input="clearFieldError('password')" @keydown="onPasswordKey" @keyup="onPasswordKey">
                <button type="button" class="control__btn" :aria-label="showPassword ? $t('login.hidePassword') : $t('login.showPassword')"
                        :aria-pressed="showPassword" tabindex="0" @click="togglePassword">
                  <svg v-if="!showPassword" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7z"/><circle cx="12" cy="12" r="3"/></svg>
                  <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 3l18 18"/><path d="M10.6 6.2A10.9 10.9 0 0 1 12 5c6.4 0 10 7 10 7a18 18 0 0 1-3.2 4.1"/><path d="M6.6 6.7A17.8 17.8 0 0 0 2 12s3.6 7 10 7a10.9 10.9 0 0 0 4.3-.9"/><path d="M9.9 10.1a3 3 0 0 0 4.2 4.2"/></svg>
                </button>
              </div>
              <p v-if="errors.password" id="errorPassword" class="fe" role="alert">{{ $t(errors.password) }}</p>
            </div>

            <!-- Mã xác thực 2 lớp -->
            <div class="field" :class="{ 'is-error': !!errors.totp }">
              <div class="field__row">
                <label id="login-totp-label">{{ $t('tfa.code') }}</label>
                <span class="field__optional" :class="{ 'is-filled': otpFilled === OTP_LENGTH }">{{ otpFilled }}/{{ OTP_LENGTH }}</span>
              </div>
              <div ref="boxTotp" class="otp" role="group" aria-labelledby="login-totp-label"
                   :aria-describedby="errors.totp ? 'errorTotp' : 'hintTotp'">
                <input v-for="i in OTP_LENGTH" :key="i - 1" :ref="(el) => setOtpRef(el, i - 1)"
                       class="otp__box" type="text" inputmode="numeric" maxlength="1"
                       :autocomplete="i === 1 ? 'one-time-code' : 'off'"
                       :aria-label="`Chữ số thứ ${i} của mã xác thực`"
                       :value="otpChars[i - 1]"
                       @input="onOtpInput(i - 1, $event)" @keydown="onOtpKey(i - 1, $event)"
                       @paste="onOtpPaste" @focus="onOtpFocus">
              </div>
              <p v-if="!errors.totp" id="hintTotp" class="hint">{{ $t('tfa.noOtp') }}</p>
              <p v-if="errors.totp" id="errorTotp" class="fe" role="alert">{{ $t(errors.totp) }}</p>
            </div>

            <div class="lform__meta">
              <label class="check">
                <input id="rememberMe" type="checkbox" v-model="rememberMe">
                <span class="check__box" aria-hidden="true">
                  <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M3 8.5l3.2 3.2L13 4.8"/></svg>
                </span>
                <span>{{ $t('auth.remember') }}</span>
              </label>
            </div>

            <button type="submit" class="lsubmit" :disabled="isFormDisabled">
              <template v-if="isSubmitting"><i class="spin" aria-hidden="true"></i> {{ $t('auth.submitting') }}</template>
              <template v-else-if="isLocked">{{ $t('login.retryAfterTime', { t: lockCountdownLabel }) }}</template>
              <template v-else>{{ $t('auth.submit') }}</template>
            </button>
          </fieldset>

          <p class="lform__tip">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 3l7 4v5c0 4.4-3 8.3-7 9-4-.7-7-4.6-7-9V7z"/></svg>
            {{ $t('auth.jwtNote') }}
          </p>

          <span class="visually-hidden" role="status" aria-live="polite">
            {{ isSubmitting ? $t('login.signingIn') : (isLocked ? $t('login.formLocked') : '') }}
          </span>
        </form>

        <footer class="panel__foot">
          <span>©{{ new Date().getFullYear() }} - Nguyen Tat Thanh University</span>
          <span class="panel__dot" aria-hidden="true">·</span>
          <span>{{ $t('brand.allRights') }}</span>
          <span class="panel__dot" aria-hidden="true">·</span>
          <span class="panel__ver" :title="$t('brand.oldUi')">{{ $t('auth.version') }}</span>
        </footer>
      </div>
    </main>
  </div>
</template>

<style scoped>
/* =============================================================
   LOGIN — thiết kế 2 cột: thương hiệu (>=960px) + form.
   Dùng token ngữ nghĩa của main.css (--primary-ink, --surface, --text...) để đồng bộ
   và tự đổi theo giao diện sáng/tối. Cột thương hiệu giữ gradient xanh đậm cố định
   vì chữ trên đó luôn là trắng — đạt tương phản ở cả hai theme.
   ============================================================= */
.login {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--bg, #f8faff);
}
@media (min-width: 960px) {
  .login { grid-template-columns: minmax(0, 1.02fr) minmax(0, 0.98fr); }
}

/* ---------------- Cột thương hiệu ---------------- */
.login__brand {
  position: relative;
  display: none;
  flex-direction: column;
  justify-content: space-between;
  gap: 2rem;
  padding: clamp(2rem, 4vw, 3.5rem);
  overflow: hidden;
  color: #fff;
  background:
    radial-gradient(120% 90% at 8% 0%, rgba(0, 132, 214, 0.55), transparent 60%),
    radial-gradient(90% 70% at 100% 100%, rgba(0, 196, 180, 0.25), transparent 65%),
    linear-gradient(158deg, #002744 0%, #003a69 45%, #004b87 100%);
}
@media (min-width: 960px) { .login__brand { display: flex; } }
.brand__glow {
  position: absolute;
  inset: -30% -20% auto auto;
  width: 34rem; height: 34rem;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(94, 210, 255, 0.22), transparent 62%);
  pointer-events: none;
}
.login__brand::after {
  content: "";
  position: absolute;
  inset: 0;
  background-image: radial-gradient(rgba(255, 255, 255, 0.16) 1px, transparent 1px);
  background-size: 26px 26px;
  -webkit-mask-image: radial-gradient(75% 60% at 75% 20%, #000 0%, transparent 75%);
  mask-image: radial-gradient(75% 60% at 75% 20%, #000 0%, transparent 75%);
  pointer-events: none;
}
.brand__top, .brand__body, .brand__foot { position: relative; z-index: 1; }
.brand__top { display: flex; align-items: center; gap: 0.8rem; }
.brand__mark {
  display: grid; place-items: center;
  width: 2.75rem; height: 2.75rem;
  border-radius: 12px;
  background: #fff; color: var(--primary-ink);
  font-size: 1.0625rem; font-weight: 800; letter-spacing: -0.02em;
  box-shadow: 0 10px 24px -10px rgba(0, 0, 0, 0.6);
}
.brand__mark--sm { width: 2.25rem; height: 2.25rem; border-radius: 10px; font-size: 0.9rem; }
.brand__wordmark { display: flex; flex-direction: column; line-height: 1.25; }
.brand__wordmark strong { font-size: 1.0625rem; letter-spacing: -0.01em; }
.brand__wordmark small { font-size: 0.75rem; color: rgba(255, 255, 255, 0.72); }
.brand__body { max-width: 30rem; }
.brand__eyebrow {
  margin: 0 0 0.9rem;
  font-size: 0.75rem; font-weight: 700; letter-spacing: 0.14em; text-transform: uppercase;
  color: #7fd7ff;
}
.brand__title {
  margin: 0 0 1rem;
  font-size: clamp(1.75rem, 1.2rem + 1.4vw, 2.35rem);
  line-height: 1.22; letter-spacing: -0.02em; color: #fff;
}
.brand__lead { margin: 0 0 1.75rem; color: rgba(255, 255, 255, 0.78); font-size: 0.9375rem; line-height: 1.65; }
.brand__list { display: flex; flex-direction: column; gap: 0.85rem; margin: 0; padding: 0; list-style: none; }
.brand__list li { display: flex; align-items: center; gap: 0.7rem; font-size: 0.9rem; color: rgba(255, 255, 255, 0.92); }
.brand__list svg { flex: none; width: 1.15rem; height: 1.15rem; color: #7fd7ff; }
.brand__foot { margin: 0; font-size: 0.75rem; color: rgba(255, 255, 255, 0.55); }

/* ---------------- Cột form ---------------- */
.login__panel {
  display: flex; align-items: center; justify-content: center;
  padding: clamp(1.5rem, 5vw, 3.5rem) clamp(1rem, 4vw, 2.5rem);
}
.panel__inner { width: 100%; max-width: 27rem; display: flex; flex-direction: column; gap: 1.25rem; }
.mobile-brand { display: flex; align-items: center; gap: 0.7rem; color: var(--text, #1b2a41); }
.mobile-brand .brand__mark { background: var(--primary-ink); color: #fff; box-shadow: 0 8px 20px -12px rgba(0, 75, 135, 0.9); }
.mobile-brand .brand__wordmark small { color: var(--text-muted, #5b6b82); }
@media (min-width: 960px) { .mobile-brand { display: none; } }

.lform {
  display: flex; flex-direction: column; gap: 1.35rem;
  padding: clamp(1.5rem, 3.2vw, 2.25rem);
  /* --surface thay cho #fff cứng: ở dark mode thành nền thẻ tối, chữ tự đổi theo --text */
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 18px;
  box-shadow: var(--shadow-card);
}
.lform__head { display: flex; flex-direction: column; gap: 0.3rem; }
.lform__eyebrow {
  margin: 0; font-size: 0.72rem; font-weight: 700; letter-spacing: 0.13em;
  text-transform: uppercase; color: var(--primary-ink); opacity: 0.85;
}
.lform__title { margin: 0; font-size: clamp(1.45rem, 1.2rem + 0.7vw, 1.7rem); letter-spacing: -0.02em; line-height: 1.2; }
.lform__sub { margin: 0; color: var(--text-muted, #5b6b82); font-size: 0.9rem; }
.lform__fields { display: flex; flex-direction: column; gap: 1.15rem; min-width: 0; margin: 0; padding: 0; border: 0; }
.lform__meta { display: flex; align-items: center; justify-content: space-between; gap: 1rem; margin-top: -0.15rem; }
.lform__tip {
  display: flex; align-items: flex-start; gap: 0.45rem; margin: -0.35rem 0 0;
  color: var(--text-muted, #5b6b82); font-size: 0.78rem; line-height: 1.5;
}
.lform__tip svg { flex: none; width: 0.95rem; height: 0.95rem; margin-top: 0.1rem; color: var(--success-strong); }

/* ---------------- Ô nhập ---------------- */
.field { display: flex; flex-direction: column; gap: 0.45rem; min-width: 0; }
.field__row { display: flex; align-items: center; justify-content: space-between; gap: 0.75rem; }
.field .req { color: var(--danger-strong); font-weight: 700; }
.field > label, .field__row > label {
  font-size: 0.8125rem; font-weight: 600; color: var(--text); letter-spacing: 0.005em;
}
.field__optional {
  font-size: 0.72rem; font-weight: 700; color: var(--text-muted, #5b6b82);
  padding: 0.1rem 0.45rem; border-radius: 999px; background: var(--surface-hover);
  font-variant-numeric: tabular-nums;
}
.field__optional.is-filled { background: var(--success-soft, #e8f6ee); color: var(--success-strong, #166534); }
.caps {
  display: inline-flex; align-items: center; gap: 0.3rem;
  font-size: 0.72rem; font-weight: 700; color: var(--warning-strong);
  background: var(--warning-soft); border: 1px solid var(--warning-border); border-radius: 999px; padding: 0.12rem 0.5rem;
}
.caps svg { width: 0.8rem; height: 0.8rem; }

.control { position: relative; display: flex; align-items: center; min-width: 0; }
.control__ic {
  position: absolute; left: 0.85rem; width: 1.1rem; height: 1.1rem;
  color: var(--text-muted); pointer-events: none; transition: color 0.18s ease;
}
.control input {
  width: 100%; height: 3rem;
  padding: 0 2.85rem 0 2.55rem;
  border: 1.5px solid var(--border-strong); border-radius: 11px;
  background: var(--surface); color: var(--text);
  font-family: inherit; font-size: 0.9375rem; font-weight: 500;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, background-color 0.18s ease;
}
.control input::placeholder { color: var(--text-muted); font-weight: 400; }
.control input:hover:not(:disabled) { border-color: var(--primary-ink); }
.control input:focus {
  outline: none; border-color: var(--primary-ink);
  background: var(--surface); box-shadow: 0 0 0 4px var(--primary-soft);
}
.control input:disabled { background: var(--surface-hover); color: var(--text-muted); cursor: not-allowed; }
.control input:-webkit-autofill,
.control input:-webkit-autofill:focus {
  -webkit-text-fill-color: var(--text);
  -webkit-box-shadow: 0 0 0 1000px var(--surface) inset;
  transition: background-color 9999s ease-out;
}
.control__btn {
  position: absolute; right: 0.35rem;
  display: grid; place-items: center;
  width: 2.25rem; height: 2.25rem;
  border: 0; border-radius: 9px;
  background: transparent; color: var(--text-muted); cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
}
.control__btn svg { width: 1.1rem; height: 1.1rem; }
.control__btn:hover { background: var(--surface-hover); color: var(--primary-ink); }
.control__btn:focus-visible { outline: 2px solid var(--primary-ink); outline-offset: 1px; }

.field.is-error .control input { border-color: var(--danger-strong); background: var(--danger-soft); }
.field.is-error .control input:focus { box-shadow: 0 0 0 4px var(--danger-soft); }
.field.is-error .control__ic { color: var(--danger-strong); }

.hint { margin: 0; font-size: 0.78rem; line-height: 1.45; color: var(--text-muted, #5b6b82); }
.fe { display: block; margin: 0; color: var(--danger-strong, #a51818); font-size: 0.78rem; font-weight: 600; line-height: 1.4; }

/* ---------------- Mã xác thực 6 ô ---------------- */
.otp { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 0.45rem; }
.otp__box {
  width: 100%; height: 3rem; padding: 0;
  border: 1.5px solid var(--border-strong); border-radius: 11px;
  background: var(--surface); color: var(--text);
  font-family: ui-monospace, "SF Mono", Consolas, monospace;
  font-size: 1.15rem; font-weight: 700; text-align: center;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, background-color 0.18s ease, transform 0.12s ease;
  -moz-appearance: textfield;
}
.otp__box::-webkit-outer-spin-button, .otp__box::-webkit-inner-spin-button { -webkit-appearance: none; margin: 0; }
.otp__box:hover:not(:disabled) { border-color: var(--primary-ink); }
.otp__box:focus {
  outline: none; border-color: var(--primary-ink);
  background: var(--surface); box-shadow: 0 0 0 3px var(--primary-soft);
  transform: translateY(-1px);
}
.otp__box:disabled { background: var(--surface-hover); color: var(--text-muted); cursor: not-allowed; }
.field.is-error .otp__box { border-color: var(--danger-strong); background: var(--danger-soft); }
.field.is-error .otp__box:focus { box-shadow: 0 0 0 3px var(--danger-soft); }

/* ---------------- Ghi nhớ đăng nhập ---------------- */
.check { display: inline-flex; align-items: center; gap: 0.55rem; cursor: pointer; user-select: none; color: var(--text, #1b2a41); font-size: 0.86rem; font-weight: 500; }
.check input { position: absolute; width: 1px; height: 1px; margin: -1px; padding: 0; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; border: 0; }
.check__box {
  display: grid; place-items: center;
  width: 1.15rem; height: 1.15rem;
  border: 1.5px solid var(--border-strong); border-radius: 5px; background: var(--surface);
  transition: background-color 0.15s ease, border-color 0.15s ease, box-shadow 0.15s ease;
}
.check__box svg { width: 0.75rem; height: 0.75rem; color: var(--on-primary); opacity: 0; transform: scale(0.6); transition: opacity 0.15s ease, transform 0.15s ease; }
.check input:checked + .check__box { background: var(--primary-ink); border-color: var(--primary-ink); }
.check input:checked + .check__box svg { opacity: 1; transform: scale(1); }
.check input:focus-visible + .check__box { box-shadow: 0 0 0 3px var(--primary-soft); }
.check input:disabled + .check__box { background: var(--surface-hover); border-color: var(--border); }
.check:hover .check__box { border-color: var(--primary-ink); }

/* ---------------- Nút đăng nhập ---------------- */
.lsubmit {
  display: inline-flex; align-items: center; justify-content: center; gap: 0.55rem;
  width: 100%; height: 3.125rem; margin-top: 0.15rem;
  border: 0; border-radius: 11px;
  background: linear-gradient(135deg, #00599f 0%, #004b87 55%, #00447b 100%);
  color: #fff; font-family: inherit; font-size: 0.9375rem; font-weight: 700; letter-spacing: 0.01em;
  cursor: pointer;
  box-shadow: 0 10px 22px -12px rgba(0, 75, 135, 0.95);
  transition: filter 0.18s ease, box-shadow 0.18s ease, transform 0.12s ease;
}
.lsubmit svg { width: 1.05rem; height: 1.05rem; }
.lsubmit:hover:not(:disabled) { filter: brightness(1.08); box-shadow: 0 14px 28px -12px rgba(0, 75, 135, 1); }
.lsubmit:active:not(:disabled) { transform: translateY(1px); }
.lsubmit:focus-visible { outline: 2px solid var(--primary-ink); outline-offset: 3px; }
.lsubmit:disabled { cursor: not-allowed; opacity: 0.92; filter: grayscale(0.4) brightness(0.95); box-shadow: none; }
.lsubmit .spin { width: 1rem; height: 1rem; border-width: 2.5px; }

/* ---------------- Thông báo ---------------- */
.note {
  display: flex; align-items: flex-start; gap: 0.6rem;
  padding: 0.75rem 0.9rem;
  border: 1px solid transparent; border-radius: 11px;
  font-size: 0.83rem; line-height: 1.5; color: var(--text, #1b2a41);
}
.note p { margin: 0; }
.note > svg { flex: none; width: 1.05rem; height: 1.05rem; margin-top: 0.1rem; }
.note--info { background: var(--primary-soft); border-color: var(--border); }
.note--info > svg { color: var(--primary-ink); }
.note--info code { padding: 0.05rem 0.3rem; border-radius: 4px; background: var(--surface); border: 1px solid var(--border); font-size: 0.78rem; }
.note--error { background: var(--danger-soft, #fdeeee); border-color: var(--danger-border, #f3c3c3); color: var(--danger-strong, #a51818); }
.note--error > svg { color: var(--danger-strong, #a51818); }
.note--locked { background: var(--warning-soft); border-color: var(--warning-border); color: var(--warning-strong); }
.note--locked > svg { color: var(--warning-strong); }
.note__body { flex: 1; min-width: 0; }
.locked-timer { display: flex; align-items: center; gap: 0.6rem; margin-top: 0.5rem; }
.locked-timer__value { font-variant-numeric: tabular-nums; font-weight: 800; font-size: 1rem; letter-spacing: 0.02em; }
.locked-timer__track { flex: 1; height: 0.35rem; border-radius: 999px; background: var(--warning-badge-bg); overflow: hidden; }
.locked-timer__track i { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, #f0b429, #d18700); transition: width 0.9s linear; }

/* Hiệu ứng xuất hiện của thông báo */
.alert-enter-active { transition: opacity 0.2s ease, transform 0.2s ease; }
.alert-leave-active { transition: opacity 0.15s ease; }
.alert-enter-from { opacity: 0; transform: translateY(-4px); }
.alert-leave-to { opacity: 0; }

/* ---------------- Chân trang ---------------- */
.panel__foot {
  display: flex; flex-wrap: wrap; align-items: center; justify-content: center; gap: 0.4rem;
  font-size: 0.75rem; color: var(--text-muted, #5b6b82); text-align: center;
}
.panel__dot { opacity: 0.6; }
.panel__ver { font-size: 0.7rem; font-weight: 600; letter-spacing: 0.02em; color: var(--text-muted); }

.visually-hidden {
  position: absolute !important; width: 1px; height: 1px; margin: -1px; padding: 0;
  overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; border: 0;
}

/* ---------------- Responsive nhỏ ---------------- */
@media (max-width: 380px) {
  .lform { padding: 1.15rem; border-radius: 14px; }
  .otp { gap: 0.3rem; }
  .otp__box { height: 2.75rem; font-size: 1rem; border-radius: 9px; }
  .control input { height: 2.85rem; }
  .lsubmit { height: 2.95rem; }
}
@media (max-height: 560px) and (orientation: landscape) {
  .login__panel { align-items: flex-start; padding-block: 1rem; }
}

/* Tôn trọng người dùng giảm chuyển động */
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.001ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.001ms !important;
  }
}

/* Chế độ tối của hệ điều hành: giữ nền sáng để tương phản chữ ổn định,
   nhưng dịu viền/đổ bóng cho đỡ chói */
@media (prefers-color-scheme: dark) {
  .lform { box-shadow: 0 1px 2px rgba(0, 0, 0, 0.2), 0 24px 56px -22px rgba(0, 0, 0, 0.55); }
}
</style>
