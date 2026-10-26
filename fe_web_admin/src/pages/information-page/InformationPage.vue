<script>
import './information-page.scss';
import RouterManagement from "@/routers/RouterManagement.js";
import Header from "../../components/header/Header.vue";
import Footer from "../../components/footer/Footer.vue";

import './card-account-nav/card-account-nav.scss';
import '../../assets/main-scss/theme-color.scss';
import CardAccountNav from "@/pages/information-page/card-account-nav/CardAccountNav.vue";
import AdminRepository from "@/repository/AdminRepository.js";

export default {
  name: "InformationPage",

  components: {
    CardAccountNav,
    Header,
    Footer,
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
      const routerManagement = new RouterManagement();
      routerManagement.savePathToSessionStorage(routePath);
    },
  },

  computed: {

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
          <span class="text-detail-information">Hệ đào tạo: {{trainingSystem}}</span>
          <span class="text-detail-information">Ngành: {{industry}}</span>
          <span class="text-detail-information">Khoa: {{faculty}}</span>
        </div>
        <div class="div-text-detail-information div-text-detail-information-flex-responsive">
          <span class="text-detail-information">Loại hình đào tạo: {{typeOfTraining}}</span>
          <span class="text-detail-information">Chuyên ngành: {{major}}</span>
          <span class="text-detail-information">Cơ sở: {{branch}}</span>
        </div>
      </div>
    </div>
  </section>
  <Footer/>
</template>

<style scoped lang="scss">
</style>