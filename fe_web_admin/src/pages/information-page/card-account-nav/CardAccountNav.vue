<script>
import './card-account-nav.scss';
import '@/assets/main_scss/theme-color.scss';
// import AdminLocalStorage from "@/local_storage/AdminLocalStorage.js";
import ButtonRed from "@/components/button/button_red/ButtonRed.vue";
// import RouterMgmt from "@/router/RouterMgmt.js";
import AdminRepository from "@/repository/AdminRepository.js";
import MgmtDateTime from "@/assets/manage-components/MgmtDateTime.js";
import MgmtDateTimeFormat from "@/assets/manage-components/MgmtDateTimeFormat.js";

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
      gender: null,
      genderBool: true,
      dateOfBirthAdmin: null,
      dateOfBirthAdminString: null,

    }
  },

  // Guard được Vue Router gọi trước khi rời khỏi route hiện tại
  async beforeRouteLeave(to, from, next) {
    // Kiểm tra nếu route sắp chuyển đến là trang '/login' (do người dùng bấm nút Back)
    if (to.path === '/login') {
      try {
        const adminRepository = new AdminRepository();
        let status = await adminRepository.getResponseFromLogoutRequest();

        if (status === "OK") {
          // Xóa token / session trong LocalStorage nếu cần
          // AdminLocalStorage.clean();
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
    await this.fetchDataAdminInformationBasic();
  },

  methods: {
    // getFormatedDateByParamDate(dateGet) {
    //   // 1. Chuẩn hóa input thành Date object
    //   let dateReturn;
    //
    //   if (dateGet instanceof Date) {
    //     dateReturn = dateGet;
    //   } else if (typeof dateGet === 'string') {
    //     // Parse "YYYY-MM-DD" theo local time (tránh lỗi timezone)
    //     const parts = dateGet.split('-').map(Number);
    //     if (parts.length === 3 && parts.every(n => !isNaN(n))) {
    //       dateReturn = new Date(parts[0], parts[1] - 1, parts[2]);
    //     }
    //   }
    //
    //   // 2. Kiểm tra hợp lệ
    //   if (!dateReturn || isNaN(dateReturn.getTime())) {
    //     console.warn('Invalid date input:', dateGet);
    //     return '';
    //   }
    //
    //   // 3. Format
    //   const day   = dateReturn.getDate();
    //   const month = dateReturn.getMonth() + 1;
    //   const year  = dateReturn.getFullYear();
    //
    //   return `${day}/${month}/${year}`;
    // },

    async fetchDataAdminInformationBasic() {
      const adminRepository = new AdminRepository();
      let adminInformationBasic = await adminRepository.getAdminInformationBasicByAdminId();
      console.log(adminInformationBasic);
      if(adminInformationBasic) {
        this.adminId = adminInformationBasic.adminId;
        this.adminName = adminInformationBasic.adminName;
        this.genderBool = adminInformationBasic.gender;
        this.gender = this.genderBool ? "Nam" : "Nữ";
        this.dateOfBirthAdmin = adminInformationBasic.dateOfBirthAdmin;

        const mgmtDateTimeFormat = new MgmtDateTimeFormat();
        this.dateOfBirthAdminString = mgmtDateTimeFormat.getFormatedDateByParamDate(this.dateOfBirthAdmin);
      }
    },

    async handleLogout() {
      this.loadButtonLogout();
      const pathLogin = '/login';

      const adminRepository = new AdminRepository();
      let status = await adminRepository.getResponseFromLogoutRequest();

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
      this.$router.push({ path: '/information-admin' }).catch(err => alert(err));
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
        <span class="text-card">Họ và tên: {{adminName}}</span>
        <span class="text-card">Mã quản trị viên: {{adminId}}</span>
        <span class="text-card">Giới tính: {{gender}}</span>
        <span class="text-card">Ngày sinh: {{dateOfBirthAdminString}}</span>
        <ButtonRed :disable-button="disableButtonLogout"
                   :loading-button="loadingButtonLogout"
                   :text-button="textBtnLogout"
                   class="btn-logout btn-logout-border-radius"
                   @click="handleLogout"
        />
      </div>
      <div class="card-image"></div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.btn-logout-border-radius {
  border-radius: 4px;
}
</style>