import AdminLocalStorage from "@/local_storage/AdminLocalStorage.js";
import {AdminAccount} from "@/model/AdminAccount.js";
import {AdminService} from "@/services/spring/AdminService.js";

import {TokenLogin} from "@/model/TokenLogin.js";
import RouterManagement from "@/routers/RouterManagement.js";
import {LoginService} from "@/services/spring/LoginService.js";

const adminLocalStorage = new AdminLocalStorage();

const adminService = new AdminService();

const loginService = new LoginService();

export default class AdminRepository {
    accessToken;
    refreshToken;

    async getResponseFromLoginRequest(adminId, password) {
        let statusLogin = "";
        const adminAccount = new AdminAccount(adminId, password);
        try{
            let dataReturn = await adminService.postDataToLoginRequest(adminAccount);
            console.log(dataReturn);
            console.log("Data:", dataReturn.data);

            if(dataReturn.data.success) {
                //save localstorage
                let accessToken = String(dataReturn.data.data.accessToken);
                let refreshToken = String(dataReturn.data.data.refreshToken);
                const tokenLogin = new TokenLogin(accessToken, refreshToken);

                console.log(tokenLogin.toString());

                tokenLogin.setAccessTokenToStorage();
                tokenLogin.setRefreshTokenToStorage();

                adminLocalStorage.setAdminIdToLocalStorage(adminAccount.getAdminId());

                statusLogin = "OK";
            } else {
                statusLogin = "Failed";
            }
        } catch (e) {
            statusLogin = "Exception system:";
            console.error(statusLogin);
            console.error(e);
            //alert(e);

        }
        return statusLogin;
        //save localstorage
    }

    async getResponseFromLogoutRequest() {
        let statusLogout = "";

        try {
            const tokenLogin = new TokenLogin();
            let dataReturn = await adminService.postDataToLogoutRequest(tokenLogin);

            if(dataReturn.data.status === 200) {
                adminLocalStorage.removeAdminIdFromLocalStorage();
                tokenLogin.removeAccessTokenAndRefreshTokenFromStorage();

                const routerManagement = new RouterManagement();
                routerManagement.removePathFromLocalStorage();
                routerManagement.removePathFromSessionStorage();

                statusLogout = "OK";
            } else {
                statusLogout = "Failed"
            }
        } catch (e) {
            statusLogout = "Exception system:";
            console.error(statusLogout);
            console.error(e);
            //alert(e);
        }
        return statusLogout;
    }

    async getAdminInformationByAdminId() {
        // Flag login auto
        let adminInfo = {};

        let statusLoginAuto = await loginService.getStatusAutoLogin();

        let adminId = adminLocalStorage.getAdminIdToLocalStorage();
        if(adminId && statusLoginAuto) {
            adminInfo = await adminService.getAdminInformationRequestByAdminId(adminId);
            if(adminInfo.data.status === 200) {
                console.log("Data fetched admin information:", JSON.stringify(adminInfo.data.data));
            }
        }
        return adminInfo.data.data;
    }

    // async getAdminInformationByAdminId_Admin() {
    //     let adminInfo_Admin = {};
    //     let adminInfo = await this.getAdminInformationByAdminId();
    //     if(adminInfo) {
    //         // const data = {
    //         //     "adminId": adminAccount.getAdminId(),
    //         //     "password": adminAccount.getPassword(),
    //         // };
    //         let { course, ...adminInfo } = adminInfo;
    //         adminInfo_Admin = adminInfo;
    //     }
    //     return adminInfo_Admin;
    // }
}