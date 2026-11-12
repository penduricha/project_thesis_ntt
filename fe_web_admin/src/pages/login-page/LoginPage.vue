<script>
import './login-page.scss';
import '@/assets/style/theme-color.scss';
import RouterMgmt from "@/router/RouterMgmt.js";
import ButtonRed from "@/components/button/button_red/ButtonRed.vue";

import AdminStorage from "@/local_storage/AdminStorage.js";
import {AdminAccount} from "@/model/AdminAccount.js";
import AdminRepository from "@/repository/AdminRepository.js";
import Footer from "@/components/footer/Footer.vue";
import Header from "@/components/header/Header.vue";

/* Thông báo hiển thị cho người dùng: gom 1 chỗ để dễ sửa câu chữ */
const MESSAGE = {
  ADMIN_ID_REQUIRED: 'Vui lòng nhập mã quản trị viên.',
  PASSWORD_REQUIRED: 'Vui lòng nhập mật khẩu.',
  WRONG_CREDENTIALS: 'Mã quản trị viên hoặc mật khẩu không đúng. Vui lòng kiểm tra lại.',
  SERVER_ERROR: 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra mạng rồi thử lại.',
  NAVIGATE_ERROR: 'Đăng nhập thành công nhưng không thể chuyển trang. Vui lòng tải lại trang.',
};

/* Quá thời gian này mà server chưa trả lời thì mở khoá form, tránh bị treo vô hạn */
const LOGIN_TIMEOUT_MS = 20000;

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
  },

  mounted() {
    // Chỉ tự focus trên máy có chuột/bàn phím.
    // Trên điện thoại không tự focus để bàn phím ảo không bật lên bất ngờ.
    const isDesktopLike = window.matchMedia?.('(hover: hover) and (pointer: fine)').matches;
    if (isDesktopLike) {
      this.focusField(this.adminId ? 'password' : 'adminId');
    }
  },

  data() {
    return {
      adminId: null,
      password: "",
      rememberMe: false,
      keyAuthenticator: null,
      showPassword: false,

      // true từ lúc bấm đăng nhập đến lúc có kết quả: khoá cả form + hiện hiệu ứng load
      isSubmitting: false,

      // lỗi theo từng ô nhập (hiện ngay dưới ô)
      errors: {
        adminId: "",
        password: "",
      },

      // lỗi chung của form (sai tài khoản/mật khẩu, lỗi server...) hiện phía trên ô "Ghi nhớ"
      validateLogin: "",
      // true khi server báo sai mã/mật khẩu -> tô đỏ cả 2 ô
      loginFailed: false,
    }
  },

  computed: {
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
      // Chỉ cho phép các ký tự số
      // Gán lại giá trị cho biến adminId
      this.keyAuthenticator = event.target.value.replace(/[^0-9]/g, '');

      if (!this.keyAuthenticator) {
        this.validateLogin = "";
      } else {
        this.validateLogin = "";
      }
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
      const input = field === 'adminId' ? this.$refs.inputAdminId : this.$refs.inputPassword;
      input?.focus();
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

    async handleLoginEvent() {
      // Chốt chặn: đang đăng nhập thì bỏ qua mọi lần bấm / Enter thêm
      if (this.isSubmitting) return;

      this.errors.adminId = "";
      this.errors.password = "";
      this.loginFailed = false;
      this.validateLogin = "";

      if (!this.validateForm()) return;

      this.isSubmitting = true; // khoá cả form + bật hiệu ứng load
      let fieldToFocus = null;

      try {
        const adminRepository = new AdminRepository();
        const statusLogin = await this.withTimeout(
            adminRepository.getResponseFromLoginRequest(Number(this.adminId), String(this.password)),
            LOGIN_TIMEOUT_MS
        );

        if (statusLogin === "OK") {
          this.handleRememberMe();
          //dang nhap thanh cong, dieu huong qua (đợi chuyển trang xong mới mở khoá form)
          await this.navigateToMainPage();
        } else if (statusLogin === "Failed") {
          this.showWrongCredentials();
          fieldToFocus = 'password';
        } else {
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
      <div class="form-login" :class="{ 'is-loading': isSubmitting }" :aria-busy="isSubmitting">
        <h4 class="title-login">ĐĂNG NHẬP</h4>

        <!-- fieldset[disabled] khoá cùng lúc mọi ô nhập, checkbox và nút bên trong -->
        <fieldset class="form-fieldset" :disabled="isSubmitting">
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

            <!-- Khóa 6 số xác thực: giữ nguyên sự kiện, chỉ đồng bộ style với các ô trên -->
            <div class="form-floating style-input-login">
              <input type="text" class="form-control border-radius-zero"
                     id="floatingInputKeyAuthenticator"
                     placeholder="Khóa 6 số xác thực"
                     @input="setInputKeyAuthenticator"
                     v-model="keyAuthenticator" maxlength="6">
              <label for="floatingInputKeyAuthenticator">Khóa 6 số xác thực</label>
            </div>

            <!-- Lỗi chung của form -->
            <Transition name="error-fade">
              <div v-if="validateLogin" class="form-error-banner" role="alert">
                {{ validateLogin }}
              </div>
            </Transition>

            <div class="box-remember-me">
              <input type="checkbox" id="rememberMe" v-model="rememberMe" class="style-checkbox"/>
              <label for="rememberMe">Ghi nhớ đăng nhập</label>
            </div>

            <!-- Nếu ButtonRed render <button type="submit"> thì có thể xoá @click (form @submit đã gọi rồi) -->
            <ButtonRed class="btn-login btn-login-border-radius"
                       @click="handleLoginEvent()"
                       :text-button="isSubmitting ? '' : 'Đăng nhập'"
                       :loading-button="isSubmitting"
                       :disable-button="isSubmitting"
            />
          </div>
        </fieldset>

        <!-- Đọc trạng thái cho trình đọc màn hình -->
        <span class="visually-hidden" role="status" aria-live="polite">
          {{ isSubmitting ? 'Đang đăng nhập...' : '' }}
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