<script>
import './modal-activate-2fa.scss';
// npm install @phosphor-icons/vue --legacy-peer-deps
import { PhCopy, PhCheck, PhCheckCircle, PhWarningCircle } from '@phosphor-icons/vue'
// import ButtonRed from "@/components/button/button_red/ButtonRed.vue";

const FEEDBACK_DURATION = 3000 // ms: thời gian hiện alert + icon tích

export default {
  name: "ModalActivate2FA",
  components: {
    PhCopy,
    PhCheck,
    PhCheckCircle,
    PhWarningCircle
  },

  data() {
    return {
      copied: false, // true => nút đổi sang icon tích trắng (nền --blue-color)
      alert: { show: false, type: 'success', message: '' },
      feedbackTimer: null,

      //btn activate
      textButtonActivate: 'Kích hoạt',
      disableButtonActivate: false,
      loadingButtonActivate: false,
    }
  },

  beforeUnmount() {
    clearTimeout(this.feedbackTimer)
  },

  methods: {
    async copySecret() {
      const input = this.$refs.secretInput
      const text = input.value

      // try {
      //   if (navigator.clipboard && window.isSecureContext) {
      //     // Cách hiện đại (cần HTTPS hoặc localhost)
      //     await navigator.clipboard.writeText(text)
      //   } else {
      //     // Dự phòng cho HTTP / trình duyệt cũ: chọn chính ô input rồi copy.
      //     // Không tạo <textarea> ngoài modal vì focus trap của Bootstrap sẽ chặn focus.
      //     input.focus()
      //     input.select()
      //     input.setSelectionRange(0, text.length) // iOS Safari
      //     if (!document.execCommand('copy')) throw new Error('execCommand copy failed')
      //   }
      //   this.showFeedback(true, 'Đã sao chép khóa bí mật!')
      // } catch (error) {
      //   console.error('Copy secret key failed:', error)
      //   this.showFeedback(false, 'Không thể sao chép, vui lòng sao chép thủ công.')
      // }
      try {
        if (!navigator.clipboard) throw new Error('Clipboard API unavailable')
        await navigator.clipboard.writeText(text)
        this.showFeedback(true, 'Đã sao chép khóa bí mật!')
      } catch (error) {
        console.error('Copy secret key failed:', error)
        this.showFeedback(false, 'Không thể sao chép, vui lòng sao chép thủ công.')
      }
    },

    // Bật trạng thái loading cho nút Kích hoạt:
    // khóa nút, ẩn chữ (template sẽ hiện spinner thay cho chữ)
    loadButtonActivate() {
      this.loadingButtonActivate = true
      this.disableButtonActivate = true
      this.textButtonActivate = ''
    },

    // Tắt trạng thái loading, khôi phục chữ "Kích hoạt"
    stopLoadButtonActivate() {
      this.loadingButtonActivate = false
      this.disableButtonActivate = false
      this.textButtonActivate = 'Kích hoạt'
    },

    async handleActivate2FAEvent() {
      // if (this.loadingButtonActivate) return // chặn bấm liên tục khi đang xử lý
      //
      // this.loadButtonActivate();
      // try {
      //   // TODO: gọi API kích hoạt 2FA ở đây, ví dụ:
      //   // await activate2FA({ secret: this.$refs.secretInput.value, interval: ... })
      //   // Muốn test hiệu ứng loading: await new Promise(resolve => setTimeout(resolve, 2000))
      // } catch (error) {
      //   console.error('Activate 2FA failed:', error)
      // } finally {
      //   this.stopLoadButtonActivate() // luôn tắt loading dù thành công hay lỗi
      // }
    },

    // Hiện alert + (nếu thành công) đổi icon, rồi tự khôi phục sau FEEDBACK_DURATION.
    // Bấm liên tục sẽ reset lại bộ đếm 3 giây.
    showFeedback(success, message) {
      clearTimeout(this.feedbackTimer)
      this.copied = success
      this.alert = { show: true, type: success ? 'success' : 'danger', message }

      this.feedbackTimer = setTimeout(() => {
        this.copied = false
        this.alert.show = false // giữ nguyên message để hiệu ứng mờ dần không bị mất chữ
      }, FEEDBACK_DURATION)
    }
  }
}
</script>

<template>
  <div class="modal fade modal-2fa" id="setup2FaModal" tabindex="-1" aria-labelledby="setup2FaModalLabel" aria-hidden="true">
    <!-- modal-dialog-scrollable: màn hình thấp (xoay ngang) thì body cuộn, header/footer cố định -->
    <div class="modal-dialog modal-dialog-centered modal-dialog-scrollable">
      <div class="modal-content shadow border-radius-zero">
        <!-- Header Modal -->
        <div class="modal-header bg-custom-blue text-white border-radius-zero">
          <h5 class="modal-title fw-bold" id="setup2FaModalLabel">
            <i class="fa-solid fa-key me-1" aria-hidden="true"></i> Cài đặt xác thực 2 lớp 2FA
          </h5>
          <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="Đóng"/>
        </div>

        <!-- Body Modal -->
        <div class="modal-body border-radius-zero">
          <!-- Đã bỏ class "small": cỡ chữ do biến --m2fa-text-size trong SCSS điều khiển theo thiết bị -->
          <p class="modal-2fa__desc text-muted">
            Sao chép chuỗi khóa bí mật dưới đây và dán vào ứng dụng di động <strong>Authenticator</strong> của để bắt đầu sinh mã 6 số.
          </p>

          <!-- Khóa Bí Mật (Secret Key) -->
          <div class="modal-2fa__field">
            <label for="secretKey2fa" class="form-label fw-bold small color-text-label">KHÓA BÍ MẬT (SECRET KEY):</label>
            <div class="input-group">
              <input
                  id="secretKey2fa"
                  ref="secretInput"
                  type="text"
                  class="form-control modal-2fa__secret font-monospace bg-light text-dark border-radius-zero"
                  value="JBSWY3DPEHPK3PXP"
                  readonly
                  autocomplete="off"
                  spellcheck="false"
                  @focus="$event.target.select()"
              >
              <!-- Khi đã copy: bỏ các class outline-primary để nền --blue-color + icon trắng hiển thị đúng -->
              <button
                  class="btn border-radius-zero btn-copy"
                  :class="copied ? 'is-copied' : 'btn-outline-primary border-primary text-custom-blue'"
                  type="button"
                  :aria-label="copied ? 'Đã sao chép' : 'Sao chép khóa bí mật'"
                  :title="copied ? 'Đã sao chép' : 'Sao chép'"
                  @click="copySecret"
              >
                <PhCheck v-if="copied" :size="20" weight="bold" color="#fff" aria-hidden="true" />
                <PhCopy v-else :size="20" aria-hidden="true" />
              </button>
            </div>
          </div>

          <!-- Tùy chọn tần suất thời gian (Time Interval) -->
          <div class="modal-2fa__field">
            <label for="interval2fa" class="form-label fw-bold small color-text-label">TẦN SUẤT ĐỔI MÃ (INTERVAL):</label>
            <select id="interval2fa" class="form-select border-radius-zero" aria-describedby="interval2faHelp">
              <option value="15">15 giây</option>
              <option value="30" selected>30 giây</option>
              <option value="45">45 giây</option>
              <option value="60">60 giây</option>
            </select>
            <div id="interval2faHelp" class="form-text text-muted">Lựa chọn thời gian đếm ngược đổi mã OTP trên App di động.</div>
          </div>
        </div>

        <!-- Footer Modal: không viền, chỉ có nút Kích hoạt.
             Nút là <button> thường (class "btn-activate"), KHÔNG dùng .btn của Bootstrap
             nên trạng thái loading/disabled hoàn toàn do method + SCSS riêng điều khiển. -->
        <div class="modal-footer border-0 bg-transparent justify-content-center border-radius-zero">
          <button
              type="button"
              class="btn-activate"
              :class="{ 'is-loading': loadingButtonActivate }"
              :disabled="disableButtonActivate"
              :aria-busy="loadingButtonActivate"
              :aria-label="loadingButtonActivate ? 'Đang xử lý' : null"
              @click="handleActivate2FAEvent"
          >
            <span v-if="loadingButtonActivate" class="btn-activate__spinner" aria-hidden="true"></span>
            <span v-if="textButtonActivate">{{ textButtonActivate }}</span>
          </button>
        </div>
      </div>
    </div>

    <!--
      Alert thông báo sao chép: Teleport ra <body> để luôn cố định ở góc phải trên viewport,
      nằm trên modal + backdrop. Vùng aria-live luôn tồn tại để trình đọc màn hình đọc được nội dung.
      Thành công: dùng class riêng "copy-alert--success" (nền --blue-color) thay cho "alert-success" của Bootstrap.
      Lỗi: giữ "alert-danger" (đỏ) để phân biệt rõ trạng thái cảnh báo.
    -->
    <Teleport to="body">
      <div class="copy-alert-container" aria-live="polite" aria-atomic="true">
        <Transition name="copy-alert">
          <div
              v-if="alert.show"
              class="copy-alert alert"
              :class="alert.type === 'success' ? 'copy-alert--success' : 'alert-danger'"
          >
            <PhCheckCircle v-if="alert.type === 'success'" :size="20" weight="fill" aria-hidden="true" />
            <PhWarningCircle v-else :size="20" weight="fill" aria-hidden="true" />
            <span>{{ alert.message }}</span>
          </div>
        </Transition>
      </div>
    </Teleport>
  </div>
</template>