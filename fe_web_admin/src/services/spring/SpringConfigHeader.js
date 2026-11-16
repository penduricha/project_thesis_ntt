import axios from "axios";
import NetworkConfiguration from "@/services/NetworkConfiguration.js";
import {silentRefresher} from "@/services/auth/SilentTokenRefresher.js";

const networkConfig = new NetworkConfiguration();

export default class SpringConfigHeader {

    #authUsername;
    #authPassword;

    constructor() {
        this.#authUsername = String(import.meta.env.VITE_AUTH_USERNAME ?? "").trim();
        this.#authPassword = String(import.meta.env.VITE_AUTH_PASSWORD ?? "").trim();
    }

    // Có cấu hình Basic Auth trong .env hay không
    #hasBasicAuth() {
        return this.#authUsername !== "" || this.#authPassword !== "";
    }

    #basicAuthHeader() {
        return `Basic ${btoa(`${this.#authUsername}:${this.#authPassword}`)}`;
    }

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

    // JWT access token: Authorization: Bearer <token>
    // withBasicAuth = true thì đẩy thêm Basic Auth vào header riêng X-Client-Auth
    // (tránh đè Authorization).
    getAPIClientWithHeadersToken(token, withBasicAuth = false) {
        return this.#createClient({
            Authorization: `Bearer ${token}`,
            ...(withBasicAuth && this.#hasBasicAuth()
                ? { "X-Client-Auth": this.#basicAuthHeader() }
                : {}),
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

    // Giữ nguyên hành vi cũ cho các nơi đang dùng getAuthorization()
    getAuthorization() {
        return {
            auth: {},
            withCredentials: true,
        };
    }
}