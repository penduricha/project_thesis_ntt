<script>
import Footer from "@/components/footer/Footer.vue";
import Header from "@/components/header/Header.vue";
import './page-not-found.scss';
import RouterMgmt from "@/routers/RouterMgmt.js";
import ButtonRed from "@/components/button/button_red/ButtonRed.vue";
import AdminLocalStorage from "@/local_storage/AdminLocalStorage.js";

export default {
  name: "PageNotFound",

  data() {
    return {
      textBtnReturn: 'Quay lại trang chủ',
    }
  },

  components: {
    ButtonRed,
    Header,
    Footer,
  },

  created() {
    this.saveRouterPath(this.getRoute());
  },

  mounted() {
    this.setTitlePage();
  },

  methods: {
    setTitlePage() {
      document.title = '404 - Not Found';
    },

    getRoute() {
      //ở đây có props thì phải thêm path của props
      return this.$route.path;
    },

    saveRouterPath(route) {
      const routerManagement = new RouterMgmt();
      routerManagement.savePathToSessionStorage(route);
    },

    handleReturnFrom404Page() {
      //Cach 1
      //const pathReturn = '/';
      // Delay the reload to ensure the navigation is completed
      // Adjust the timeout as needed
      // this.$router.replace({path: pathReturn}).then(() => {
      //       setTimeout(() => {
      //         window.location.reload();
      //       }, 0.5);
      //     })
      //     .catch((error) => {
      //       console.error('Error navigating:', error);
      //       alert(error);
      //     });
      //Cach 2
      const routerManagement = new RouterMgmt();
      const adminLocalStorage = new AdminLocalStorage();
      const checkPath_And_ID =
          routerManagement.getPathFromLocalStorage() &&
          adminLocalStorage.getAdminIdToLocalStorage();
      if(checkPath_And_ID) {
        const pathReturn = '/information-admin';
        this.$router.replace({path: pathReturn}).then(() => {

              })
              .catch((error) => {
                console.error('Error navigating:', error);
                alert(error);
              });
      } else {
        const pathReturn = '/login';
        this.$router.replace({path: pathReturn}).then(() => {
              // setTimeout(() => {
              //   window.location.reload();
              // }, 10);
            })
            .catch((error) => {
              console.error('Error navigating:', error);
              alert(error);
            });
      }

    }
  },

  computed: {},
}
</script>

<template>
  <Header/>
  <section class="section-page-404">
    <div class="box-text-404-not-found">
      <h4>Ooops 404 - Không tìm thấy trang</h4>
      <ButtonRed :disable-button="false"
                    :loading-button="false"
                    :text-button="textBtnReturn"
                    @click="handleReturnFrom404Page"
                    class="style-btn-return-from-not-found"
      />
    </div>
  </section>
  <Footer/>
</template>

<style scoped lang="scss">

</style>