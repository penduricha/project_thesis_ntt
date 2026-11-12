<script>
import './header.scss';
import '@/assets/style/theme-color.scss';

const SCHOOL_NAME = 'Trường Đại học Nguyễn Tất Thành';
const LOGIN_PATH = '/login';

export default {
  name: "Header",

  props: {
    // Trang đích khi bấm vào logo
    homePath: {
      type: String,
      default: '/information-admin',
    },
  },

  data() {
    return {
      schoolName: SCHOOL_NAME,
    };
  },

  computed: {
    // Ở trang đăng nhập chưa có "trang chủ" để quay về -> logo chỉ là ảnh, không phải link
    isLogoClickable() {
      return !!this.$route && this.$route.path !== LOGIN_PATH;
    },

    // Link thì dùng <router-link> (điều hướng không tải lại trang), không phải link thì dùng <div>
    logoTag() {
      return this.isLogoClickable ? 'router-link' : 'div';
    },

    logoAttrs() {
      if (!this.isLogoClickable) return {};
      return {
        to: this.homePath,
        title: 'Về trang chủ',
        'aria-label': `${this.schoolName} - Về trang chủ`,
      };
    },
  },
}
</script>

<template>
  <header class="site-header">
    <component :is="logoTag" v-bind="logoAttrs" class="logo-link">
      <!-- Logo nằm ở đầu trang (LCP) nên tải ưu tiên, KHÔNG lazy-load -->
      <img src="../../assets/images/banner-ntt.jpg"
           :alt="schoolName"
           class="image-banner-ntt"
           fetchpriority="high"
           decoding="async"
           draggable="false"
      >
    </component>
  </header>
</template>