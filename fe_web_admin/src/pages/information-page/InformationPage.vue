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
import {id} from "vuetify/locale";

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
      address: null,
      idCard: null,

      //University
      industry: null,
      faculty: null,
      branch: null,
      typeOfTraining: null,
      trainingSystem: null,
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
    await this.fetchDataAdminInformationDetail();
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

    async fetchDataAdminInformationDetail(){
      // "trainingSystem": "Master",
      //     "address": "467/134/20 QL1A, An Phú Đông, Tphcm.",
      //     "typeOfTraining": "Research",
      //     "major": "AI/ML",
      //     "idCard": "060203000127",
      //     "adminId": 2500021775,
      //     "industry": "AI (MLOPS)",
      //     "branch": "An Phu Dong ward, Ho Chi Minh city.",
      //     "faculty": "IT"
      const adminRepository = new AdminRepository();
      let adminInformationDetail = await adminRepository.getAdminInformationDetailByAdminId();
      console.log('Admin detail in4: ',adminInformationDetail);
      if(adminInformationDetail) {
        this.address = adminInformationDetail.address;
        this.idCard = adminInformationDetail.idCard;
        this.industry = adminInformationDetail.industry;
        this.faculty = adminInformationDetail.faculty;

        this.trainingSystem = adminInformationDetail.trainingSystem;
        this.typeOfTraining = adminInformationDetail.typeOfTraining;
        this.major = adminInformationDetail.major;
        this.branch = adminInformationDetail.branch;
      }
    }


  },

  computed: {
    id() {
      return id
    },
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
          <span class="text-detail-information">Địa chỉ: {{address}}</span>
          <span class="text-detail-information">Căn cước công dân: {{idCard}}</span>
          <span class="text-detail-information">Ngành: {{industry}}</span>
          <span class="text-detail-information">Khoa: {{faculty}}</span>
        </div>
        <div class="div-text-detail-information div-text-detail-information-flex-responsive">
          <span class="text-detail-information">Loại hình đào tạo: {{typeOfTraining}}</span>
          <span class="text-detail-information">Hệ đào tạo: {{trainingSystem}}</span>
          <span class="text-detail-information">Chuyên ngành: {{major}}</span>
          <span class="text-detail-information">Cơ sở: {{branch}}</span>
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