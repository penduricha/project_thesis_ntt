<script>
import './modal-activate-2fa.scss';
// npm install @phosphor-icons/vue --legacy-peer-deps
import { PhCopy } from '@phosphor-icons/vue'

export default {
  name: "ModalActivate2FA",
  components: {
    PhCopy
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
          <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="Đóng"></button>
        </div>

        <!-- Body Modal -->
        <div class="modal-body border-radius-zero">
          <p class="modal-2fa__desc text-muted small">
            Sao chép chuỗi khóa bí mật dưới đây và dán vào ứng dụng <strong>Mobile Authenticator</strong> của bạn để bắt đầu sinh mã.
          </p>

          <!-- Khóa Bí Mật (Secret Key) -->
          <div class="modal-2fa__field">
            <label for="secretKey2fa" class="form-label fw-bold small color-text-label">KHÓA BÍ MẬT (SECRET KEY):</label>
            <div class="input-group">
              <input
                  id="secretKey2fa"
                  type="text"
                  class="form-control modal-2fa__secret font-monospace bg-light text-dark border-radius-zero"
                  value="JBSWY3DPEHPK3PXP"
                  readonly
                  autocomplete="off"
                  spellcheck="false"
                  @focus="$event.target.select()"
              >
              <button
                  class="btn btn-outline-primary border-primary text-custom-blue border-radius-zero btn-copy"
                  type="button"
                  aria-label="Sao chép khóa bí mật"
                  title="Sao chép"
              >
                <PhCopy :size="20" aria-hidden="true" />
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

        <!-- Footer Modal: không viền, chỉ có nút Kích hoạt (kích thước do SCSS điều khiển theo thiết bị) -->
        <div class="modal-footer border-0 bg-transparent justify-content-center border-radius-zero">
          <button type="button" class="btn btn-custom-success fw-semibold btn-submit border-radius-zero">
            Kích hoạt
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">

</style>