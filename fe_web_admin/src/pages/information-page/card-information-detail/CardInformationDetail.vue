<script>
import './card-information-detail.scss';
import AdminRepository from "@/repository/AdminRepository.js";

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
  name: "CardInformationDetail",

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

      // Vòng đời
      isUnmounted: false,   // bỏ qua kết quả API trả về sau khi component đã bị gỡ
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
  },

  // ================= LIFECYCLE (theo đúng thứ tự chạy) =================

  // 1. created: chưa có DOM.
  created() {
    this.isUnmounted = false;
  },

  // 2. mounted: DOM đã render -> gọi API lấy dữ liệu.
  async mounted() {
    await this.fetchDataAdminInformationDetail();
  },

  // 3. beforeUnmount: dọn dẹp mọi thứ đã tạo ở trên.
  beforeUnmount() {
    // Hủy timer tự ẩn CCCD
    clearTimeout(this.idCardTimer);
    this.idCardTimer = null;

    // Xóa số CCCD khỏi bộ nhớ component khi rời trang
    this.isIdCardVisible = false;
    this.idCard = null;
  },

  // 4. unmounted: đánh dấu để các Promise còn treo không ghi vào state nữa.
  unmounted() {
    this.isUnmounted = true;
  },

  // =====================================================================

  methods: {
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
    },
  },
};
</script>

<template>
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
</template>