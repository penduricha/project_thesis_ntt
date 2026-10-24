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
<!--        <div class="div-text-detail-information">-->
<!--          <span class="text-detail-information">Hệ đào tạo: <b>{{trainingSystem}}</b></span>-->
<!--          <span class="text-detail-information">Ngành: <b>{{industry}}</b></span>-->
<!--          <span class="text-detail-information">Khoa: <b>{{faculty}}</b></span>-->
<!--        </div>-->
<!--        <div class="div-text-detail-information div-text-detail-information-flex-responsive">-->
<!--          <span class="text-detail-information">Loại hình đào tạo: <b>{{typeOfTraining}}</b></span>-->
<!--          <span class="text-detail-information">Chuyên ngành: <b>{{major}}</b></span>-->
<!--          <span class="text-detail-information">Cơ sở: <b>{{branch}}</b></span>-->
<!--        </div>-->
      </div>
    </div>
  </section>
  <Footer/>
</template>

<style scoped lang="scss">
.div-detail-information {
  border-left: solid 4px var(--blue-color);
  flex-direction: column;
  padding-top: 1rem;
  gap: 1rem;
}

@media only screen and (max-width: 767px) {
  .section-page-information {
    padding-bottom: 1rem;
  }

  .div-detail-information {
    justify-content: start;
    align-items: center;
    height: 50rem;
  }

  .div-background-text-detail {
    display: flex;
    flex-direction: column;
    width: 20rem;
    height: 15rem;
    gap: 1rem;
  }

  .div-text-detail-information {
    flex: 1;
    justify-content: end;
  }

  .div-text-detail-information-flex-responsive {
    justify-content: start;
  }

  .section-page-information {
    height: 120vh;
  }
}

@media only screen and (min-width: 767px) and (max-width: 1223px) {
  .section-page-information {
    padding-bottom: 1rem;
  }

  .section-page-information {
    height: 120vh;
  }

  .div-detail-information {
    justify-content: start;
    align-items: center;
    height: 24.5rem;
  }

  .div-background-text-detail {
    display: flex;
    flex-direction: column;
    /* Thay đổi từ row thành column */
    width: 35rem;
    height: 15rem;
    gap: 1rem;
  }

  .div-text-detail-information {
    flex: 1;
    justify-content: end;
  }

  .div-text-detail-information-flex-responsive {
    justify-content: start;
  }
}

</style>