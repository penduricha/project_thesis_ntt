<script>
import './footer.scss';

export default {
  name: 'SiteFooter',
  data() {
    return {
      year: new Date().getFullYear(),
      schoolName: 'TRƯỜNG ĐẠI HỌC NGUYỄN TẤT THÀNH',
      copyright: 'Nguyen Tat Thanh University - All Rights Reserved.',
      contacts: [
        {
          label: 'Địa chỉ:',
          text: '300A – Nguyễn Tất Thành, Phường Xóm Chiếu, TP. Hồ Chí Minh, Việt Nam',
        },
        {
          label: 'Điện thoại:',
          text: '1900 2039',
          href: 'tel:19002039',
        },
        {
          label: 'Email 1:',
          text: 'ntt@ntt.edu.vn',
          href: 'mailto:ntt@ntt.edu.vn',
        },
        {
          label: 'Email 2:',
          text: 'tuyensinh@ntt.edu.vn',
          href: 'mailto:tuyensinh@ntt.edu.vn',
        },
      ],
    };
  },
};
</script>

<template>
  <footer class="site-footer">
    <!-- Khối chính: tên trường | thông tin liên hệ -->
    <div class="site-footer__main">
      <div class="site-footer__inner">
        <div class="site-footer__brand">
          <h2 class="site-footer__name">{{ schoolName }}</h2>
        </div>

        <ul class="site-footer__contact">
          <li
              v-for="item in contacts"
              :key="item.label"
              class="site-footer__item"
          >
            <span class="site-footer__label">{{ item.label }}</span>
            <a
                v-if="item.href"
                :href="item.href"
                class="site-footer__link"
            >{{ item.text }}</a>
            <span v-else>{{ item.text }}</span>
          </li>
        </ul>
      </div>
    </div>

    <!-- Thanh bản quyền -->
    <div class="site-footer__bottom">
      <p class="site-footer__copy">©{{ year }} - {{ copyright }}</p>
    </div>
  </footer>
</template>

<style scoped lang="scss">
/* Toàn bộ style nằm trong footer.scss (global, prefix .site-footer) */
</style>