<script>
import './information-page.scss';
import './event-text-2fa.scss';
import RouterMgmt from "@/router/RouterMgmt.js";
import Header from "../../components/header/Header.vue";
import Footer from "../../components/footer/Footer.vue";

import './card-account-nav/card-account-nav.scss';
import '@/assets/style/theme-color.scss';
import CardAccountNav from "@/pages/information-page/card-account-nav/CardAccountNav.vue";
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

// Tự ẩn lại CCCD sau 30 giây để tránh để lộ khi quên che màn hình
const ID_CARD_AUTO_HIDE_MS = 30000;

export default {
  name: "InformationPage",

  components: {
    ButtonBlue,
    TextGreen,
    TextRed,
    CardAccountNav,
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

      // btn activate
      is2faEnabled: false,
    }
  },

  created() {
    this.saveRouterPath();
  },

  async mounted() {
    this.setTitlePage();
    await this.fetchDataAdminInformationDetail();
  },

  // Vue 3: beforeDestroy đã đổi tên thành beforeUnmount
  beforeUnmount() {
    clearTimeout(this.idCardTimer);
  },

  methods: {
    setTitlePage() {
      document.title = 'Thông tin quản trị';
    },

    saveRouterPath() {
      const routePath = this.$route.path;
      const routerManagement = new RouterMgmt();
      routerManagement.savePathToSessionStorage(routePath);
    },

    async fetchDataAdminInformationDetail() {
      const adminRepository = new AdminRepository();
      let adminInformationDetail = await adminRepository.getAdminInformationDetailByAdminId();
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
    },

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

    twoFAStateClass() {
      return this.is2faEnabled ? 'twofa--on' : 'twofa--off';
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
    <article class="info-card" aria-labelledby="twofa-title">
      <div class="info-card__header">
        <span class="info-card__icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
               stroke-linecap="round" stroke-linejoin="round">
            <rect x="3" y="11" width="18" height="11" rx="2"/>
            <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
          </svg>
        </span>
        <h3 id="twofa-title" class="info-card__title">Xác thực hai yếu tố</h3>
      </div>

      <div class="twofa" :class="twoFAStateClass">
        <div class="twofa__main">
          <span class="twofa__icon" aria-hidden="true">
            <svg v-if="is2faEnabled" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
              <polyline points="9 12 11 14 15 10"/>
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
              <line x1="12" y1="8" x2="12" y2="12"/>
              <line x1="12" y1="16" x2="12.01" y2="16"/>
            </svg>
          </span>

          <div class="twofa__content">
            <!-- role="status": trình đọc màn hình tự đọc lại khi trạng thái đổi -->
            <p class="twofa__status" role="status">
              <span>Trạng thái:</span>
              <span class="ui-badge" :class="is2faEnabled ? 'ui-badge--success' : 'ui-badge--danger'">
                <svg v-if="is2faEnabled" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                     stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
                  <polyline points="22 4 12 14.01 9 11.01"/>
                </svg>
                <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor"
                     stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
                  <line x1="12" y1="9" x2="12" y2="13"/>
                  <line x1="12" y1="17" x2="12.01" y2="17"/>
                </svg>
                {{ is2faEnabled ? 'Đã kích hoạt' : 'Chưa kích hoạt' }}
              </span>
            </p>
            <p class="twofa__desc">
              {{ is2faEnabled
                ? 'Tài khoản của bạn đang được bảo vệ bằng mã xác thực từ thiết bị.'
                : 'Tài khoản quản trị có quyền truy cập dữ liệu nhạy cảm. Bật xác thực hai yếu tố để chặn truy cập trái phép ngay cả khi mật khẩu bị lộ.' }}
            </p>
          </div>
        </div>

        <!-- Đã bật rồi thì ẩn nút kích hoạt (tránh bấm thừa) -->
        <ButtonBlue
            v-if="!is2faEnabled"
            data-bs-toggle="modal"
            data-bs-target="#setup2FaModal"
            class="style-btn-activate"
            :disable-button="false"
            :loading-button="false"
            text-button="Kích hoạt 2FA"
        />
      </div>
    </article>
  </section>
  <Footer/>
  <ModalActivate2FA/>
</template>