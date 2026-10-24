import {TokenLogin} from "@/model/TokenLogin.js";

import {AdminService} from "@/services/spring/AdminService.js";

export class LoginService {
    async getStatusAutoLogin() {
        // Dk login thanh cong
        const tokenLogin = new TokenLogin();

        const studentService = new AdminService();

        // Flag login auto
        let statusLoginAuto = false;

        let accessToken = tokenLogin.getAccessTokenFromStorage();
        let refreshToken = tokenLogin.getRefreshTokenFromStorage();
        tokenLogin.setAccessToken(accessToken);
        tokenLogin.setRefreshToken(refreshToken);

        if(refreshToken) {
            statusLoginAuto = await studentService.getDataToAutoLoginAccessToken(tokenLogin);
        }
        return statusLoginAuto;
    }
}