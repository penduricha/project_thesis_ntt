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

// Hiển thị khi backend chưa có dữ liệu (rõ ràng hơn dấu "—")
const EMPTY_TEXT = 'Chưa cập nhật';

// Nét vẽ icon (bộ Feather, 24x24)
const ICONS = {
  user: 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0z',
  book: 'M2 4h6a4 4 0 0 1 4 4v13a3 3 0 0 0-3-3H2zM22 4h-6a4 4 0 0 0-4 4v13a3 3 0 0 1 3-3h7z',
  pin: 'M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0zM15 10a3 3 0 1 1-6 0 3 3 0 0 1 6 0z',
  card: 'M3 5h18a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2zM6 10h5M6 14h3M15 12a1.5 1.5 0 1 0 3 0 1.5 1.5 0 0 0-3 0z',
  eye: 'M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12zM15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0z',
  eyeOff: 'M17.94 17.94A10.07 10.07 0 0 1 12 19c-6.5 0-10-7-10-7a18.5 18.5 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c6.5 0 10 7 10 7a18.5 18.5 0 0 1-2.16 3.19M14.12 14.12a3 3 0 1 1-4.24-4.24M1 1l22 22',
  alert: 'M12 9v4M12 17h.01M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z',
};

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

      // Trạng thái tải dữ liệu
      isLoading: true,      // true: hiện skeleton thay cho giá trị
      isFetching: false,    // đang gọi API: chặn bấm "Thử lại" nhiều lần
      loadFailed: false,    // true: hiện khung lỗi + nút Thử lại

      // Dùng trong template
      icons: ICONS,
      emptyText: EMPTY_TEXT,
      autoHideSeconds: ID_CARD_AUTO_HIDE_MS / 1000,

      // Vòng đời
      isUnmounted: false,   // bỏ qua kết quả API trả về sau khi component đã bị gỡ
    }
  },

  computed: {
    // Trả về chuỗi rỗng khi chưa có dữ liệu để template hiện EMPTY_TEXT
    displayIdCard() {
      if (!this.idCard) return '';
      return this.isIdCardVisible ? this.idCard : this.maskIdCard(this.idCard);
    },

    trainingSystemLabel() {
      return TRAINING_SYSTEM_LABELS[this.trainingSystem] || this.trainingSystem || '';
    },

    typeOfTrainingLabel() {
      return TYPE_OF_TRAINING_LABELS[this.typeOfTraining] || this.typeOfTraining || '';
    },

    // Danh sách trường "Thông tin đào tạo": thêm / sắp xếp lại chỉ cần sửa ở đây.
    // badge: hiển thị dạng huy hiệu màu thay vì chữ thường
    trainingFields() {
      return [
        {key: 'faculty', label: 'Khoa', value: this.faculty},
        {key: 'industry', label: 'Ngành', value: this.industry},
        {key: 'major', label: 'Chuyên ngành', value: this.major},
        {key: 'trainingSystem', label: 'Hệ đào tạo', value: this.trainingSystemLabel, badge: 'primary'},
        {key: 'typeOfTraining', label: 'Loại hình đào tạo', value: this.typeOfTrainingLabel, badge: 'accent'},
        {key: 'branch', label: 'Cơ sở', value: this.branch},
      ];
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
    // Gọi lúc mount và cả khi bấm "Thử lại"
    async fetchDataAdminInformationDetail() {
      if (this.isFetching) return; // đang gọi dở thì bỏ qua lần bấm thêm
      this.isFetching = true;
      this.isLoading = true;
      this.loadFailed = false;

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
        if (!this.isUnmounted) this.loadFailed = true;
      } finally {
        this.isFetching = false;
        if (!this.isUnmounted) this.isLoading = false;
      }
    },

    // Che số CCCD: 060203000127 -> 060203••••27
    // visibleStart / visibleEnd: số ký tự giữ lại ở đầu / cuối.
    // Muốn kín hơn, đặt visibleStart = 0 và visibleEnd = 4 (chỉ lộ 4 số cuối).
    maskIdCard(value, visibleStart = 6, visibleEnd = 2) {
      if (!value) return '';
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
  <article class="info-card" aria-labelledby="admin-info-title" :aria-busy="isLoading">
    <div class="info-card__header">
      <span class="info-card__icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
             stroke-linecap="round" stroke-linejoin="round">
          <path :d="icons.user"/>
        </svg>
      </span>
      <div class="info-card__text">
        <h3 id="admin-info-title" class="info-card__title">Thông tin quản trị viên</h3>
        <p class="info-card__subtitle">Thông tin cá nhân và quá trình đào tạo</p>
      </div>
    </div>

    <span v-if="isLoading" class="sr-only" role="status">Đang tải thông tin...</span>

    <!-- Lỗi tải dữ liệu -->
    <div v-if="loadFailed" class="info-alert" role="alert">
      <div class="info-alert__body">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
             stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path :d="icons.alert"/>
        </svg>
        <div>
          <p class="info-alert__title">Không tải được thông tin</p>
          <p class="info-alert__text">Vui lòng kiểm tra kết nối mạng rồi thử lại.</p>
        </div>
      </div>
      <button type="button" class="btn-retry" :disabled="isLoading" @click="fetchDataAdminInformationDetail">
        Thử lại
      </button>
    </div>

    <template v-else>
      <div class="info-group">
        <h4 class="info-group__title">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
               stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path :d="icons.user"/>
          </svg>
          Thông tin cá nhân
        </h4>
        <dl class="info-grid">
          <div class="info-field">
            <dt class="info-field__label">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                   stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path :d="icons.pin"/>
              </svg>
              Địa chỉ
            </dt>
            <dd class="info-field__value" :class="{'is-empty': !isLoading && !address}">
              <span v-if="isLoading" class="skeleton" aria-hidden="true"></span>
              <template v-else>{{ address || emptyText }}</template>
            </dd>
          </div>

          <div class="info-field">
            <dt class="info-field__label">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                   stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path :d="icons.card"/>
              </svg>
              Căn cước công dân
            </dt>
            <dd class="info-field__value info-field__value--secret" :class="{'is-empty': !isLoading && !idCard}">
              <span v-if="isLoading" class="skeleton" aria-hidden="true"></span>
              <template v-else>
                <span class="info-field__secret-text" aria-live="polite">{{ displayIdCard || emptyText }}</span>
                <button v-if="idCard"
                        type="button"
                        class="btn-reveal"
                        :aria-label="isIdCardVisible ? 'Ẩn số căn cước công dân' : 'Hiện số căn cước công dân'"
                        @click="toggleIdCard">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                       stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path :d="isIdCardVisible ? icons.eyeOff : icons.eye"/>
                  </svg>
                  {{ isIdCardVisible ? 'Ẩn' : 'Hiện' }}
                </button>
                <span v-if="isIdCardVisible" class="info-field__hint">Tự động ẩn sau {{ autoHideSeconds }} giây</span>
              </template>
            </dd>
          </div>
        </dl>
      </div>

      <div class="info-group">
        <h4 class="info-group__title">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
               stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path :d="icons.book"/>
          </svg>
          Thông tin đào tạo
        </h4>
        <dl class="info-grid info-grid--3">
          <div v-for="field in trainingFields" :key="field.key" class="info-field">
            <dt class="info-field__label">{{ field.label }}</dt>
            <dd class="info-field__value" :class="{'is-empty': !isLoading && !field.value}">
              <span v-if="isLoading" class="skeleton" aria-hidden="true"></span>
              <span v-else-if="field.badge && field.value"
                    class="info-badge"
                    :class="{'info-badge--accent': field.badge === 'accent'}">{{ field.value }}</span>
              <template v-else>{{ field.value || emptyText }}</template>
            </dd>
          </div>
        </dl>
      </div>
    </template>
  </article>
</template>