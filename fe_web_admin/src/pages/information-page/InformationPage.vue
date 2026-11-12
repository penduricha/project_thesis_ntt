<script>
import './information-page.scss';
import './event-text-2fa.scss';
import './two-fa-pending.scss';
import RouterMgmt from "@/router/RouterMgmt.js";
import Header from "../../components/header/Header.vue";
import Footer from "../../components/footer/Footer.vue";

import '@/pages/information-page/card-account/card-account.scss';
import '@/assets/style/theme-color.scss';
import CardAccount from "@/pages/information-page/card-account/CardAccount.vue";
import AdminRepository from "@/repository/AdminRepository.js";
import TextRed from "@/components/span/TextRed.vue";
import TextGreen from "@/components/span/TextGreen.vue";
import ButtonBlue from "@/components/button/button_blue/ButtonBlue.vue";
import ModalActivate2FA from "@/pages/information-page/pop-up-activate-2fa/ModalActivate2FA.vue";

// Mapping hiển thị tiếng Việt cho dữ liệu backend trả về bằng tiếng Anh.
// Giá trị chưa có trong bảng sẽ hiển thị nguyên bản. Bổ sung theo dữ liệu thực tế.
const TRAINING_SYSTEM_LABELS = {
  Master: 'Thạc sĩ',
  Bachelor: 'Đại học',
  Doctor: 'Tiến sĩ',
  PhD: 'Tiến sĩ',
};

const TYPE_OF_TRAINING_LABELS = {
  Research: 'Nghiên cứu',
  Application: 'Ứng dụng',
};

const PAGE_TITLE = 'Thông tin quản trị';

// Tự ẩn lại CCCD sau 30 giây để tránh để lộ khi quên che màn hình
const ID_CARD_AUTO_HIDE_MS = 30000;

// ===== Trạng thái 2FA: chuỗi khớp 1-1 với giá trị backend trả về (GET /web/admin/2fa/status) =====
//  NOT_ENABLED: chưa có credential, hoặc credential chờ xác nhận đã quá hạn
//  PENDING:     đã bấm "Kích hoạt" trên web, mobile CHƯA xác nhận (totp_enabled = false)
//  ENABLED:     mobile đã xác nhận mã đầu tiên (totp_enabled = true)
const TWO_FA_STATUS = Object.freeze({
  NOT_ENABLED: 'NOT_ENABLED',
  PENDING: 'PENDING',
  ENABLED: 'ENABLED',
});

// Mọi thứ phụ thuộc trạng thái gom vào một chỗ để template không phải rải ternary.
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

// Khi đang chờ xác nhận: cứ mỗi N giây hỏi lại server để tự chuyển sang "Đã kích hoạt".
const PENDING_POLL_SECONDS = 5;

export default {
  name: "InformationPage",

  components: {
    ButtonBlue,
    TextGreen,
    TextRed,
    CardAccount,
    Header,
    Footer,
    ModalActivate2FA,
  },

  data() {
    return {
      // Admin Info
      adminId: null,
      address: null,
      idCard: null,
      isIdCardVisible: false,
      idCardTimer: null,

      // University
      industry: null,
      faculty: null,
      branch: null,
      typeOfTraining: null,
      trainingSystem: null,
      major: null,
      yearOpening: null,

      // 2FA
      // 2FA
      twoFaStatus: 'PENDING',          // đổi từ null sang 'PENDING'
      pendingRemainingSeconds: 600,    // 600 giây = hiện "Còn 10:00"
      //twoFaStatus: null,             // null = đang tải; còn lại là một giá trị của TWO_FA_STATUS
      //pendingRemainingSeconds: 0,    // thời gian còn lại của trạng thái chờ (server gửi, FE đếm ngược)
      isResettingPending: false,     // đang gọi API "Hủy và cài lại"
      twoFaActionError: null,        // lỗi hiển thị dưới mô tả khi hủy thất bại
      twoFaTimer: null,              // setInterval 1 giây (đếm ngược + poll)
      twoFaTickCount: 0,
      twoFaFetchSeq: 0,              // bỏ qua phản hồi cũ khi có nhiều request chồng nhau

      // Vòng đời
      previousTitle: null,  // tiêu đề tab trước khi vào trang, để khôi phục khi rời
      isUnmounted: false,   // bỏ qua kết quả API trả về sau khi trang đã bị gỡ
    }
  },

  computed: {
    displayIdCard() {
      if (!this.idCard) return '—';
      return this.isIdCardVisible ? this.idCard : this.maskIdCard(this.idCard);
    },

    trainingSystemLabel() {
      return TRAINING_SYSTEM_LABELS[this.trainingSystem] || this.trainingSystem || '—';
    },

    typeOfTrainingLabel() {
      return TYPE_OF_TRAINING_LABELS[this.typeOfTraining] || this.typeOfTraining || '—';
    },

    // ----- 2FA -----
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

    // 600 giây -> "10:00"
    pendingCountdownLabel() {
      const total = Math.max(0, this.pendingRemainingSeconds);
      const minutes = String(Math.floor(total / 60)).padStart(2, '0');
      const seconds = String(total % 60).padStart(2, '0');
      return `${minutes}:${seconds}`;
    },
  },

  // ================= LIFECYCLE (theo đúng thứ tự chạy) =================

  // 1. created: data/computed/methods đã sẵn sàng, CHƯA có DOM.
  //    Chỉ làm việc không cần DOM: ghi lại đường dẫn route hiện tại.
  created() {
    this.isUnmounted = false;
    this.saveRouterPath();
  },

  // 2. mounted: DOM đã render xong.
  //    Làm việc chạm vào DOM (document.title) rồi gọi API lấy dữ liệu.
  //    Hai API độc lập nên gọi song song.
  async mounted() {
    this.setTitlePage();
    await Promise.all([
      this.fetchDataAdminInformationDetail(),
      this.fetchTwoFaStatus(),
    ]);
  },

  // 3. beforeUnmount: instance còn nguyên vẹn -> dọn dẹp mọi thứ đã tạo ở trên.
  //    (Vue 3: đây là tên mới của beforeDestroy)
  beforeUnmount() {
    // Hủy timer tự ẩn CCCD
    clearTimeout(this.idCardTimer);
    this.idCardTimer = null;

    // Hủy timer đếm ngược / poll trạng thái 2FA
    this.stopTwoFaTimer();

    // Xóa số CCCD khỏi bộ nhớ component khi rời trang
    this.isIdCardVisible = false;
    this.idCard = null;

    // Khôi phục tiêu đề tab. Đặt ở beforeUnmount (chạy đồng bộ lúc gỡ)
    // để không ghi đè tiêu đề mà trang kế tiếp đặt trong mounted của nó.
    this.restoreTitlePage();
  },

  // 4. unmounted: đã gỡ xong -> đánh dấu để các Promise còn treo không ghi vào state nữa.
  unmounted() {
    this.isUnmounted = true;
  },

  // =====================================================================

  methods: {
    setTitlePage() {
      this.previousTitle = document.title;
      document.title = PAGE_TITLE;
    },

    restoreTitlePage() {
      if (this.previousTitle !== null) {
        document.title = this.previousTitle;
        this.previousTitle = null;
      }
    },

    saveRouterPath() {
      const routePath = this.$route.path;
      const routerManagement = new RouterMgmt();
      routerManagement.savePathToSessionStorage(routePath);
    },

    async fetchDataAdminInformationDetail() {
      try {
        const adminRepository = new AdminRepository();
        let adminInformationDetail = await adminRepository.getAdminInformationDetailByAdminId();

        // Người dùng đã rời trang trong lúc chờ API: bỏ qua kết quả
        if (this.isUnmounted) return;

        if (adminInformationDetail) {
          this.address = adminInformationDetail.address;
          this.idCard = adminInformationDetail.idCard;
          this.industry = adminInformationDetail.industry;
          this.faculty = adminInformationDetail.faculty;

          this.trainingSystem = adminInformationDetail.trainingSystem;
          this.typeOfTraining = adminInformationDetail.typeOfTraining;
          this.major = adminInformationDetail.major;
          this.branch = adminInformationDetail.branch;
        }
      } catch (error) {
        console.error('Error to fetch admin information detail:', error);
      }
    },

    // ================= 2FA =================

    // Hỏi server trạng thái hiện tại. Server là nguồn sự thật: tải lại trang vẫn đúng.
    // Kỳ vọng AdminRepository.getTwoFaStatus() trả về { status, pendingExpiresInSeconds }.
    async fetchTwoFaStatus() {
      const seq = ++this.twoFaFetchSeq;
      try {
        const adminRepository = new AdminRepository();
        const result = await adminRepository.getTwoFaStatus();

        // Rời trang, hoặc đã có request mới hơn: bỏ phản hồi này
        if (this.isUnmounted || seq !== this.twoFaFetchSeq) return;
        this.applyTwoFaStatus(result);
      } catch (error) {
        console.error('Error to fetch 2FA status:', error);
        if (this.isUnmounted || seq !== this.twoFaFetchSeq) return;
        // Lần tải đầu mà lỗi: coi như chưa bật (nút Kích hoạt vẫn dùng được, server sẽ kiểm tra lại).
        // Đang poll mà lỗi mạng thoáng qua: giữ nguyên trạng thái đang hiển thị.
        if (this.twoFaStatus === null) {
          this.twoFaStatus = TWO_FA_STATUS.NOT_ENABLED;
        }
      }
    },

    applyTwoFaStatus(result) {
      const status = result?.status;
      this.twoFaStatus = Object.values(TWO_FA_STATUS).includes(status)
          ? status
          : TWO_FA_STATUS.NOT_ENABLED;

      const expiresIn = Number(result?.pendingExpiresInSeconds);
      this.pendingRemainingSeconds = expiresIn > 0 ? Math.floor(expiresIn) : 0;

      this.syncTwoFaTimer();
    },

    // Chỉ chạy timer khi đang PENDING; các trạng thái khác thì dừng.
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

      // Hết hạn: hỏi ngay để chuyển về "Chưa kích hoạt".
      // Đến lượt poll: chỉ hỏi khi tab đang hiện, tránh gọi API vô ích ở tab nền.
      if (justExpired || (isPollTurn && !document.hidden)) {
        this.fetchTwoFaStatus();
      }
    },

    // "Hủy và cài lại": xóa yêu cầu đang chờ rồi mở lại modal để sinh khóa mới.
    // Kỳ vọng AdminRepository.cancelPendingTwoFa() gọi DELETE /web/admin/2fa/enrollment/pending.
    async onResetPending() {
      if (this.isResettingPending) return;
      this.isResettingPending = true;
      this.twoFaActionError = null;

      try {
        const adminRepository = new AdminRepository();
        await adminRepository.cancelPendingTwoFa();
        if (this.isUnmounted) return;

        this.applyTwoFaStatus({ status: TWO_FA_STATUS.NOT_ENABLED });

        // Mở lại modal bằng nút ẩn (dùng đúng data-bs-toggle như nút "Kích hoạt 2FA")
        await this.$nextTick();
        this.$refs.setup2FaTrigger?.click();
      } catch (error) {
        console.error('Error to cancel pending 2FA enrollment:', error);
        if (!this.isUnmounted) {
          this.twoFaActionError = 'Không thể hủy yêu cầu hiện tại. Vui lòng thử lại.';
        }
      } finally {
        this.isResettingPending = false;
      }
    },

    // =======================================

    // Che số CCCD: 060203000127 -> 060203••••27
    // visibleStart / visibleEnd: số ký tự giữ lại ở đầu / cuối.
    // Muốn kín hơn, đặt visibleStart = 0 và visibleEnd = 4 (chỉ lộ 4 số cuối).
    maskIdCard(value, visibleStart = 6, visibleEnd = 2) {
      if (!value) return '—';
      const text = String(value);
      if (text.length <= visibleStart + visibleEnd) {
        return '•'.repeat(text.length);
      }
      const hiddenLength = text.length - visibleStart - visibleEnd;
      return text.slice(0, visibleStart) + '•'.repeat(hiddenLength) + text.slice(-visibleEnd);
    },

    // Nút hiện / ẩn CCCD
    toggleIdCard() {
      this.isIdCardVisible = !this.isIdCardVisible;
      clearTimeout(this.idCardTimer);

      if (this.isIdCardVisible) {
        this.idCardTimer = setTimeout(() => {
          this.isIdCardVisible = false;
        }, ID_CARD_AUTO_HIDE_MS);
      }
    }
  }
}
</script>

<template>
  <Header/>
  <section class="section-page-information">
    <CardAccountNav/>

    <!-- ===== Thông tin quản trị viên ===== -->
    <article class="info-card" aria-labelledby="admin-info-title">
      <div class="info-card__header">
        <span class="info-card__icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
               stroke-linecap="round" stroke-linejoin="round">
            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
            <circle cx="12" cy="7" r="4"/>
          </svg>
        </span>
        <h3 id="admin-info-title" class="info-card__title">Thông tin quản trị viên</h3>
      </div>

      <div class="info-group">
        <h4 class="info-group__title">Thông tin cá nhân</h4>
        <dl class="info-grid">
          <div class="info-field">
            <dt class="info-field__label">Địa chỉ</dt>
            <dd class="info-field__value">{{ address || '—' }}</dd>
          </div>

          <div class="info-field">
            <dt class="info-field__label">Căn cước công dân</dt>
            <dd class="info-field__value info-field__value--secret">
              <span class="info-field__secret-text" aria-live="polite">{{ displayIdCard }}</span>
              <button v-if="idCard"
                      type="button"
                      class="icon-btn"
                      :aria-label="isIdCardVisible ? 'Ẩn số căn cước công dân' : 'Hiện số căn cước công dân'"
                      :title="isIdCardVisible ? 'Ẩn' : 'Hiện'"
                      @click="toggleIdCard">
                <!-- Mắt mở: đang che, bấm để hiện -->
                <svg v-if="!isIdCardVisible" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                     stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"/>
                  <circle cx="12" cy="12" r="3"/>
                </svg>
                <!-- Mắt gạch: đang hiện, bấm để che -->
                <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor"
                     stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M17.94 17.94A10.07 10.07 0 0 1 12 19c-6.5 0-10-7-10-7a18.5 18.5 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c6.5 0 10 7 10 7a18.5 18.5 0 0 1-2.16 3.19M14.12 14.12a3 3 0 1 1-4.24-4.24"/>
                  <line x1="1" y1="1" x2="23" y2="23"/>
                </svg>
              </button>
            </dd>
          </div>
        </dl>
      </div>

      <div class="info-group">
        <h4 class="info-group__title">Thông tin đào tạo</h4>
        <dl class="info-grid info-grid--3">
          <div class="info-field">
            <dt class="info-field__label">Khoa</dt>
            <dd class="info-field__value">{{ faculty || '—' }}</dd>
          </div>
          <div class="info-field">
            <dt class="info-field__label">Ngành</dt>
            <dd class="info-field__value">{{ industry || '—' }}</dd>
          </div>
          <div class="info-field">
            <dt class="info-field__label">Chuyên ngành</dt>
            <dd class="info-field__value">{{ major || '—' }}</dd>
          </div>
          <div class="info-field">
            <dt class="info-field__label">Hệ đào tạo</dt>
            <dd class="info-field__value">{{ trainingSystemLabel }}</dd>
          </div>
          <div class="info-field">
            <dt class="info-field__label">Loại hình đào tạo</dt>
            <dd class="info-field__value">{{ typeOfTrainingLabel }}</dd>
          </div>
          <div class="info-field">
            <dt class="info-field__label">Cơ sở</dt>
            <dd class="info-field__value">{{ branch || '—' }}</dd>
          </div>
        </dl>
      </div>
    </article>

    <!-- ===== Xác thực hai yếu tố ===== -->
    <article class="info-card" aria-labelledby="two_fa-title">
      <div class="info-card__header">
        <span class="info-card__icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
               stroke-linecap="round" stroke-linejoin="round">
            <rect x="3" y="11" width="18" height="11" rx="2"/>
            <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
          </svg>
        </span>
        <h3 id="two_fa-title" class="info-card__title">Xác thực hai yếu tố</h3>
      </div>

      <!-- Đang hỏi server: chưa biết trạng thái thì không hiện đỏ/vàng/xanh để tránh báo sai -->
      <div v-if="isTwoFaLoading" class="two_fa two_fa--loading" role="status" aria-busy="true">
        <span class="two_fa__spinner" aria-hidden="true"></span>
        <p class="two_fa__desc">Đang kiểm tra trạng thái bảo mật…</p>
      </div>

      <!-- Ba trạng thái: NOT_ENABLED (đỏ) | PENDING (vàng) | ENABLED (xanh) -->
      <div v-else class="two_fa" :class="twoFaView.stateClass">
        <div class="two_fa__main">
          <span class="two_fa__icon" aria-hidden="true">
            <!-- Đã bật: khiên + dấu tick -->
            <svg v-if="isTwoFaEnabled" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
              <polyline points="9 12 11 14 15 10"/>
            </svg>
            <!-- Đang chờ: đồng hồ -->
            <svg v-else-if="isTwoFaPending" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="10"/>
              <polyline points="12 6 12 12 16 14"/>
            </svg>
            <!-- Chưa bật: khiên + chấm than -->
            <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
              <line x1="12" y1="8" x2="12" y2="12"/>
              <line x1="12" y1="16" x2="12.01" y2="16"/>
            </svg>
          </span>

          <div class="two_fa__content">
            <!-- role="status": trình đọc màn hình tự đọc lại khi trạng thái đổi.
                 Đếm ngược để NGOÀI vùng này, nếu không sẽ bị đọc lại mỗi giây. -->
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

            <!-- Chỉ ở trạng thái chờ: báo hạn còn lại + đăng nhập chưa bị yêu cầu mã -->
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

        <!-- Đang chờ xác nhận: hủy yêu cầu hiện tại rồi cài lại từ đầu -->
        <button
            v-else-if="isTwoFaPending"
            type="button"
            class="two_fa__btn-reset"
            :disabled="isResettingPending"
            @click="onResetPending"
        >
          <span v-if="isResettingPending" class="two_fa__spinner" aria-hidden="true"></span>
          {{ isResettingPending ? '' : 'Hủy và cài lại' }}
        </button>

        <!-- Đã bật: không có nút (tránh bấm thừa) -->
      </div>

      <!-- Nút ẩn: để onResetPending() mở lại modal qua data-bs-toggle, không cần import bootstrap -->
      <button
          ref="setup2FaTrigger"
          type="button"
          hidden
          tabindex="-1"
          aria-hidden="true"
          data-bs-toggle="modal"
          data-bs-target="#setup2FaModal"
      ></button>
    </article>
  </section>
  <Footer/>
  <!-- Modal cần $emit('activated') sau khi gọi API kích hoạt thành công, để trang chuyển sang trạng thái vàng -->
  <ModalActivate2FA @activated="fetchTwoFaStatus"/>
</template>