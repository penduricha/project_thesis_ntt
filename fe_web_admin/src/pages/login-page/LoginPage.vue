<script>
import './login-page.scss';
import '@/assets/style/theme-color.scss';
import RouterMgmt from "@/router/RouterMgmt.js";
import ButtonRed from "@/components/button/button_red/ButtonRed.vue";

import AdminStorage from "@/local_storage/AdminStorage.js";
import {AdminAccount} from "@/model/AdminAccount.js";
import AdminRepository, {REQUEST_STATUS} from "@/repository/AdminRepository.js";
import Footer from "@/components/footer/Footer.vue";
import Header from "@/components/header/Header.vue";

/* Thông báo hiển thị cho người dùng: gom 1 chỗ để dễ sửa câu chữ */
const MESSAGE = {
  ADMIN_ID_REQUIRED: 'Vui lòng nhập mã quản trị viên.',
  PASSWORD_REQUIRED: 'Vui lòng nhập mật khẩu.',
  WRONG_CREDENTIALS: 'Mã quản trị viên hoặc mật khẩu không đúng. Vui lòng kiểm tra lại.',
  SERVER_ERROR: 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra mạng rồi thử lại.',
  NAVIGATE_ERROR: 'Đăng nhập thành công nhưng không thể chuyển trang. Vui lòng tải lại trang.',
  TOTP_REQUIRED: 'Tài khoản đã bật xác thực 2 lớp. Bắt buộc nhập khóa 6 số đang hiển thị trong ứng dụng Authenticator.',
  TOTP_FORMAT: 'Khóa xác thực gồm đúng 6 chữ số.',
  TOTP_INVALID: 'Mã xác thực không đúng hoặc đã hết hạn. Hãy nhập mã mới đang hiển thị trên ứng dụng.',
  LOCKED: 'Nhập sai quá nhiều lần. Biểu mẫu tạm khóa, sẽ tự mở lại khi hết thời gian đếm ngược.',
};

/* Quá thời gian này mà server chưa trả lời thì mở khoá form, tránh bị treo vô hạn */
const LOGIN_TIMEOUT_MS = 20000;

/* Khóa form khi nhập sai khóa 6 số quá nhiều lần.
   Thời gian chính xác do server trả về (retryAfterSeconds); con số này chỉ dùng khi server không gửi. */
const LOCK_DEFAULT_SECONDS = 5 * 60;
/* Lưu hạn khóa để tải lại trang (F5) không làm mất đếm ngược. Server vẫn là nơi quyết định cuối cùng. */
const LOCK_STORAGE_KEY = 'login.totpLock';

export default {
  name: "LoginPage",

  components: {
    ButtonRed,
    Header,
    Footer,
  },

  created() {
    this.setTitlePage();
    this.saveRouterPath();
    this.getStatusCheckBoxRememberMe();
    this.restoreLock();
  },

  mounted() {
    document.addEventListener('visibilitychange', this.onLockVisibilityChange);

    // Đang bị khóa thì các ô đang disabled, không focus.
    // Chỉ tự focus trên máy có chuột/bàn phím.
    // Trên điện thoại không tự focus để bàn phím ảo không bật lên bất ngờ.
    const isDesktopLike = window.matchMedia?.('(hover: hover) and (pointer: fine)').matches;
    if (isDesktopLike && !this.isLocked) {
      this.focusField(this.adminId ? 'password' : 'adminId');
    }
  },

  beforeUnmount() {
    document.removeEventListener('visibilitychange', this.onLockVisibilityChange);
    this.stopLockTimer();
  },

  data() {
    return {
      adminId: null,
      password: "",
      rememberMe: false,
      keyAuthenticator: "",
      showPassword: false,

      // true từ lúc bấm đăng nhập đến lúc có kết quả: khoá cả form + hiện hiệu ứng load
      isSubmitting: false,

      // lỗi theo từng ô nhập (hiện ngay dưới ô)
      errors: {
        adminId: "",
        password: "",
        totp: "",
      },

      // lỗi chung của form (sai tài khoản/mật khẩu, lỗi server...) hiện phía trên ô "Ghi nhớ"
      validateLogin: "",
      // true khi server báo sai mã/mật khẩu -> tô đỏ cả 2 ô
      loginFailed: false,

      // Khóa form khi nhập sai khóa 6 số quá nhiều lần
      lockUntil: 0,              // mốc thời gian (ms) mở khóa; 0 = không khóa
      lockRemainingSeconds: 0,   // số giây còn lại, cập nhật mỗi giây
      lockTimer: null,
    }
  },

  computed: {
    isLocked() {
      return this.lockRemainingSeconds > 0;
    },
    lockMessage() {
      return MESSAGE.LOCKED;
    },
    // 04:59
    lockCountdownLabel() {
      const total = Math.max(0, this.lockRemainingSeconds);
      const mm = String(Math.floor(total / 60)).padStart(2, '0');
      const ss = String(total % 60).padStart(2, '0');
      return `${mm}:${ss}`;
    },
    isCredentialError() {
      return this.loginFailed && !!this.validateLogin;
    },
    invalidAdminId() {
      return !!this.errors.adminId || this.isCredentialError;
    },
    invalidPassword() {
      return !!this.errors.password || this.isCredentialError;
    },
  },

  methods: {
    setTitlePage() {
      document.title = 'Đăng nhập';
    },

    saveRouterPath(route) {
      const routePath = this.$route.path;
      const routerManagement = new RouterMgmt();
      routerManagement.savePathToSessionStorage(routePath);
    },

    //lock paste
    preventPaste(event) {
      event.preventDefault();
    },

    /* ------------------------------------------------------------
       SỰ KIỆN NHẬP LIỆU
       Gõ vào ô nào thì xoá lỗi của ô đó (và thông báo chung)
       ------------------------------------------------------------ */
    clearFieldError(field) {
      this.errors[field] = "";
      this.loginFailed = false;
      this.validateLogin = "";
    },

    setInputAdminId(event) {
      // Chỉ cho phép các ký tự số
      this.adminId = event.target.value.replace(/[^0-9]/g, '');
      this.clearFieldError('adminId');
    },

    setInputKeyAuthenticator(event) {
      // Chỉ cho phép các ký tự số, tối đa 6
      this.keyAuthenticator = event.target.value.replace(/[^0-9]/g, '').slice(0, 6);
      this.errors.totp = "";
      this.validateLogin = "";
    },

    setInputPassword() {
      this.clearFieldError('password');
    },

    togglePassword() {
      this.showPassword = !this.showPassword;
      this.focusField('password');
    },

    /* ------------------------------------------------------------
       HIỆU ỨNG / FOCUS
       ------------------------------------------------------------ */
    async focusField(field) {
      // Chờ Vue vẽ lại DOM (gỡ disabled) rồi mới focus được
      await this.$nextTick();
      const inputs = {
        adminId: this.$refs.inputAdminId,
        password: this.$refs.inputPassword,
        totp: this.$refs.inputTotp,
      };
      inputs[field]?.focus();
    },

    // Rung nhẹ ô nhập để thu hút sự chú ý. Tự tắt nếu người dùng bật "giảm chuyển động".
    shakeElement(element) {
      if (!element || typeof element.animate !== 'function') return;
      if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return;
      element.animate(
          [
            {transform: 'translateX(0)'},
            {transform: 'translateX(-6px)'},
            {transform: 'translateX(6px)'},
            {transform: 'translateX(-4px)'},
            {transform: 'translateX(4px)'},
            {transform: 'translateX(0)'},
          ],
          {duration: 400, easing: 'ease-in-out'}
      );
    },

    /* ------------------------------------------------------------
       VALIDATE
       ------------------------------------------------------------ */
    validateForm() {
      const adminId = String(this.adminId ?? '').trim();
      const password = String(this.password ?? '');

      this.errors.adminId = adminId ? "" : MESSAGE.ADMIN_ID_REQUIRED;
      this.errors.password = password ? "" : MESSAGE.PASSWORD_REQUIRED;

      if (this.errors.adminId) this.shakeElement(this.$refs.boxAdminId);
      if (this.errors.password) this.shakeElement(this.$refs.boxPassword);

      // focus vào ô lỗi đầu tiên
      if (this.errors.adminId) {
        this.focusField('adminId');
        return false;
      }
      if (this.errors.password) {
        this.focusField('password');
        return false;
      }

      // Ô khóa 6 số: để trống thì hợp lệ (tài khoản chưa cài 2FA vẫn đăng nhập được).
      // Việc "đã cài 2FA thì bắt buộc nhập" do server quyết định (chỉ server biết, và chỉ nói sau khi mật khẩu đúng).
      // Đã nhập thì phải đúng 6 số.
      const totp = String(this.keyAuthenticator ?? '').trim();
      if (totp !== '' && !/^\d{6}$/.test(totp)) {
        this.errors.totp = MESSAGE.TOTP_FORMAT;
        this.shakeElement(this.$refs.boxTotp);
        this.focusField('totp');
        return false;
      }
      return true;
    },

    showWrongCredentials() {
      this.loginFailed = true;
      this.validateLogin = MESSAGE.WRONG_CREDENTIALS;
      this.password = "";
      this.showPassword = false;
      this.shakeElement(this.$refs.boxAdminId);
      this.shakeElement(this.$refs.boxPassword);
    },

    /* ------------------------------------------------------------
       ĐĂNG NHẬP
       ------------------------------------------------------------ */
    async navigateToMainPage() {
      //save path to local storage
      const routerManagement = new RouterMgmt();

      const firstPathAfterLogin = "/information-admin";
      routerManagement.savePathToLocalStorage(firstPathAfterLogin);

      try {
        await this.$router.push({path: firstPathAfterLogin});
      } catch (error) {
        console.error('Error navigating :', error);
        this.validateLogin = MESSAGE.NAVIGATE_ERROR;
      }
    },

    // Bọc request: quá hạn thì báo lỗi để form không bị khoá mãi
    withTimeout(promise, ms) {
      let timer;
      const timeout = new Promise((_, reject) => {
        timer = setTimeout(() => reject(new Error('Login request timeout')), ms);
      });
      return Promise.race([promise, timeout]).finally(() => clearTimeout(timer));
    },

    /* ------------------------------------------------------------
       KHÓA FORM KHI NHẬP SAI KHÓA 6 SỐ QUÁ NHIỀU LẦN
       Server trả LOCKED + retryAfterSeconds -> khóa cả form, đếm ngược,
       hết giờ thì tự mở lại. Đếm theo mốc thời gian (không cộng dồn từng giây)
       nên tab bị trình duyệt làm chậm vẫn đúng giờ.
       ------------------------------------------------------------ */
    startLock(seconds) {
      const total = Math.max(1, Math.ceil(Number(seconds) || LOCK_DEFAULT_SECONDS));
      this.lockUntil = Date.now() + total * 1000;
      this.lockRemainingSeconds = total;
      this.persistLock();
      this.runLockTimer();
    },

    runLockTimer() {
      this.stopLockTimer();
      this.lockTimer = setInterval(this.onLockTick, 1000);
    },

    stopLockTimer() {
      if (this.lockTimer !== null) {
        clearInterval(this.lockTimer);
        this.lockTimer = null;
      }
    },

    onLockTick() {
      const remaining = Math.ceil((this.lockUntil - Date.now()) / 1000);
      if (remaining <= 0) {
        this.releaseLock();
      } else {
        this.lockRemainingSeconds = remaining;
      }
    },

    // Quay lại tab: cập nhật ngay, không chờ nhịp 1 giây kế tiếp
    onLockVisibilityChange() {
      if (document.visibilityState === 'visible' && this.lockUntil > 0) {
        this.onLockTick();
      }
    },

    // Hết giờ: đưa form về trạng thái bình thường để nhập lại
    releaseLock() {
      this.stopLockTimer();
      this.lockUntil = 0;
      this.lockRemainingSeconds = 0;
      this.clearPersistedLock();

      this.validateLogin = "";
      this.errors.totp = "";
      this.keyAuthenticator = "";
      this.focusField('totp');
    },

    persistLock() {
      try {
        localStorage.setItem(LOCK_STORAGE_KEY, JSON.stringify({
          adminId: this.adminId ? String(this.adminId) : null,
          until: this.lockUntil,
        }));
      } catch (e) {
        // Trình duyệt chặn lưu trữ: bỏ qua, đếm ngược vẫn chạy trong trang
      }
    },

    clearPersistedLock() {
      try {
        localStorage.removeItem(LOCK_STORAGE_KEY);
      } catch (e) {
        // bỏ qua
      }
    },

    // Tải lại trang giữa lúc đang khóa: dựng lại đếm ngược
    restoreLock() {
      try {
        const raw = localStorage.getItem(LOCK_STORAGE_KEY);
        if (!raw) return;
        const saved = JSON.parse(raw);
        const until = Number(saved?.until) || 0;
        const remaining = Math.ceil((until - Date.now()) / 1000);
        if (remaining > 0) {
          if (!this.adminId && saved.adminId) this.adminId = saved.adminId;
          this.lockUntil = until;
          this.lockRemainingSeconds = remaining;
          this.runLockTimer();
        } else {
          this.clearPersistedLock();
        }
      } catch (e) {
        this.clearPersistedLock();
      }
    },

    async handleLoginEvent() {
      // Chốt chặn: đang đăng nhập hoặc đang bị khóa thì bỏ qua mọi lần bấm / Enter thêm
      if (this.isSubmitting || this.isLocked) return;

      this.errors.adminId = "";
      this.errors.password = "";
      this.errors.totp = "";
      this.loginFailed = false;
      this.validateLogin = "";

      if (!this.validateForm()) return;

      this.isSubmitting = true; // khoá cả form + bật hiệu ứng load
      let fieldToFocus = null;

      try {
        const adminRepository = new AdminRepository();
        const result = await this.withTimeout(
            adminRepository.getLoginResult(
                Number(this.adminId),
                String(this.password),
                String(this.keyAuthenticator ?? '').trim() || null // trống -> null (chưa cài 2FA)
            ),
            LOGIN_TIMEOUT_MS
        );

        switch (result.getStatus()) {
          case REQUEST_STATUS.OK:
            this.handleRememberMe();
            //dang nhap thanh cong, dieu huong qua (đợi chuyển trang xong mới mở khoá form)
            await this.navigateToMainPage();
            break;

          case REQUEST_STATUS.TOTP_REQUIRED:
            // Mật khẩu đúng + tài khoản đã bật 2FA nhưng ô khóa 6 số đang trống -> báo bắt buộc nhập
            this.errors.totp = MESSAGE.TOTP_REQUIRED;
            this.shakeElement(this.$refs.boxTotp);
            fieldToFocus = 'totp';
            break;

          case REQUEST_STATUS.TOTP_INVALID:
            this.errors.totp = MESSAGE.TOTP_INVALID;
            this.keyAuthenticator = "";
            this.shakeElement(this.$refs.boxTotp);
            fieldToFocus = 'totp';
            break;

          case REQUEST_STATUS.LOCKED:
            // Khóa cả form + đếm ngược theo giây server trả về; hết giờ tự mở lại
            this.keyAuthenticator = "";
            this.errors.totp = "";
            this.validateLogin = "";
            this.startLock(result.getRetryAfterSeconds());
            break;

          case REQUEST_STATUS.FAILED:
            this.keyAuthenticator = "";
            this.showWrongCredentials();
            fieldToFocus = 'password';
            break;

          default:
            // Server trả về trạng thái lạ (lỗi 5xx, mất kết nối...)
            this.validateLogin = MESSAGE.SERVER_ERROR;
        }
      } catch (error) {
        console.error('Login error:', error);
        this.validateLogin = MESSAGE.SERVER_ERROR;
      } finally {
        // Dù thành công, sai mật khẩu hay lỗi mạng: luôn trả form về trạng thái bình thường
        this.isSubmitting = false;
      }

      if (fieldToFocus) this.focusField(fieldToFocus);
    },

    /* ------------------------------------------------------------
       GHI NHỚ ĐĂNG NHẬP
       ------------------------------------------------------------ */
    handleRememberMe() {
      if (this.rememberMe === true) {
        this.saveDataInputToLocalStorage();
      } else {
        this.removeDataInputFromLocalStorage();
      }
    },

    removeDataInputFromLocalStorage() {
      const adminLocalStorage = new AdminStorage();
      adminLocalStorage.removeLocalStorageRememberMe();
    },

    saveDataInputToLocalStorage() {
      if (this.adminId && this.password) {
        const adminLocalStorage = new AdminStorage();
        adminLocalStorage.saveLocalStorageRememberMe(
            this.adminId,
            this.password.trim());
      }
    },

    getStatusCheckBoxRememberMe() {
      /* neu local storage có save thì true ngược lại thì false */
      /* neu tru thi set input*/
      const adminLocalStorage = new AdminStorage();
      let adminFetched = adminLocalStorage
          .getLocalStorageRememberMe();
      if (!adminFetched || (
          typeof adminFetched === 'object'
          && Object.keys(adminFetched).length === 0)) {
        this.rememberMe = false;
      } else {
        this.rememberMe = true;
        this.setDataInputFromLocalStorage(adminFetched);
      }
    },

    setDataInputFromLocalStorage(adminFetched) {
      if (adminFetched) {
        let adminId = adminFetched.adminId;
        let password = adminFetched.password;
        const adminAccount = new AdminAccount(adminId, password);
        this.adminId = adminAccount.getAdminId();
        this.password = adminAccount.getPassword();
      }
    }
  },
}
</script>

<template>
  <form class="container-page-login" novalidate @submit.prevent="handleLoginEvent">
    <Header/>
    <section class="container-form-login-page">
      <div class="form-login" :class="{ 'is-loading': isSubmitting, 'is-locked': isLocked }" :aria-busy="isSubmitting">
        <h4 class="title-login">ĐĂNG NHẬP</h4>

        <!-- fieldset[disabled] khoá cùng lúc mọi ô nhập, checkbox và nút bên trong
             (đang đăng nhập, hoặc đang bị khóa vì nhập sai khóa 6 số quá nhiều lần) -->
        <fieldset class="form-fieldset" :disabled="isSubmitting || isLocked">
          <div class="form-fields">

            <!-- Mã quản trị viên -->
            <div class="field-group">
              <div ref="boxAdminId"
                   class="form-floating style-input-login"
                   :class="{ 'has-error': invalidAdminId }">
                <input type="text" class="form-control border-radius-zero"
                       id="floatingInputAdminId"
                       ref="inputAdminId"
                       placeholder="Mã quản trị viên (nhập số)"
                       inputmode="numeric"
                       autocomplete="username"
                       maxlength="10"
                       :aria-invalid="invalidAdminId"
                       :aria-describedby="errors.adminId ? 'errorAdminId' : null"
                       @input="setInputAdminId"
                       v-model="adminId">
                <label for="floatingInputAdminId">Mã quản trị viên (nhập số)</label>
              </div>
              <Transition name="error-fade">
                <p v-if="errors.adminId" id="errorAdminId" class="field-error" role="alert">
                  {{ errors.adminId }}
                </p>
              </Transition>
            </div>

            <!-- Mật khẩu -->
            <div class="field-group">
              <div ref="boxPassword"
                   class="form-floating style-input-login has-toggle"
                   :class="{ 'has-error': invalidPassword }">
                <input :type="showPassword ? 'text' : 'password'"
                       class="form-control border-radius-zero"
                       id="floatingInputPassword"
                       ref="inputPassword"
                       placeholder="Mật khẩu"
                       autocomplete="current-password"
                       maxlength="20"
                       :aria-invalid="invalidPassword"
                       :aria-describedby="errors.password ? 'errorPassword' : null"
                       @input="setInputPassword"
                       @paste="preventPaste($event)"
                       v-model="password">
                <label for="floatingInputPassword">Mật khẩu</label>
                <button type="button" class="btn-toggle-password"
                        :aria-label="showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'"
                        :aria-pressed="showPassword"
                        @click="togglePassword">
                  <svg v-if="!showPassword" viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                    <circle cx="12" cy="12" r="3"/>
                  </svg>
                  <svg v-else viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                    <line x1="1" y1="1" x2="23" y2="23"/>
                  </svg>
                </button>
              </div>
              <Transition name="error-fade">
                <p v-if="errors.password" id="errorPassword" class="field-error" role="alert">
                  {{ errors.password }}
                </p>
              </Transition>
            </div>

            <!-- Khóa 6 số xác thực: luôn hiện.
                 Đã cài 2FA -> bắt buộc nhập (server kiểm tra). Chưa cài 2FA -> để trống vẫn đăng nhập được. -->
            <div class="field-group">
              <div ref="boxTotp"
                   class="form-floating style-input-login"
                   :class="{ 'has-error': !!errors.totp }">
                <input type="text" class="form-control border-radius-zero"
                       id="floatingInputKeyAuthenticator"
                       ref="inputTotp"
                       placeholder="Khóa 6 số xác thực (để trống nếu chưa cài 2FA)"
                       inputmode="numeric"
                       autocomplete="one-time-code"
                       maxlength="6"
                       :aria-invalid="!!errors.totp"
                       :aria-describedby="errors.totp ? 'errorTotp' : null"
                       @input="setInputKeyAuthenticator"
                       v-model="keyAuthenticator">
                <label for="floatingInputKeyAuthenticator">Khóa 6 số xác thực (để trống nếu chưa cài 2FA)</label>
              </div>
              <Transition name="error-fade">
                <p v-if="errors.totp" id="errorTotp" class="field-error" role="alert">
                  {{ errors.totp }}
                </p>
              </Transition>
            </div>

            <!-- Lỗi chung của form -->
            <Transition name="error-fade">
              <div v-if="validateLogin" class="form-error-banner" role="alert">
                {{ validateLogin }}
              </div>
            </Transition>

            <!-- Tạm khóa: đếm ngược, hết giờ tự mở lại form -->
            <Transition name="error-fade">
              <div v-if="isLocked" class="form-error-banner form-lock-banner" role="alert">
                <span>{{ lockMessage }}</span>
                <strong class="lock-countdown" aria-hidden="true">{{ lockCountdownLabel }}</strong>
              </div>
            </Transition>

            <div class="box-remember-me">
              <input type="checkbox" id="rememberMe" v-model="rememberMe" class="style-checkbox"/>
              <label for="rememberMe">Ghi nhớ đăng nhập</label>
            </div>

            <!-- Nếu ButtonRed render <button type="submit"> thì có thể xoá @click (form @submit đã gọi rồi) -->
            <ButtonRed class="btn-login btn-login-border-radius"
                       @click="handleLoginEvent()"
                       :text-button="isSubmitting ? '' : (isLocked ? 'Thử lại sau ' + lockCountdownLabel : 'Đăng nhập')"
                       :loading-button="isSubmitting"
                       :disable-button="isSubmitting || isLocked"
            />
          </div>
        </fieldset>

        <!-- Đọc trạng thái cho trình đọc màn hình -->
        <span class="visually-hidden" role="status" aria-live="polite">
          {{ isSubmitting ? 'Đang đăng nhập...' : (isLocked ? 'Biểu mẫu đang tạm khóa do nhập sai quá nhiều lần.' : '') }}
        </span>
      </div>
    </section>
    <Footer/>
  </form>

</template>

<style scoped lang="scss">

//.container-page-login {
//  border: solid;
//}

.border-radius-zero {
  border-radius: 0;
}

.btn-login-border-radius {
  border-radius: 4px;
}
</style>