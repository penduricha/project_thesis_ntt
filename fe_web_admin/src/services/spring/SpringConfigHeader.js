import axios from "axios";
import AxiosConfiguration from "@/services/AxiosConfiguration.js";

const axiosConfig = new AxiosConfiguration();

export default class SpringConfigHeader {

    #authUsername;
    #authPassword;

    constructor() {
        this.#authUsername = import.meta.env.VITE_AUTH_USERNAME || "";
        this.#authPassword = import.meta.env.VITE_AUTH_PASSWORD || "";
    }

    getAPIClientNoHeaders(){
        return axios.create({
            baseURL: axiosConfig.requestPathFromSpringBoot(),
            headers: { 'Content-Type': 'application/json', }
        });
    }

    // getAPIClientWithHeadersIsAccessToken(token) {
    //     return axios.create({
    //         baseURL: axiosConfig.requestPathFromSpringBoot(),
    //         auth: {
    //             username: this.#authUsername,
    //             password: this.#authPassword
    //         },
    //         // auth: {},
    //         headers: {
    //             'Authorization': `Bearer ${token}`,
    //             'Content-Type': 'application/json'
    //         }
    //     });
    // }

    //Ko username and password
    // getAPIClientWithHeadersIsAccessToken(token) {
    //     return axios.create({
    //         baseURL: axiosConfig.requestPathFromSpringBoot(),
    //         // Giữ Basic Auth chuẩn để không bị lỗi CORS
    //         // auth: {
    //         //     username: this.#authUsername,
    //         //     password: this.#authPassword,
    //         // },
    //         // Truyền Bearer Token vào header tiêu chuẩn
    //         headers: {
    //             'Authorization': `Bearer ${token}`,
    //             'Content-Type': 'application/json'
    //         }
    //     });
    // }

    getAPIClientWithHeadersToken(token) {
        // Mã hóa Basic Auth (username:password) sang Base64
        //const basicAuthBase64 = btoa(`${this.#authUsername}:${this.#authPassword}`);

        return axios.create({
            baseURL: axiosConfig.requestPathFromSpringBoot(),
            headers: {
                'Authorization': `Bearer ${token}`,           // JWT Token
                //'X-Client-Auth': `Basic ${basicAuthBase64}`,    // Basic Auth đẩy vào Header riêng (tránh đè Authorization)
                'Content-Type': 'application/json'
            }
        });
    }

    getAuthorization() {
        // Nếu cần HTTP Basic Auth
        return {
            // auth: {
            //     username: this.#authUsername,
            //     password: this.#authPassword,
            // },
            auth: {},
            withCredentials: true,
        };

        // Nếu không cần auth
        //return {};
    }

}