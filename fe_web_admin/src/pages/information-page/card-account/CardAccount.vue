<script>
import './card-account.scss';
import '@/assets/style/theme-color.scss';
import AdminRepository from "@/repository/AdminRepository.js";
import { broadcastLogout } from '@/assets/auth-sync.js';

import MgmtDateTimeFormat from "@/assets/manage-components/MgmtDateTimeFormat.js";

export default {
  name: "CardAccount",

  props: {
    // Link ảnh đại diện (nếu có). Không truyền thì hiện chữ cái đầu của tên.
    avatarUrl: {
      type: String,
      default: ''
    }
  },

  data() {
    return {
      loadingButtonLogout: false,
      disableButtonLogout: false,
      isDialogClosing: false, // đang chạy animation đóng dialog
      isUnmounted: false,     // component đã bị gỡ: dùng để bỏ qua kết quả API trả về muộn
      prefersReducedMotion: false, // cập nhật bởi media query listener (đăng ký ở mounted)

      // Admin Info
      adminId: null,
      adminName: null,
      gender: null,
      genderBool: true,
      dateOfBirthAdmin: null,
      dateOfBirthAdminString: null,
    }
  },

  computed: {
    // "Từ Quang Nhật" -> "TN" (chữ đầu của họ + chữ đầu của tên)
    initials() {
      const words = (this.adminName || '').trim().split(/\s+/).filter(Boolean);
      if (!words.length) return '?';
      const first = words[0].charAt(0);
      const last = words.length > 1 ? words[words.length - 1].charAt(0) : '';
      return (first + last).toUpperCase();
    }
  },

  // Guard được Vue Router gọi trước khi rời khỏi route hiện tại
  async beforeRouteLeave(to, from, next) {
    if (to.path === '/login') {
      try {
        const adminRepository = new AdminRepository();
        let status = await adminRepository.getResponseFromLogoutRequest();

        if (status === "OK") {
          next();
        } else {
          next(false);
        }
      } catch (error) {
        console.error('Error to logout:', error);
        next(false);
      }
    } else {
      next();
    }
  },

  // ================= LIFECYCLE =================

  // 1. created: instance đã có data/computed/methods, CHƯA có DOM.
  //    Chỉ khởi tạo state thuần, không đụng DOM/window/API ở đây.
  created() {
    this.isUnmounted = false;
  },

  // 2. mounted: DOM đã render xong ($refs dùng được).
  //    Đăng ký thứ phụ thuộc trình duyệt + gọi API lấy dữ liệu.
  async mounted() {
    this.setupReducedMotionListener();
    await this.fetchDataAdminInformationBasic();
  },

  // 3. beforeUnmount: instance còn nguyên vẹn -> dọn dẹp tài nguyên đã đăng ký.
  beforeUnmount() {
    this.teardownReducedMotionListener();

    // Dialog đang mở (showModal) mà component bị gỡ thì đóng sạch để không kẹt backdrop/focus trap
    const dialog = this.$refs.logoutDialog;
    if (dialog?.open) dialog.close();
  },

  // 4. unmounted: đã gỡ xong -> đánh dấu để các Promise còn treo không ghi vào state nữa.
  unmounted() {
    this.isUnmounted = true;
  },

  // =============================================

  methods: {
    async fetchDataAdminInformationBasic() {
      try {
        const adminRepository = new AdminRepository();
        let adminInformationBasic = await adminRepository.getAdminInformationBasicByAdminId();

        // Người dùng đã rời trang trong lúc chờ API: bỏ qua kết quả
        if (this.isUnmounted) return;

        if (adminInformationBasic) {
          this.adminId = adminInformationBasic.adminId;
          this.adminName = adminInformationBasic.adminName;
          this.genderBool = adminInformationBasic.gender;
          this.gender = this.genderBool ? "Nam" : "Nữ";
          this.dateOfBirthAdmin = adminInformationBasic.dateOfBirthAdmin;

          const mgmtDateTimeFormat = new MgmtDateTimeFormat();
          this.dateOfBirthAdminString = mgmtDateTimeFormat.getFormatedDateByParamDate(this.dateOfBirthAdmin);
        }
      } catch (error) {
        console.error('Error to fetch admin information:', error);
      }
    },

    // ----- Theo dõi "giảm chuyển động" của hệ điều hành (đăng ký ở mounted, gỡ ở beforeUnmount) -----
    setupReducedMotionListener() {
      this.reducedMotionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
      this.prefersReducedMotion = this.reducedMotionQuery.matches;
      this.reducedMotionQuery.addEventListener('change', this.onReducedMotionChange);
    },

    teardownReducedMotionListener() {
      this.reducedMotionQuery?.removeEventListener('change', this.onReducedMotionChange);
      this.reducedMotionQuery = null;
    },

    onReducedMotionChange(event) {
      this.prefersReducedMotion = event.matches;
    },

    // ----- Hộp thoại xác nhận đăng xuất -----
    openLogoutConfirm() {
      this.isDialogClosing = false;
      this.$refs.logoutDialog?.showModal(); // animation mở chạy bằng CSS [open]
    },

    // Đóng có animation: thêm class .is-closing, chạy xong mới gọi close()
    closeLogoutConfirm() {
      if (this.loadingButtonLogout) return; // đang đăng xuất thì không cho đóng

      const dialog = this.$refs.logoutDialog;
      if (!dialog || !dialog.open || this.isDialogClosing) return;

      // Người dùng tắt hiệu ứng chuyển động: đóng ngay, không chờ animation
      if (this.prefersReducedMotion) {
        dialog.close();
        return;
      }
      this.isDialogClosing = true;
    },

    onDialogAnimationEnd(event) {
      const dialog = this.$refs.logoutDialog;
      if (event.target !== dialog || !this.isDialogClosing) return;
      this.isDialogClosing = false;
      dialog.close();
    },

    // Phím Esc: chặn đóng đột ngột của trình duyệt, đi qua đường đóng có animation
    onDialogCancel(event) {
      event.preventDefault();
      this.closeLogoutConfirm();
    },

    async handleLogout() {
      this.loadButtonLogout();
      const pathLogin = '/login';

      try {
        const adminRepository = new AdminRepository();
        let status = await adminRepository.getResponseFromLogoutRequest();

        if (status === "OK") {
          broadcastLogout(); // <-- báo các tab khác
          this.$refs.logoutDialog?.close();
          await this.$router.replace({ path: pathLogin });
        }
      } catch (error) {
        console.error('Error to logout:', error);
        alert(error);
      } finally {
        // Sau router.replace component có thể đã unmounted: không cập nhật state nữa
        if (!this.isUnmounted) this.stopLoadButtonLogout();
      }
    },

    loadButtonLogout() {
      this.loadingButtonLogout = true;
      this.disableButtonLogout = true;
    },

    stopLoadButtonLogout() {
      this.loadingButtonLogout = false;
      this.disableButtonLogout = false;
    },
  }
}
</script>

<template>
  <div class="div-account-nav">
    <div class="profile-card">
      <div class="profile-card__avatar" aria-hidden="true">
        <img v-if="avatarUrl" :src="avatarUrl" alt="">
        <span v-else class="profile-card__initials">{{ initials }}</span>
      </div>

      <div class="profile-card__body">
        <div class="profile-card__heading">
          <h2 class="profile-card__name">{{ adminName }}</h2>
          <span class="ui-badge ui-badge--primary">Quản trị viên</span>
        </div>

        <dl class="profile-card__meta">
          <div class="profile-card__meta-item">
            <dt class="profile-card__label">Mã quản trị viên</dt>
            <dd class="profile-card__value">{{ adminId }}</dd>
          </div>
          <div class="profile-card__meta-item">
            <dt class="profile-card__label">Giới tính</dt>
            <dd class="profile-card__value">{{ gender }}</dd>
          </div>
          <div class="profile-card__meta-item">
            <dt class="profile-card__label">Ngày sinh</dt>
            <dd class="profile-card__value">{{ dateOfBirthAdminString }}</dd>
          </div>
        </dl>
      </div>

      <button type="button"
              class="ui-btn ui-btn--ghost profile-card__logout"
              @click="openLogoutConfirm">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
             stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
          <polyline points="16 17 21 12 16 7"/>
          <line x1="21" y1="12" x2="9" y2="12"/>
        </svg>
        <span>Đăng xuất</span>
      </button>
    </div>

    <!-- Hộp thoại xác nhận: Esc hoặc bấm ra ngoài vùng tối để đóng -->
    <dialog ref="logoutDialog"
            class="confirm-dialog"
            :class="{ 'is-closing': isDialogClosing }"
            aria-labelledby="logout-dialog-title"
            @click.self="closeLogoutConfirm"
            @cancel="onDialogCancel"
            @animationend="onDialogAnimationEnd">
      <div class="confirm-dialog__panel">
        <h3 id="logout-dialog-title" class="confirm-dialog__title">Đăng xuất khỏi hệ thống?</h3>
        <p class="confirm-dialog__text">Bạn sẽ cần đăng nhập lại để tiếp tục quản trị.</p>
        <div class="confirm-dialog__actions">
          <button type="button"
                  class="ui-btn ui-btn--ghost"
                  autofocus
                  :disabled="disableButtonLogout"
                  @click="closeLogoutConfirm">
            Hủy
          </button>
          <button type="button"
                  class="ui-btn ui-btn--primary"
                  :disabled="disableButtonLogout"
                  @click="handleLogout">
            <span v-if="loadingButtonLogout" class="ui-spinner" aria-hidden="true"></span>
            <span>{{ loadingButtonLogout ? '' : 'Đăng xuất' }}</span>
          </button>
        </div>
      </div>
    </dialog>
  </div>
</template>