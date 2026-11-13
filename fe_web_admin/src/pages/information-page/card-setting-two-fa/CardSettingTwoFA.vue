<script>
import './card-setting-two-fa.scss';
import './two-fa-lock.scss';
import { TokenLogin } from '@/model/TokenLogin.js';
import { EnrollmentRepository } from '@/repository/EnrollmentRepository.js';
import ButtonBlue from "@/components/button/button_blue/ButtonBlue.vue";
import ModalActivate2FA from "@/pages/information-page/pop-up-activate-2fa/ModalActivate2FA.vue";

// Chuỗi khớp 1-1 với backend (GET /web/admin/2fa/status)
const TWO_FA_STATUS = Object.freeze({
  NOT_ENABLED: 'NOT_ENABLED', // chưa bật (hoặc chờ xác nhận đã quá hạn)
  PENDING: 'PENDING',         // đã tạo khóa, mobile chưa xác nhận
  ENABLED: 'ENABLED',         // đã xác nhận xong
});

// Mọi thứ phụ thuộc trạng thái gom vào một chỗ để template không phải rải ternary
const TWO_FA_VIEW = Object.freeze({
  [TWO_FA_STATUS.NOT_ENABLED]: {
    stateClass: 'two_fa--off',
    badgeClass: 'ui-badge--danger',
    badgeText: 'Chưa kích hoạt',
    desc: 'Tài khoản quản trị có quyền truy cập dữ liệu nhạy cảm. Bật xác thực hai yếu tố để chặn truy cập trái phép ngay cả khi mật khẩu bị lộ.',
  },
  [TWO_FA_STATUS.PENDING]: {
    stateClass: 'two_fa--pending',
    badgeClass: 'ui-badge--warning',
    badgeText: 'Đang chờ xác nhận',
    desc: 'Khóa bí mật đã được tạo. Hãy dán khóa vào ứng dụng Authenticator trên điện thoại và xác nhận kích hoạt trong ứng dụng để hoàn tất.',
  },
  [TWO_FA_STATUS.ENABLED]: {
    stateClass: 'two_fa--on',
    badgeClass: 'ui-badge--success',
    badgeText: 'Đã kích hoạt',
    desc: 'Tài khoản của bạn đang được bảo vệ bằng mã xác thực từ thiết bị.',
  },
});

// Đang chờ mobile xác nhận: POLLING là cơ chế chính, cứ mỗi 3 giây hỏi lại GET /2fa/status
// cho tới khi server báo ENABLED (thẻ tự chuyển xanh) hoặc NOT_ENABLED (quá hạn / đã hủy).
// SSE chỉ là đường tắt để cập nhật sớm hơn; không phụ thuộc vào nó nên SSE hỏng cũng không bị kẹt ở màu vàng.
const PENDING_POLL_SECONDS = 3;

export default {
  name: "CardSettingTwoFA",

  components: { ButtonBlue, ModalActivate2FA },

  data() {
    return {
      // twoFaStatus: 'ENABLED',
      // pendingRemainingSeconds: 0,   // không dùng ở trạng thái này, để 0 cho sạch

      // twoFaStatus: 'PENDING',        // hoặc 'NOT_ENABLED' / 'ENABLED' / null (spinner)
      // pendingRemainingSeconds: 600,

      twoFaStatus: null,           // null = đang tải
      pendingRemainingSeconds: 0,  // server gửi, FE đếm ngược
      isPageLocked: false,         // đang gọi API "Kích hoạt" trong modal: cấm tương tác cả web
      isResetDialogClosing: false, // đang chạy animation đóng dialog xác nhận hủy
      isResettingPending: false,   // đang gọi API "Hủy và cài lại"
      twoFaActionError: null,
      twoFaTimer: null,
      twoFaTickCount: 0,
      twoFaFetchSeq: 0,            // bỏ phản hồi cũ khi có nhiều request chồng nhau
      isUnmounted: false,          // bỏ kết quả API về muộn sau khi component bị gỡ
    };
  },

  computed: {
    twoFaView() {
      return TWO_FA_VIEW[this.twoFaStatus] || null;
    },
    isTwoFaLoading() {
      return this.twoFaStatus === null;
    },
    isTwoFaNotEnabled() {
      return this.twoFaStatus === TWO_FA_STATUS.NOT_ENABLED;
    },
    isTwoFaPending() {
      return this.twoFaStatus === TWO_FA_STATUS.PENDING;
    },
    isTwoFaEnabled() {
      return this.twoFaStatus === TWO_FA_STATUS.ENABLED;
    },
    // 600 -> "10:00"
    pendingCountdownLabel() {
      const total = Math.max(0, this.pendingRemainingSeconds);
      const minutes = String(Math.floor(total / 60)).padStart(2, '0');
      const seconds = String(total % 60).padStart(2, '0');
      return `${minutes}:${seconds}`;
    },
  },

  created() {
    this.isUnmounted = false;

    // Không đặt trong data(): chỉ là đối tượng gọi API / cờ nội bộ, Vue không cần theo dõi
    this.enrollmentRepository = new EnrollmentRepository();
    this.tokenLogin = new TokenLogin();
    this.isExpiringPending = false; // chặn xử lý "hết 10 phút" chạy chồng
    this.stopWatchTwoFa = null;
  },

  async mounted() {
    await this.fetchTwoFaStatus();

    // Quay lại tab / cửa sổ (vừa xác nhận xong trên điện thoại): hỏi ngay, không chờ nhịp polling kế tiếp
    document.addEventListener('visibilitychange', this.onVisibilityChange);
    window.addEventListener('focus', this.onWindowFocus);

    // Lắng nghe server báo đổi trạng thái (mobile xác nhận, tab khác kích hoạt / hủy, quá hạn)
    this.stopWatchTwoFa = this.enrollmentRepository.watchTwoFaStatus(this.tokenLogin, {
      onChange: this.onTwoFaPushEvent,
    });
  },

  beforeUnmount() {
    document.removeEventListener('visibilitychange', this.onVisibilityChange);
    window.removeEventListener('focus', this.onWindowFocus);
    this.stopWatchTwoFa?.();
    this.stopWatchTwoFa = null;
    this.stopTwoFaTimer();

    // Dialog xác nhận hủy đang mở mà component bị gỡ thì đóng sạch để không kẹt backdrop / focus trap
    const resetDialog = this.$refs.resetDialog;
    if (resetDialog?.open) resetDialog.close();
  },

  unmounted() {
    this.isUnmounted = true;
  },

  methods: {
    // ===== API =====

    // EnrollmentRepository.getTwoFaStatus() trả { success, status, pendingExpiresInSeconds }
    async fetchTwoFaStatus() {
      const seq = ++this.twoFaFetchSeq;
      try {
        const result = await this.enrollmentRepository.getTwoFaStatus(this.tokenLogin);
        if (this.isUnmounted || seq !== this.twoFaFetchSeq) return;
        if (!result.success) throw new Error(result.message);

        const wasPending = this.twoFaStatus === TWO_FA_STATUS.PENDING;
        this.applyTwoFaStatus(result);

        // Đang chờ mà server báo "không còn chờ" (quá hạn 10 phút phía server): dọn nốt dòng chờ ở MariaDB
        if (wasPending && result.status === TWO_FA_STATUS.NOT_ENABLED) {
          this.enrollmentRepository.cancelPendingEnrollment(this.tokenLogin);
        }
      } catch (error) {
        console.error('Error to fetch 2FA status:', error);
        if (this.isUnmounted || seq !== this.twoFaFetchSeq) return;
        // Lần tải đầu lỗi: coi như chưa bật. Đang poll lỗi thoáng qua: giữ nguyên.
        if (this.twoFaStatus === null) {
          this.twoFaStatus = TWO_FA_STATUS.NOT_ENABLED;
        }
      }
    },

    // ----- "Hủy và cài lại": hộp thoại xác nhận (cùng kiểu hộp thoại Đăng xuất ở CardAccount.vue) -----
    openResetConfirm() {
      this.isResetDialogClosing = false;
      this.twoFaActionError = null;
      this.$refs.resetDialog?.showModal(); // animation mở chạy bằng CSS [open]
    },

    // Đóng có animation: thêm class .is-closing, chạy xong mới gọi close()
    closeResetConfirm() {
      if (this.isResettingPending) return; // đang hủy thì không cho đóng

      const dialog = this.$refs.resetDialog;
      if (!dialog || !dialog.open || this.isResetDialogClosing) return;

      // Người dùng tắt hiệu ứng chuyển động: đóng ngay, không chờ animation
      if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
        dialog.close();
        return;
      }
      this.isResetDialogClosing = true;
    },

    onResetDialogAnimationEnd(event) {
      const dialog = this.$refs.resetDialog;
      if (event.target !== dialog || !this.isResetDialogClosing) return;
      this.isResetDialogClosing = false;
      dialog.close();
    },

    // Phím Esc: chặn đóng đột ngột của trình duyệt, đi qua đường đóng có animation
    onResetDialogCancel(event) {
      event.preventDefault();
      this.closeResetConfirm();
    },

    // Bấm "Hủy kích hoạt" trong hộp thoại: xóa phần chờ xác nhận rồi về "Chưa kích hoạt".
    // Muốn cài lại thì bấm "Kích hoạt 2FA" để sinh khóa mới.
    async confirmResetPending() {
      if (this.isResettingPending) return;
      this.isResettingPending = true;
      this.twoFaActionError = null;

      try {
        const cancelled = await this.enrollmentRepository.cancelPendingEnrollment(this.tokenLogin);
        if (this.isUnmounted) return;

        if (cancelled) {
          this.applyTwoFaStatus({ status: TWO_FA_STATUS.NOT_ENABLED });
        } else {
          this.twoFaActionError = 'Không thể hủy yêu cầu hiện tại. Vui lòng thử lại.';
        }
      } catch (error) {
        console.error('Error to cancel pending 2FA enrollment:', error);
        if (!this.isUnmounted) {
          this.twoFaActionError = 'Không thể hủy yêu cầu hiện tại. Vui lòng thử lại.';
        }
      } finally {
        if (!this.isUnmounted) {
          this.isResettingPending = false;
          this.closeResetConfirm(); // đóng dialog dù thành công hay lỗi (lỗi hiện ngay trong khung 2FA)
        }
      }
    },

    // ===== Sự kiện đẩy từ server (SSE) =====

    // Chỉ dùng sự kiện như "tín hiệu": hỏi lại GET /2fa/status để lấy số liệu chuẩn (giây còn lại...).
    // fetchTwoFaStatus đã có số thứ tự yêu cầu nên kết quả về muộn không ghi đè kết quả mới hơn.
    onTwoFaPushEvent() {
      if (this.isUnmounted || this.isPageLocked) return; // đang gọi "Kích hoạt": modal sẽ tự hỏi lại khi xong
      this.fetchTwoFaStatus();
    },

    // Tab hiện lại / cửa sổ được focus: chỉ cần hỏi khi đang chờ xác nhận
    onVisibilityChange() {
      if (!document.hidden) this.refreshIfPending();
    },
    onWindowFocus() {
      this.refreshIfPending();
    },
    refreshIfPending() {
      if (this.isUnmounted || this.isPageLocked || !this.isTwoFaPending) return;
      this.fetchTwoFaStatus();
    },

    // ===== Trạng thái + timer =====

    applyTwoFaStatus(result) {
      const status = result?.status;
      this.twoFaStatus = Object.values(TWO_FA_STATUS).includes(status)
          ? status
          : TWO_FA_STATUS.NOT_ENABLED;

      const expiresIn = Number(result?.pendingExpiresInSeconds);
      this.pendingRemainingSeconds = expiresIn > 0 ? Math.floor(expiresIn) : 0;

      this.syncTwoFaTimer();
    },

    // Chỉ chạy timer khi đang PENDING
    syncTwoFaTimer() {
      if (this.isTwoFaPending) {
        if (!this.twoFaTimer) {
          this.twoFaTickCount = 0;
          this.twoFaTimer = setInterval(this.onTwoFaTick, 1000);
        }
      } else {
        this.stopTwoFaTimer();
      }
    },

    stopTwoFaTimer() {
      clearInterval(this.twoFaTimer);
      this.twoFaTimer = null;
      this.twoFaTickCount = 0;
    },

    onTwoFaTick() {
      const wasCounting = this.pendingRemainingSeconds > 0;
      if (wasCounting) this.pendingRemainingSeconds -= 1;
      this.twoFaTickCount += 1;

      const justExpired = wasCounting && this.pendingRemainingSeconds === 0;
      const isPollTurn = this.twoFaTickCount % PENDING_POLL_SECONDS === 0;

      // Hết 10 phút mà mobile chưa xác nhận: HỦY phần chờ rồi về "Chưa kích hoạt".
      // Đến lượt poll (mỗi 3 giây): chỉ hỏi khi tab đang hiện; tab hiện lại thì onVisibilityChange hỏi bù.
      if (justExpired) {
        this.handlePendingExpired();
      } else if (isPollTurn && !document.hidden) {
        this.fetchTwoFaStatus();
      }
    },

    // Hết giờ chờ xác nhận -> xóa credential chưa xác nhận ở server -> quay về NOT_ENABLED (khung đỏ)
    async handlePendingExpired() {
      if (this.isExpiringPending) return;
      this.isExpiringPending = true;
      this.stopTwoFaTimer(); // dừng đếm / poll trong lúc xử lý

      try {
        // Hỏi lần cuối: mobile có thể vừa xác nhận đúng lúc đồng hồ về 0
        const latest = await this.enrollmentRepository.getTwoFaStatus(this.tokenLogin);
        if (this.isUnmounted) return;
        if (latest.success && latest.status === TWO_FA_STATUS.ENABLED) {
          this.applyTwoFaStatus(latest);
          return;
        }

        await this.enrollmentRepository.cancelPendingEnrollment(this.tokenLogin);
        if (this.isUnmounted) return;
        this.applyTwoFaStatus({ status: TWO_FA_STATUS.NOT_ENABLED });
      } finally {
        this.isExpiringPending = false;
      }
    },
  },
};
</script>

<template>
  <article class="two-fa-card" aria-labelledby="two_fa-title">
    <div class="two-fa-card__header">
      <span class="two-fa-card__icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
             stroke-linecap="round" stroke-linejoin="round">
          <rect x="3" y="11" width="18" height="11" rx="2"/>
          <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
        </svg>
      </span>
      <h3 id="two_fa-title" class="two-fa-card__title">Xác thực hai yếu tố</h3>
    </div>

    <!-- Đang tải: chưa biết trạng thái thì không hiện đỏ/vàng/xanh -->
    <div v-if="isTwoFaLoading" class="two_fa two_fa--loading" role="status" aria-busy="true">
      <span class="two_fa__spinner" aria-hidden="true"></span>
      <p class="two_fa__desc">Đang kiểm tra trạng thái bảo mật…</p>
    </div>

    <!-- 3 trạng thái: NOT_ENABLED (đỏ) | PENDING (vàng) | ENABLED (xanh) -->
    <div v-else class="two_fa" :class="twoFaView.stateClass">
      <div class="two_fa__main">
        <span class="two_fa__icon" aria-hidden="true">
          <svg v-if="isTwoFaEnabled" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
            <polyline points="9 12 11 14 15 10"/>
          </svg>
          <svg v-else-if="isTwoFaPending" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"/>
            <polyline points="12 6 12 12 16 14"/>
          </svg>
          <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
            <line x1="12" y1="8" x2="12" y2="12"/>
            <line x1="12" y1="16" x2="12.01" y2="16"/>
          </svg>
        </span>

        <div class="two_fa__content">
          <!-- role="status" chỉ bọc badge; đếm ngược để NGOÀI vùng này kẻo bị đọc lại mỗi giây -->
          <p class="two_fa__status" role="status">
            <span>Trạng thái:</span>
            <span class="ui-badge" :class="twoFaView.badgeClass">
              <svg v-if="isTwoFaEnabled" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                   stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
                <polyline points="22 4 12 14.01 9 11.01"/>
              </svg>
              <svg v-else-if="isTwoFaPending" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                   stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <circle cx="12" cy="12" r="10"/>
                <polyline points="12 6 12 12 16 14"/>
              </svg>
              <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor"
                   stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
                <line x1="12" y1="9" x2="12" y2="13"/>
                <line x1="12" y1="17" x2="12.01" y2="17"/>
              </svg>
              {{ twoFaView.badgeText }}
            </span>
          </p>

          <p class="two_fa__desc">{{ twoFaView.desc }}</p>

          <p v-if="isTwoFaPending" class="two_fa__hint">
            <template v-if="pendingRemainingSeconds > 0">
              Còn <span class="two_fa__countdown">{{ pendingCountdownLabel }}</span> để hoàn tất.
            </template>
            Trong lúc chờ, đăng nhập chưa yêu cầu mã xác thực.
          </p>

          <p v-if="twoFaActionError" class="two_fa__error" role="alert">{{ twoFaActionError }}</p>
        </div>
      </div>

      <!-- Chưa bật: nút kích hoạt (mở modal) -->
      <ButtonBlue
          v-if="isTwoFaNotEnabled"
          data-bs-toggle="modal"
          data-bs-target="#setup2FaModal"
          class="style-btn-activate"
          :disable-button="false"
          :loading-button="false"
          text-button="Kích hoạt 2FA"
      />

      <!-- Đang chờ: hủy yêu cầu hiện tại rồi cài lại -->
      <button
          v-else-if="isTwoFaPending"
          type="button"
          class="two_fa__btn-reset"
          :disabled="isResettingPending"
          @click="openResetConfirm"
      >Hủy và cài lại</button>

      <!-- Đã bật: không có nút -->
    </div>

    <!-- Hộp thoại xác nhận hủy: Esc hoặc bấm ra ngoài vùng tối để đóng
         (style .confirm-dialog / .ui-btn dùng chung ở card-account.scss, đã được trang import) -->
    <dialog ref="resetDialog"
            class="confirm-dialog"
            :class="{ 'is-closing': isResetDialogClosing }"
            aria-labelledby="reset-2fa-dialog-title"
            @click.self="closeResetConfirm"
            @cancel="onResetDialogCancel"
            @animationend="onResetDialogAnimationEnd">
      <div class="confirm-dialog__panel">
        <h3 id="reset-2fa-dialog-title" class="confirm-dialog__title">Hủy kích hoạt xác thực hai yếu tố?</h3>
        <p class="confirm-dialog__text">
          Khóa bí mật hiện tại sẽ bị xóa. Nếu muốn cài lại, bạn cần tạo khóa mới và nhập lại vào ứng dụng Authenticator.
        </p>
        <div class="confirm-dialog__actions">
          <button type="button"
                  class="ui-btn ui-btn--ghost"
                  autofocus
                  :disabled="isResettingPending"
                  @click="closeResetConfirm">
            Quay lại
          </button>
          <button type="button"
                  class="ui-btn ui-btn--primary"
                  :disabled="isResettingPending"
                  @click="confirmResetPending">
            <span v-if="isResettingPending" class="ui-spinner" aria-hidden="true"></span>
            <span>{{ isResettingPending ? '' : 'Hủy kích hoạt' }}</span>
          </button>
        </div>
      </div>
    </dialog>
  </article>

  <!-- Đang gọi API "Kích hoạt": lớp phủ trong suốt phủ kín viewport chặn mọi thao tác chuột
       (Teleport ra body để không bị ngăn xếp z-index của trang đè). Bàn phím do `inert` trong modal chặn. -->
  <Teleport to="body">
    <div v-if="isPageLocked" class="two-fa-page-lock" aria-hidden="true"></div>
  </Teleport>

  <!-- activated: ghi xong, chờ mobile xác nhận -> hỏi lại trạng thái để chuyển sang vàng.
       busy-change: modal đang gọi API Kích hoạt -> khóa / mở tương tác cả trang. -->
  <ModalActivate2FA @activated="fetchTwoFaStatus" @busy-change="isPageLocked = $event"/>
</template>