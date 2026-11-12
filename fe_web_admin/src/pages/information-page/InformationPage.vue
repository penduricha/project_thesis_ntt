<script>
import './information-page.scss';
import './event-text-2fa.scss';
import RouterMgmt from "@/router/RouterMgmt.js";
import Header from "../../components/header/Header.vue";
import Footer from "../../components/footer/Footer.vue";

import '@/pages/information-page/card-account/card-account.scss';
import '@/assets/style/theme-color.scss';
import CardAccount from "@/pages/information-page/card-account/CardAccount.vue";
import CardInformationDetail from "@/pages/information-page/card-information-detail/CardInformationDetail.vue";
import CardSettingTwoFA from "@/pages/information-page/card-setting-two-fa/CardSettingTwoFA.vue";
import TextRed from "@/components/span/TextRed.vue";
import TextGreen from "@/components/span/TextGreen.vue";

const PAGE_TITLE = 'Thông tin quản trị';

export default {
  name: "InformationPage",

  components: {
    TextGreen,
    TextRed,
    CardAccountNav: CardAccount,
    CardInformationDetail,
    CardSettingTwoFA,
    Header,
    Footer,
  },

  data() {
    return {
      previousTitle: null,  // tiêu đề tab trước khi vào trang, để khôi phục khi rời
    }
  },

  // ================= LIFECYCLE (theo đúng thứ tự chạy) =================

  // 1. created: chưa có DOM -> ghi lại đường dẫn route hiện tại.
  created() {
    this.saveRouterPath();
  },

  // 2. mounted: DOM đã render -> đặt tiêu đề tab.
  //    (Dữ liệu admin do CardInformationDetail tự tải, trạng thái 2FA do CardSettingTwoFA tự tải.)
  mounted() {
    this.setTitlePage();
  },

  // 3. beforeUnmount: khôi phục tiêu đề tab (đồng bộ lúc gỡ để không ghi đè tiêu đề trang kế tiếp).
  beforeUnmount() {
    this.restoreTitlePage();
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
  }
}
</script>

<template>
  <Header/>
  <section class="section-page-information">
    <CardAccountNav/>

    <!-- Thông tin quản trị viên: component con tự gọi API -->
    <CardInformationDetail/>

    <!-- Xác thực hai yếu tố: component con tự quản lý trạng thái, API, modal -->
    <CardSettingTwoFA/>
  </section>
  <Footer/>
</template>