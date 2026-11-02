<script>
import './information-page.scss';
import './event-text-2fa.scss';
import RouterMgmt from "@/routers/RouterMgmt.js";
import Header from "../../components/header/Header.vue";
import Footer from "../../components/footer/Footer.vue";

import './card-account-nav/card-account-nav.scss';
import '@/assets/main_scss/theme-color.scss';
import CardAccountNav from "@/pages/information-page/card-account-nav/CardAccountNav.vue";
import AdminRepository from "@/repository/AdminRepository.js";
import TextRed from "@/components/span/TextRed.vue";
import TextGreen from "@/components/span/TextGreen.vue";
import ButtonBlue from "@/components/button/button_blue/ButtonBlue.vue";
import ModalActivate2FA from "@/pages/information-page/pop-up-activate-2fa/ModalActivate2FA.vue";

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
      /*
              {
            "data": {
                "adminId": 2500021775,
                "gender": true,
                "adminName": "Từ Quang Nhật",
                "course": {
                    "courseId": 8480201,
                    "trainingSystem": "Master",
                    "branch": "An Phu Dong ward, Ho Chi Minh city.",
                    "industry": "CNTT",
                    "faculty": "Khoa CNTT",
                    "major": "AI/ML",
                    "typeOfTraining": "Research",
                    "yearOpening": 2025
                },
                "clazzName": "25MTH1B"
            },
            "message": "Admin found successfully.",
            "status": 200
        }
       */

      //Admin Info
      adminId: null,
      adminName: null,
      gender: true,
      trainingSystem: null,
      clazzName: null,

      //University
      industry: null,
      faculty: null,
      branch: null,
      typeOfTraining: null,
      major: null,
      yearOpening: null,

      //btn activate
      is2faEnabled: false,
    }
  },

  created() {
    this.saveRouterPath();
  },

  async mounted() {
    this.setTitlePage();
  },

  beforeDestroy() {

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
  },

  computed: {
    setTextActivate2FA() {
      return (this.is2faEnabled) ? 'text-2fa-activate' : 'text-2fa-unactivated';
    }
  }
}
</script>

<template>
  <Header/>
  <section class="section-page-information">
    <CardAccountNav/>
    <div class="div-account-nav div-detail-information" style="flex-direction: column">
      <h3 class="text-title-information">THÔNG TIN QUẢN TRỊ VIÊN</h3>
      <div class="div-background-text-detail">
        <div class="div-text-detail-information">
          <span class="text-detail-information">Địa chỉ: 465/134 QL1A, Tphcm</span>
          <span class="text-detail-information">Căn cước công dân: 060203444567</span>
          <span class="text-detail-information">Ngành: Trí tuệ nhân tạo (MLOPS)</span>
          <span class="text-detail-information">Khoa: CNTT</span>
        </div>
        <div class="div-text-detail-information div-text-detail-information-flex-responsive">
          <span class="text-detail-information">Loại hình đào tạo: Thạc sĩ</span>
          <span class="text-detail-information">Hệ đào tạo: Chính quy</span>
          <span class="text-detail-information">Chuyên ngành: AI/ML</span>
          <span class="text-detail-information">Cơ sở: An Phú Đông</span>
        </div>
      </div>
    </div>
    <div class="div-account-nav div-detail-information div-detail-2fa">
      <h3 class="text-title-information">XÁC THỰC HAI YẾU TỐ</h3>

      <div class="div-2fa-row">
        <!-- role="status": trình đọc màn hình tự đọc lại khi trạng thái đổi -->
        <div class="div-2fa-status" role="status">
          <span>Trạng thái:</span>
          <span
              class="text-2fa-status"
              :class="['text-2fa-status-color', setTextActivate2FA]"
          >
        {{ is2faEnabled ? 'Đã kích hoạt 2FA' : 'Chưa kích hoạt 2FA' }}
      </span>
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
    </div>
  </section>
  <Footer/>
  <ModalActivate2FA/>
</template>

<style scoped lang="scss">

</style>