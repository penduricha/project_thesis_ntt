import axios from "axios";
import NetworkConfiguration from "@/services/NetworkConfiguration.js";
import {silentRefresher} from "@/services/auth/SilentTokenRefresher.js";

const networkConfig = new NetworkConfiguration();

export default class SpringConfigHeader {

    #createClient(extraHeaders = {}) {
        return axios.create({
            baseURL: networkConfig.requestSpringBoot(),
            timeout: networkConfig.requestTimeout,
            headers: {
                "Content-Type": "application/json",
                ...extraHeaders,
            },
        });
    }

    // Không có header xác thực (đăng nhập, API công khai)
    getAPIClientNoHeaders() {
        return this.#createClient();
    }

    // JWT access/refresh token: Authorization: Bearer <token>
    getAPIClientWithHeadersToken(token) {
        return this.#createClient({
            Authorization: `Bearer ${token}`,
        });
    }

    // Client cho API cần đăng nhập, TỰ làm mới phiên ngầm để người dùng không bị gián đoạn:
    //  - Trước mỗi request: access token sắp hết hạn thì làm mới trước, rồi gắn token MỚI NHẤT trong storage
    //    (không dùng token chụp lúc tạo client, vì client có thể sống lâu hơn 5 phút của access token).
    //  - Server vẫn trả 401: làm mới ngầm rồi gửi lại ĐÚNG MỘT lần. Làm mới không được (hết phiên thật)
    //    thì trả lỗi 401 gốc cho nơi gọi xử lý.
    // KHÔNG dùng cho chính API đăng nhập / làm mới token (tránh lặp vô hạn).
    getAuthorizedAPIClient(tokenLogin) {
        const client = this.#createClient();

        client.interceptors.request.use(async (config) => {
            await silentRefresher.ensureFresh();
            const token = tokenLogin.getAccessTokenFromStorage();
            if (token) {
                config.headers.set("Authorization", `Bearer ${token}`);
            }
            return config;
        });

        client.interceptors.response.use(undefined, async (error) => {
            const config = error.config;
            if (error.response?.status !== 401
                || !config
                || config.__authRetried
                || !silentRefresher.isReady()) {
                return Promise.reject(error);
            }
            config.__authRetried = true;

            // Token request vừa gửi: nếu tab khác đã làm mới (storage có token khác) thì dùng luôn, khỏi gọi server
            const sentToken = String(config.headers?.get?.("Authorization") ?? "")
                .replace(/^Bearer\s+/i, "") || null;
            const refreshed = await silentRefresher.refreshNow(sentToken);
            if (!refreshed) {
                return Promise.reject(error);
            }
            return client.request(config); // request interceptor sẽ gắn token mới
        });

        return client;
    }

    getAuthorization() {
        return {
            withCredentials: true,
        };
    }
}
