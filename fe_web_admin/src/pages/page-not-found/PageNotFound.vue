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
      textBtnBack: 'Quay lại trang trước',
    }
  },

  components: {
    ButtonRed,
    Header,
    Footer,
  },

  created() {
    this.saveRouterPath(this.getRoute());
    // Nhãn nút phản ánh đúng nơi sẽ quay về (đã đăng nhập → trang chủ, chưa → trang đăng nhập)
    this.textBtnReturn = this.resolveReturnTarget().text;
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

    // Quyết định nơi quay về. Giữ nguyên logic cũ: có path đã lưu + có id admin → trang thông tin, ngược lại → đăng nhập
    resolveReturnTarget() {
      const routerManagement = new RouterMgmt();
      const adminLocalStorage = new AdminStorage();
      const isLoggedIn =
          routerManagement.getPathFromLocalStorage() &&
          adminLocalStorage.getAdminIdToLocalStorage();
      return isLoggedIn
          ? {path: '/information-admin', text: 'Quay lại trang chủ'}
          : {path: '/login', text: 'Về trang đăng nhập'};
    },

    handleReturnFrom404Page() {
      // Đọc lại trạng thái đăng nhập tại thời điểm bấm nút
      const {path} = this.resolveReturnTarget();
      this.$router.replace({path}).catch((error) => {
        // Chỉ ghi log, không dùng alert() để tránh chặn người dùng bằng hộp thoại
        console.error('Error navigating:', error);
      });
    },

    // vue-router 4 lưu history.state.back: có giá trị nghĩa là còn trang trước trong ứng dụng.
    // Nếu người dùng vào thẳng link lỗi (không có trang trước) thì về trang chủ thay vì thoát khỏi website.
    handleGoBack() {
      if (window.history.state && window.history.state.back) {
        this.$router.back();
      } else {
        this.handleReturnFrom404Page();
      }
    }
  },

  computed: {
    // Đường dẫn người dùng đã gõ (chỉ path, không hiển thị query string)
    requestedPath() {
      return this.$route.path || '';
    },
  },
}
</script>

<template>
  <Header/>
  <section class="section-page-404" aria-labelledby="title-404">
    <div class="box-text-404-not-found">

      <!-- Số 404, chữ "0" là kính lúp (chỉ trang trí) -->
      <div class="box-code-404" aria-hidden="true">
        <span class="digit">4</span>
        <span class="digit-zero"></span>
        <span class="digit">4</span>
      </div>

      <div class="box-content-404">
        <span class="badge-404">Lỗi 404</span>
        <h1 id="title-404" class="title-404">Không tìm thấy trang bạn cần</h1>
        <p class="text-desc">
          Đường dẫn có thể đã bị xoá, đổi tên hoặc bạn nhập chưa đúng.
          Hãy thử quay lại trang trước hoặc về trang chủ.
        </p>

        <p v-if="requestedPath" class="box-path-404">
          <span class="label">Đường dẫn:</span>
          <code :title="requestedPath">{{ requestedPath }}</code>
        </p>

        <div class="box-actions-404">
          <ButtonRed :disable-button="false"
                     :loading-button="false"
                     :text-button="textBtnReturn"
                     @click="handleReturnFrom404Page"
                     class="style-btn-return-from-not-found"
          />
          <button type="button" class="btn-back-404" @click="handleGoBack">
            {{ textBtnBack }}
          </button>
        </div>
      </div>
    </div>
  </section>
  <Footer/>
</template>

<style scoped lang="scss">

</style>