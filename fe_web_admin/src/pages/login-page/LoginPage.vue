<script>
import './login-page.scss';
import './form-event.scss';
import '@/assets/main-scss/theme-color.scss';
import RouterManagement from "@/routers/RouterManagement.js";
import ButtonRed from "@/components/button/button_red/ButtonRed.vue";
import TextInvalid from "@/components/span/TextInvalid.vue";

import StudentLocalStorage from "@/local_storage/StudentLocalStorage.js";
import {StudentAccount} from "@/model/StudentAccount.js";
import StudentRepository from "@/repository/StudentRepository.js";
import Footer from "@/components/footer/Footer.vue";
import Header from "@/components/header/Header.vue";
// import '../../components/button/button_blue/button_blue.scss';

export default {
  name: "LoginPage",

  components: {
    TextInvalid,
    ButtonRed,
    Header,
    Footer,
  },

  beforeCreate() {

  },

  created() {
    this.saveRouterPath(this.getRoute());
    this.getStatusCheckBoxRememberMe();
  },

  mounted() {
    this.setTitlePage();
  },

  data() {
    return {
      studentId: null,
      password: "",
      validateLogin: "",
      rememberMe: false,

      //btn login
      loadingButtonLogin: false,
      textButtonLogin: 'Đăng nhập',
      disableButtonLogin: false,

      //event form
      disableFormLogin: false,
    }
  },

  methods: {

    setTitlePage() {
      document.title = 'Đăng nhập';
    },

    getRoute() {
      //ở đây có props thì phải thêm path của props
      return this.$route.path;
    },

    saveRouterPath(route) {
      const routerManagement = new RouterManagement();
      routerManagement.savePathToSessionStorage(route);
    },

    //lock paste
    preventPaste(event) {
      event.preventDefault();
    },

    // isNumber(evt) {
    //   const charCode = (evt.which) ? evt.which : evt.keyCode;
    //   // Các mã ASCII từ 48 đến 57 tương ứng với các số từ 0 đến 9
    //   if (charCode > 31 && (charCode < 48 || charCode > 57)) {
    //     evt.preventDefault(); // Chặn không cho nhập ký tự đó vào input
    //   }
    // },

    setInputStudentId(event) {
      // Chỉ cho phép các ký tự số
      // Gán lại giá trị cho biến studentId
      this.studentId = event.target.value.replace(/[^0-9]/g, '');

      if (!this.studentId) {
        this.validateLogin = "";
      } else {
        this.validateLogin = "";
      }
    },

    setInputPassword() {
      if (this.password) {
        this.validateLogin = "";
      } else {
        this.validateLogin = "";
      }
    },

    //Call api

    navigateToRegisterCoursesPage() {
      //save path to local storage
      const routerManagement = new RouterManagement();

      //save path
      const routerPathToSave = '/information-student';
      routerManagement.savePathToLocalStorage(routerPathToSave);

      this.$router.push({
        path: routerPathToSave,
        // query: {
        // }
      }).then(() => {
        // Delay the reload to ensure the navigation is completed
        // Adjust the timeout as needed
        // setTimeout(() => {
        //    window.location.reload();
        // }, 50);
      }).catch((error) => {
        console.error('Error navigating :', error);
        alert(error);
      });
    },

    loadButtonLogin() {
      this.loadingButtonLogin = true;
      this.disableButtonLogin = true;
      this.textButtonLogin = "";
      this.disableFormLogin = true;
    },

    stopLoadButtonLogin() {
      this.loadingButtonLogin = false;
      this.disableButtonLogin = false;
      this.textButtonLogin = 'Đăng nhập';
      this.disableFormLogin = false;
    },

    async handleLoginEvent() {
      const nullFieldInput = !this.studentId || !this.password;
      if (nullFieldInput) {
        this.validateLogin = 'Vui lòng nhập đầy đủ thông tin.';
      } else {
        this.loadButtonLogin();
        //call api
        //Call logic Login
        const studentCache = new StudentRepository();
        let statusLogin = await studentCache.getResponseFromLoginRequest(Number(this.studentId), String(this.password));

        if (statusLogin === "Failed") {
          this.validateLogin = 'Mã số sinh viên hoặc mật khẩu không đúng.';
          this.stopLoadButtonLogin();
        } else if (statusLogin === "OK") {
          //save remember me
          this.validateLogin = "";
          if (this.rememberMe === true) {
            this.saveDataInputToLocalStorage();
          } else {
            this.removeDataInputFromLocalStorage();
          }
          //save student id to local storage
          console.log('Login successfully');
          //dang nhap thanh cong, dieu huong qua
          this.navigateToRegisterCoursesPage();
        }
        this.stopLoadButtonLogin();
      }
    },

    removeDataInputFromLocalStorage() {
      const studentLocalStorage = new StudentLocalStorage();
      studentLocalStorage.removeLocalStorageRememberMe();
    },

    saveDataInputToLocalStorage() {
      if (this.studentId && this.password) {
        const studentLocalStorage = new StudentLocalStorage();
        studentLocalStorage.saveLocalStorageRememberMe(
          this.studentId,
          this.password.trim());
      }
    },

    getStatusCheckBoxRememberMe() {
      /* neu local storage có save thì true ngược lại thì false */
      /* neu tru thi set input*/
      const studentLocalStorage = new StudentLocalStorage();
      let studentFetched = studentLocalStorage
        .getLocalStorageRememberMe();
      console.log('Student fetched from local storage remember me:', studentFetched);
      if (!studentFetched || (
        typeof studentFetched === 'object'
        && Object.keys(studentFetched).length === 0)) {
        this.rememberMe = false;
      } else {
        this.rememberMe = true;
        this.setDataInputFromLocalStorage(studentFetched);
      }
    },

    setDataInputFromLocalStorage(studentFetched) {
      if (studentFetched) {
        let studentId = studentFetched.studentId;
        let password = studentFetched.password;
        const studentAccount = new StudentAccount(studentId, password);
        console.log(studentAccount.toString());
        this.studentId = studentAccount.getStudentId();
        this.password = studentAccount.getPassword();
      }
    }
  },

  computed: {
    // setBorderColorChoose() {
    //   return (index) => {
    //     return (index === this.indexQuestion)
    //         ? 'border-choose'
    //         : 'border-no-choose';
    //   };
    // },
    setDisableForm() {
      return (this.disableFormLogin) ? 'disabled' : 'allow';
    }
  },
}
</script>

<template>
  <form class="container-page-login" @submit.prevent="handleLoginEvent">
    <Header/>
    <section class="container-form-login-page" >
      <div class="form-login" :class="['event-form-login', setDisableForm]">
        <h4 class="title-login">ĐĂNG NHẬP</h4>
        <div class="form-floating mb-3 style-input-login">
          <input type="text" class="form-control border-radius-zero"
            id="floatingInputStudentId" 
            placeholder="Mã sinh viên (nhập số)"
            @input="setInputStudentId"
            v-model="studentId" maxlength="10">
          <label for="floatingInputStudentId">Mã sinh viên (nhập số)</label>
        </div>
        <div class="form-floating mb-3 style-input-login">
          <input type="password" class="form-control border-radius-zero" id="floatingInputPassword" placeholder="Mật khẩu"
            @input="setInputPassword" v-model="password" @paste="preventPaste($event)" maxlength="20">
          <label for="floatingInputPassword">Mật khẩu</label>
        </div>

        <TextInvalid :text-span="validateLogin"/>

        <div class="box-remember-me">
          <input type="checkbox" id="rememberMe" v-model="rememberMe" @change="saveDataInputToLocalStorage()"
            class="style-checkbox" />
          <span>Ghi nhớ đăng nhập</span>
        </div>

        <ButtonRed class="btn-login" 
          @click="handleLoginEvent()" 
          :text-button="textButtonLogin"
          :loading-button="loadingButtonLogin" 
          :disable-button="disableButtonLogin" 
          />
      </div>
    </section>
    <Footer/>
  </form>

</template>

<style scoped lang="scss">

//.container-page-login {
//  border: solid;
//}

.border-radius-zero {
  border-radius: 0;
}
</style>