<script>
import './modal-activate-2fa.scss';
// npm install @phosphor-icons/vue --legacy-peer-deps
import { PhCopy, PhCheck, PhCheckCircle, PhWarningCircle } from '@phosphor-icons/vue'
import { TokenLogin } from '@/model/TokenLogin.js'
import { EnrollmentRepository } from '@/repository/EnrollmentRepository.js'
// import ButtonRed from "@/components/button/button_red/ButtonRed.vue";

const FEEDBACK_DURATION = 3000 // ms: thời gian hiện alert + icon tích

export default {
  name: "ModalActivate2FA",

  // activated   : đã ghi xong, đang CHỜ mobile xác nhận -> CardSettingTwoFA chuyển sang khung vàng
  // busy-change : true khi bắt đầu gọi API "Kích hoạt", false khi xong -> khóa / mở tương tác cả trang
  emits: ['activated', 'busy-change'],

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

      // Khóa Base32 do Spring Boot sinh (lưu ở Redis: EnrollSession). Rỗng = chưa có khóa.
      secretKey: '',
      loadingSecretKey: false,
      secretKeyError: '',
      intervalSeconds: 30, // khớp ô chọn "Tần suất đổi mã" (15 | 30 | 45 | 60)

      // Bước hiển thị: 'key' = khóa bí mật | 'backup' = 10 mã dự phòng (sau khi bấm Kích hoạt thành công)
      step: 'key',
      backupCodes: [],

      //btn activate
      textButtonActivate: 'Kích hoạt',
      disableButtonActivate: false,
      loadingButtonActivate: false,
    }
  },

  // ================= LIFECYCLE =================

  // created: chưa có DOM. Tạo các đối tượng dùng chung. Các biến dưới đây KHÔNG đặt trong data()
  // vì chỉ là cờ nội bộ / đối tượng gọi API, không cần Vue theo dõi để vẽ lại giao diện.
  created() {
    this.enrollmentRepository = new EnrollmentRepository()
    this.tokenLogin = new TokenLogin() // tạo giống cách bạn đang tạo ở nơi khác
    this.isModalOpen = false
    this.startRequestId = 0 // đánh số mỗi lần xin khóa để bỏ kết quả của lần đã cũ
  },

  // mounted: DOM đã có. Lắng nghe sự kiện của Bootstrap Modal (bắn trên chính phần tử modal):
  //  - show.bs.modal : modal BẮT ĐẦU mở (nút "Kích hoạt 2FA" ở InformationPage bấm vào)
  //  - hide.bs.modal : modal BẮT ĐẦU đóng, dù đóng bằng nút X, bấm ra ngoài hay phím Esc
  //  - pagehide      : tắt tab / đóng trình duyệt
  mounted() {
    this.modalElement = this.$el
    this.modalElement.addEventListener('show.bs.modal', this.handleModalShow)
    this.modalElement.addEventListener('hide.bs.modal', this.handleModalHide)
    window.addEventListener('pagehide', this.handlePageHide)
  },

  // beforeUnmount: chuyển sang trang khác trong app (component bị gỡ) -> dọn listener
  // và xóa khóa nếu modal đang mở.
  beforeUnmount() {
    clearTimeout(this.feedbackTimer)

    this.modalElement?.removeEventListener('show.bs.modal', this.handleModalShow)
    this.modalElement?.removeEventListener('hide.bs.modal', this.handleModalHide)
    window.removeEventListener('pagehide', this.handlePageHide)

    this.releaseSecretKey()
  },

  methods: {
    // ===== Khóa bí mật: xin khi mở modal, xóa khi đóng / rời đi =====

    handleModalShow() {
      this.isModalOpen = true
      this.step = 'key'
      this.backupCodes = []
      this.requestSecretKey()
    },

    // Bootstrap cho phép HỦY việc đóng bằng preventDefault().
    // Đang gọi API "Kích hoạt" thì KHÔNG cho đóng (nút X, bấm ra ngoài, Esc): nếu đóng giữa chừng,
    // handler bên dưới sẽ xóa khóa ở Redis và lệnh kích hoạt đang bay sẽ thất bại (410).
    handleModalHide(event) {
      if (this.loadingButtonActivate) {
        event.preventDefault()
        return
      }
      this.releaseSecretKey()
      // Mã dự phòng chỉ hiển thị một lần: đóng modal là xóa khỏi bộ nhớ trình duyệt
      this.backupCodes = []
      this.step = 'key'
    },

    // Tắt tab: yêu cầu fetch keepalive vẫn được gửi dù trang đang đóng
    handlePageHide() {
      if (!this.isModalOpen) return
      this.isModalOpen = false
      this.enrollmentRepository.cancelEnrollmentOnPageHide(this.tokenLogin)
    },

    // Gọi Spring Boot sinh khóa Base32 mới (cũng dùng cho nút "Thử lại")
    async requestSecretKey() {
      const requestId = ++this.startRequestId
      this.secretKey = ''
      this.secretKeyError = ''
      this.loadingSecretKey = true

      const result = await this.enrollmentRepository
          .startEnrollment(this.tokenLogin, this.intervalSeconds)

      // Lần xin này đã cũ (modal đã đóng hoặc mở lại trong lúc chờ API): bỏ kết quả.
      // Nếu server vừa tạo khóa mà modal đã đóng thì xóa ngay, không để khóa mồ côi trên Redis.
      if (requestId !== this.startRequestId) {
        if (result.success && !this.isModalOpen) {
          await this.enrollmentRepository.cancelEnrollment(this.tokenLogin)
        }
        return
      }

      this.loadingSecretKey = false

      if (!result.success) {
        this.secretKeyError = result.message
        this.showFeedback(false, result.message)
        return
      }
      this.secretKey = result.data.secretKey
    },

    // Modal đóng (mọi cách) hoặc component bị gỡ: xóa khóa trong bộ nhớ trình duyệt
    // và báo Spring Boot xóa khóa trên Redis. Không chờ kết quả (có TTL 5 phút dự phòng).
    releaseSecretKey() {
      const wasOpen = this.isModalOpen
      this.isModalOpen = false
      this.startRequestId++ // vô hiệu hóa lần xin khóa đang chờ (nếu có)

      this.secretKey = ''
      this.secretKeyError = ''
      this.loadingSecretKey = false

      if (wasOpen) {
        this.enrollmentRepository.cancelEnrollment(this.tokenLogin)
      }
    },

    async copySecret() {
      const input = this.$refs.secretInput
      const text = input.value
      if (!text) return // chưa có khóa thì không có gì để sao chép

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
      this.$emit('busy-change', true) // khóa tương tác cả trang
    },

    // Tắt trạng thái loading, khôi phục chữ "Kích hoạt"
    stopLoadButtonActivate() {
      this.loadingButtonActivate = false
      this.disableButtonActivate = false
      this.textButtonActivate = 'Kích hoạt'
      this.$emit('busy-change', false) // mở lại tương tác, dù thành công hay lỗi
    },

    // Bấm "Kích hoạt": ghi khóa xuống MariaDB ở trạng thái CHỜ XÁC NHẬN.
    // Gửi lên server: chu kỳ đang chọn (15/30/45/60). adminId lấy từ token; khóa bí mật server đã giữ
    // ở Redis từ lúc mở modal nên KHÔNG gửi lại khóa qua mạng lần nữa.
    // 2FA CHƯA bật thật: chỉ bật khi app Authenticator trên điện thoại gửi đúng mã 6 số đầu tiên.
    async handleActivate2FAEvent() {
      if (this.loadingButtonActivate || !this.secretKey) return // chặn bấm liên tục / chưa có khóa

      this.loadButtonActivate()
      try {
        const result = await this.enrollmentRepository
            .activateEnrollment(this.tokenLogin, this.intervalSeconds)

        if (!result.success) {
          this.showFeedback(false, result.message)
          // Khóa chờ ở Redis đã hết hạn (5 phút): xin khóa mới để admin thử lại
          if (result.expired) await this.requestSecretKey()
          return
        }

        // Thành công: khóa đã nằm ở MariaDB (đã mã hóa), Redis đã bị server xóa.
        // Hạ cờ isModalOpen để lúc đóng modal không gọi DELETE thừa; xóa khóa khỏi bộ nhớ trang.
        this.isModalOpen = false
        this.startRequestId++
        this.secretKey = ''
        this.backupCodes = result.data.backupCodes
        this.step = 'backup'

        this.$emit('activated', {
          expiresInSeconds: result.data.expiresInSeconds,
          periodSeconds: result.data.periodSeconds,
        })
      } catch (error) {
        console.error('Activate 2FA failed:', error)
        this.showFeedback(false, 'Không thể kích hoạt. Vui lòng thử lại.')
      } finally {
        this.stopLoadButtonActivate() // luôn mở lại tương tác dù thành công hay lỗi
      }
    },

    async copyBackupCodes() {
      try {
        if (!navigator.clipboard) throw new Error('Clipboard API unavailable')
        await navigator.clipboard.writeText(this.backupCodes.join('\n'))
        this.showFeedback(true, 'Đã sao chép mã dự phòng!')
      } catch (error) {
        console.error('Copy backup codes failed:', error)
        this.showFeedback(false, 'Không thể sao chép, vui lòng chép thủ công.')
      }
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
      <!-- Đang gọi API Kích hoạt: inert = chặn cả chuột lẫn bàn phím trong modal -->
      <div class="modal-content shadow border-radius-zero"
           :inert="loadingButtonActivate ? '' : null"
           :aria-busy="loadingButtonActivate">
        <!-- Header Modal -->
        <div class="modal-header bg-custom-blue text-white border-radius-zero">
          <h5 class="modal-title fw-bold" id="setup2FaModalLabel">
            <i class="fa-solid fa-key me-1" aria-hidden="true"></i> Cài đặt xác thực 2 lớp 2FA
          </h5>
          <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="Đóng"
                  :disabled="loadingButtonActivate"/>
        </div>

        <!-- ===== Bước 1: khóa bí mật ===== -->
        <div v-if="step === 'key'" class="modal-body border-radius-zero">
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
                  :value="secretKey"
                  :placeholder="loadingSecretKey ? 'Đang tạo khóa...' : (secretKeyError ? 'Không thể tạo khóa' : '')"
                  :aria-busy="loadingSecretKey"
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
                  :disabled="!secretKey"
                  :aria-label="copied ? 'Đã sao chép' : 'Sao chép khóa bí mật'"
                  :title="copied ? 'Đã sao chép' : 'Sao chép'"
                  @click="copySecret"
              >
                <PhCheck v-if="copied" :size="20" weight="bold" color="#fff" aria-hidden="true" />
                <PhCopy v-else :size="20" aria-hidden="true" />
              </button>
            </div>
            <!-- Lỗi tạo khóa (mất mạng, hết phiên...): cho thử lại ngay trong modal -->
            <button
                v-if="secretKeyError"
                type="button"
                class="btn btn-link p-0 mt-1 small"
                @click="requestSecretKey"
            >Thử lại</button>
          </div>

          <!-- Tùy chọn tần suất thời gian (Time Interval) -->
          <div class="modal-2fa__field">
            <label for="interval2fa" class="form-label fw-bold small color-text-label">TẦN SUẤT ĐỔI MÃ (INTERVAL):</label>
            <select id="interval2fa" v-model.number="intervalSeconds" class="form-select border-radius-zero" aria-describedby="interval2faHelp">
              <option value="15">15 giây</option>
              <option value="30">30 giây</option>
              <option value="45">45 giây</option>
              <option value="60">60 giây</option>
            </select>
            <div id="interval2faHelp" class="form-text text-muted">Lựa chọn thời gian đếm ngược đổi mã OTP trên App di động.</div>
          </div>
        </div>

        <!-- Footer Modal: không viền, chỉ có nút Kích hoạt.
             Nút là <button> thường (class "btn-activate"), KHÔNG dùng .btn của Bootstrap
             nên trạng thái loading/disabled hoàn toàn do method + SCSS riêng điều khiển. -->
        <div v-if="step === 'key'" class="modal-footer border-0 bg-transparent justify-content-center border-radius-zero">
          <button
              type="button"
              class="btn-activate"
              :class="{ 'is-loading': loadingButtonActivate }"
              :disabled="disableButtonActivate || !secretKey"
              :aria-busy="loadingButtonActivate"
              :aria-label="loadingButtonActivate ? 'Đang xử lý' : null"
              @click="handleActivate2FAEvent"
          >
            <span v-if="loadingButtonActivate" class="btn-activate__spinner" aria-hidden="true"></span>
            <span v-if="textButtonActivate">{{ textButtonActivate }}</span>
          </button>
        </div>

        <!-- ===== Bước 2: mã dự phòng (chỉ hiển thị một lần) ===== -->
        <div v-if="step === 'backup'" class="modal-body border-radius-zero">
          <p class="modal-2fa__desc text-muted">
            Đã tạo khóa. Hãy <strong>dán khóa vào ứng dụng Authenticator</strong> trên điện thoại và bấm
            <strong>Activate</strong> để hoàn tất trong vòng 10 phút. Lưu 10 mã dự phòng dưới đây ở nơi an toàn:
            chúng <strong>chỉ hiển thị một lần</strong> và dùng khi bạn mất điện thoại.
          </p>
          <ul class="backup-codes" aria-label="Mã dự phòng">
            <li v-for="code in backupCodes" :key="code" class="backup-codes__item font-monospace">{{ code }}</li>
          </ul>
        </div>
        <!-- 2 nút cùng width + height, căn giữa modal. KHÔNG dùng .btn của Bootstrap (xem .m2fa-btn trong SCSS) -->
        <div v-if="step === 'backup'" class="modal-footer m2fa-footer-pair border-0 bg-transparent">
          <button type="button" class="m2fa-btn m2fa-btn--outline" @click="copyBackupCodes">
            Sao chép mã dự phòng
          </button>
          <button type="button" class="m2fa-btn m2fa-btn--solid" data-bs-dismiss="modal">
            Đã lưu, đóng
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