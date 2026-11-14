<script>
import Footer from "@/components/footer/Footer.vue";
import Header from "@/components/header/Header.vue";
import './page-not-found.scss';
import RouterMgmt from "@/router/RouterMgmt.js";
import ButtonRed from "@/components/button/button_red/ButtonRed.vue";
import AdminStorage from "@/local_storage/AdminStorage.js";

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
      const adminLocalStorage = new AdminStorage();
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

    },

    // Nút phụ "quay lại trang trước" — chỉ lùi lịch sử trình duyệt, không đổi
    // logic điều hướng chính. Nếu không còn lịch sử (vào thẳng link 404) thì
    // rơi về đúng hành vi của nút chính cho an toàn.
    goBack() {
      if (window.history.length > 1) {
        this.$router.go(-1);
      } else {
        this.handleReturnFrom404Page();
      }
    }
  },

  computed: {},
}
</script>

<template>
  <Header/>
  <section class="section-page-404">
    <div class="wrap-404">

      <!-- Số 404 với kính lúp thay cho số 0 (chỉ trang trí) -->
      <div class="visual-404" aria-hidden="true">
        <div class="box-code-404">
          <span class="digit">4</span>
          <span class="digit digit-zero">
            <svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
              <circle cx="42" cy="42" r="30" fill="none" stroke="currentColor" stroke-width="10"/>
              <line class="handle" x1="64" y1="64" x2="90" y2="90" stroke-width="12" stroke-linecap="round"/>
              <path d="M30 42h24M42 30v24" stroke="currentColor" stroke-width="6" stroke-linecap="round" opacity=".35"/>
            </svg>
          </span>
          <span class="digit">4</span>
        </div>
      </div>

      <div class="content-404">
        <h4>Không tìm thấy trang</h4>
        <p class="text-desc">
          Trang bạn đang tìm kiếm có thể đã bị xoá, đổi tên hoặc tạm thời không truy cập được.
          Vui lòng kiểm tra lại đường dẫn hoặc quay về trang chủ.
        </p>

        <div class="actions-404">
          <ButtonRed :disable-button="false"
                     :loading-button="false"
                     :text-button="textBtnReturn"
                     @click="handleReturnFrom404Page"
                     class="style-btn-return-from-not-found"
          />
          <button type="button" class="link-go-back" @click="goBack">
            Hoặc quay lại trang trước
          </button>
        </div>
      </div>

    </div>
  </section>
  <Footer/>
</template>

<style scoped lang="scss">

</style>