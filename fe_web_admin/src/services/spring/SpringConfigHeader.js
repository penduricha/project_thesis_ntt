import axios from "axios";
import NetworkConfiguration from "@/services/NetworkConfiguration.js";

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

    // Giữ nguyên hành vi cũ cho các nơi đang dùng getAuthorization()
    getAuthorization() {
        return {
            auth: {},
            withCredentials: true,
        };
    }
}