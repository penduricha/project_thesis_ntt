<script>
import './card-account-nav.scss';
import '../../../assets/main-scss/theme-color.scss';
// import StudentLocalStorage from "@/local_storage/StudentLocalStorage.js";
import ButtonRed from "@/components/button/button_red/ButtonRed.vue";
// import RouterManagement from "@/routers/RouterManagement.js";
import StudentRepository from "@/repository/StudentRepository.js";

export default {
  name: "CardAccountNav",

  components: {
    ButtonRed
  },

  props :{

  },

  data() {
    return {
      textBtnLogout: 'Đăng xuất',
      loadingButtonLogout: false,
      disableButtonLogout: false,

      /*
              {
            "data": {
                "studentId": 2500021775,
                "gender": true,
                "studentName": "Từ Quang Nhật",
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
            "message": "Student found successfully.",
            "status": 200
        }
       */

      //Student Info
      studentId: null,
      studentName: null,
      gender: null,
      genderBool: true,
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

  // Guard được Vue Router gọi trước khi rời khỏi route hiện tại
  async beforeRouteLeave(to, from, next) {
    // Kiểm tra nếu route sắp chuyển đến là trang '/login' (do người dùng bấm nút Back)
    if (to.path === '/login') {
      try {
        const studentRepository = new StudentRepository();
        let status = await studentRepository.getResponseFromLogoutRequest();

        if (status === "OK") {
          // Xóa token / session trong LocalStorage nếu cần
          // StudentLocalStorage.clean();
          // Cho phép chuyển hướng về /login
          next();
        } else {
          // Hủy chuyển hướng nếu logout không thành công
          next(false);
        }
      } catch (error) {
        console.error('Error to logout:', error);
        next(false);
      }
    } else {
      next(); // Cho phép chuyển hướng bình thường nếu đi tới trang khác
    }
  },

  async created() {
    await this.fetchDataStudentInformation();
  },

  methods: {
    async fetchDataStudentInformation() {
      const studentRepository = new StudentRepository();
      let studentFetched = await studentRepository.getStudentInformationByStudentId();
      console.log("Student from API: ", studentFetched);
      if(studentFetched) {
        this.studentId = studentFetched.studentId;
        this.studentName = studentFetched.studentName;
        this.genderBool = studentFetched.gender;
        this.gender = this.genderBool ? "Nam" : "Nữ";
        this.clazzName = studentFetched.clazzName;
      }
    },

    async handleLogout() {
      this.loadButtonLogout();
      const pathLogin = '/login';
      const studentRepository = new StudentRepository();
      let status = await studentRepository.getResponseFromLogoutRequest();

      if (status === "OK") {
        this.$router.replace({ path: pathLogin }).catch((error) => {
          console.error('Error navigating:', error);
          alert(error);
        });
      }
      this.stopLoadButtonLogout();
    },

    loadButtonLogout() {
      this.loadingButtonLogout = true;
      this.disableButtonLogout = true;
      this.textBtnLogout = "";
    },

    stopLoadButtonLogout() {
      this.loadingButtonLogout = false;
      this.disableButtonLogout = false;
      this.textBtnLogout = "Đăng xuất";
    },

    handleNavigatePersonalInformation() {
      this.$router.push({ path: '/information-student' }).catch(err => alert(err));
    },

    handleNavigateListSubjects(){
      this.$router.push({ path: '/list-courses' }).catch(err => alert(err));
    },

    handleNavigateRegisterCourses() {
      this.$router.push({ path: '/register-courses' }).catch(err => alert(err));
    },

    handleNavigateUniversitySchedule() {
      this.$router.push({ path: '/university-schedule' }).catch(err => alert(err));
    }
  }
}
</script>

<template>
  <div class="div-account-nav">
    <div class="div-card-account-image">
      <div class="card-text-account">
        <span class="text-card">Họ và tên: {{studentName}}</span>
        <span class="text-card">Mã số sinh viên: {{studentId}}</span>
        <span class="text-card">Giới tính: {{gender}}</span>
        <span class="text-card">Lớp: {{clazzName}}</span>
        <ButtonRed :disable-button="disableButtonLogout"
                   :loading-button="loadingButtonLogout"
                   :text-button="textBtnLogout"
                   class="btn-logout"
                   @click="handleLogout"
        />
      </div>
      <div class="card-image"></div>
    </div>
  </div>
</template>

<style scoped lang="scss">
</style>