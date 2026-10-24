import {AdminAccount} from "@/model/AdminAccount.js";
import SpringConfigHeader from "@/services/spring/SpringConfigHeader.js";
import {TokenLogin} from "@/model/TokenLogin.js";
import AdminLocalStorage from "@/local_storage/AdminLocalStorage.js";

const springConfigHeader = new SpringConfigHeader();

const adminLocalStorage = new AdminLocalStorage();

export class AdminService {
    async postDataToLoginRequest(adminAccount) {
        if (!(adminAccount instanceof AdminAccount)) {
            throw new Error("Invalid AdminAccount instance");
        }

        const data = {
            "adminId": adminAccount.getAdminId(),
            "password": adminAccount.getPassword(),
        };

        const path = '/admin/auth/login';
        return springConfigHeader.getAPIClientNoHeaders().post(path, data, springConfigHeader.getAuthorization());
    }

    async postDataToLogoutRequest(tokenLogin) {

        if (!(tokenLogin instanceof TokenLogin)) {
            throw new Error("Invalid TokenLogin instance");
        }

        const data = {
            "accessToken": tokenLogin.getAccessTokenFromStorage(),
            "refreshToken": tokenLogin.getRefreshTokenFromStorage(),
        };

        const path = '/admin/auth/logout';
        return springConfigHeader.getAPIClientNoHeaders().post(path, data, springConfigHeader.getAuthorization());
    }

    async getAdminInformationRequestByAdminId(adminId) {
        let dataResponse = null;
        try {
            const path = `/admin/information?adminId=${adminId}`;
            dataResponse = await springConfigHeader.getAPIClientNoHeaders().get(path, springConfigHeader.getAuthorization());
            console.log(dataResponse);
        } catch(e) {
            console.error(e);
            //alert(e);
        }
        return dataResponse;
    }


    async getDataToAutoLoginAccessToken(tokenLogin) {
        let statusAutoLogin = false;

        if (!(tokenLogin instanceof TokenLogin)) {
            throw new Error("Invalid TokenLogin instance");
        }

        const pathAccessToken = "/admin/auth/login/auto/access-token";
        const pathRefreshToken = "/admin/auth/login/auto/refresh-token";

        try {
            // 1. Thử gọi API bằng Access Token hiện tại
            // Instance đã có sẵn cả 'Authorization: Bearer <token>' và 'X-Client-Auth: Basic <base64>'
            // Tham số 2 của POST là Body (truyền {} nếu API không yêu cầu Body)
            let dataResponse = await springConfigHeader
                .getAPIClientWithHeadersToken(tokenLogin.getAccessToken())
                .get(pathAccessToken);

            if (dataResponse.status === 200) {
                let adminId = dataResponse.data.data.adminId;
                adminLocalStorage.setAdminIdToLocalStorage(Number(adminId));
                return true;
            }
        } catch (error) {
            // 2. Bắt lỗi 401 khi Access Token bị hết hạn
            const status = error.response?.status;

            console.log("Error object caught:", error);
            console.log("Response status:", error.response?.status);

            if (status === 401) {
                console.warn('Access Token is expired. Renewing tokens...');
                console.log('Exception about err: '+error);

                try {
                    // Gọi API Refresh Token - Truyền Refresh Token vào Instance!
                    let dataResponseRefreshToken = await springConfigHeader
                        .getAPIClientWithHeadersToken(tokenLogin.getRefreshToken())
                        .get(pathRefreshToken);

                    if (dataResponseRefreshToken && dataResponseRefreshToken.status === 200) {
                        // Cập nhật Token mới vào Object & Storage
                        let accessTokenNew = dataResponseRefreshToken.data.data.accessToken;
                        let refreshTokenNew = dataResponseRefreshToken.data.data.refreshToken;

                        tokenLogin.setAccessToken(accessTokenNew);
                        tokenLogin.setRefreshToken(refreshTokenNew);
                        tokenLogin.setAccessTokenToStorage();
                        tokenLogin.setRefreshTokenToStorage();

                        // 3. Retry gọi lại API cũ bằng Access Token MỚI
                        let retryResponse = await springConfigHeader
                            .getAPIClientWithHeadersToken(accessTokenNew)
                            .get(pathAccessToken);

                        if (retryResponse.status === 200) {
                            //set lai ID moi
                            let adminId = retryResponse.data.data.adminId;
                            adminLocalStorage.setAdminIdToLocalStorage(Number(adminId));
                            statusAutoLogin = true;
                        }
                    } else {
                        tokenLogin.removeAccessTokenAndRefreshTokenFromStorage();
                        adminLocalStorage.removeAdminIdFromLocalStorage();
                    }
                } catch (refreshError) {
                    console.error('Refresh token failed:', refreshError);
                    tokenLogin.removeAccessTokenAndRefreshTokenFromStorage();
                    adminLocalStorage.removeAdminIdFromLocalStorage();
                }
            } else {
                console.error('Auto login error:', error);
                tokenLogin.removeAccessTokenAndRefreshTokenFromStorage();
                adminLocalStorage.removeAdminIdFromLocalStorage();
            }
        }
        return statusAutoLogin;
    }
}