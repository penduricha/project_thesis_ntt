// Đọc cấu hình mạng từ .env (Vite: chỉ biến bắt đầu bằng VITE_ mới đọc được).
// Key thiếu hoặc để trống thì dùng giá trị mặc định (fallback).
const ENV = import.meta.env;

function readString(key, fallback) {
    const value = String(ENV[key] ?? "").trim();
    return value === "" ? fallback : value;
}

// Chuẩn hóa tiền tố path: luôn bắt đầu bằng "/" và không kết thúc bằng "/".
// Gõ "api/web" hay "/api/web/" trong .env vẫn ra "/api/web", tránh path bị ghép sai ("//" hoặc thiếu "/") dẫn tới 404.
function readPrefix(key, fallback) {
    const value = readString(key, fallback);
    return (value.startsWith("/") ? value : `/${value}`).replace(/\/+$/, "");
}

function readNumber(key, fallback) {
    const value = Number.parseInt(String(ENV[key] ?? "").trim(), 10);
    return Number.isNaN(value) ? fallback : value;
}

export default class NetworkConfiguration {
    constructor() {}

    // ---------- Spring Boot ----------
    // Quy ước (khớp Spring Boot): context-path /api + @RequestMapping("/web/admin") => /api/web/admin/...
    // VITE_SPRING_API_PREFIX = /api/web, các service chỉ ghi phần đuôi "/admin/...".
    // (Mobile Flutter dùng nhánh còn lại: /api/mobile + "/admin/...".)
    get springProtocol() { return readString("VITE_SPRING_PROTOCOL", "http"); }
    get springHost()     { return readString("VITE_SPRING_HOST", "localhost"); }
    get springPort()     { return readNumber("VITE_SPRING_PORT", 8080); }
    get springApiPrefix(){ return readPrefix("VITE_SPRING_API_PREFIX", "/api/web"); }

    // ---------- Node.js ----------
    get nodeJsProtocol() { return readString("VITE_NODEJS_PROTOCOL", "http"); }
    get nodeJsHost()     { return readString("VITE_NODEJS_HOST", "localhost"); }
    get nodeJsPort()     { return readNumber("VITE_NODEJS_PORT", 3000); }
    get nodeJsApiPrefix(){ return readPrefix("VITE_NODEJS_API_PREFIX", "/api/web"); }

    // ---------- Thời gian chờ (ms) ----------
    get requestTimeout() { return readNumber("VITE_REQUEST_TIMEOUT_MS", 15000); }

    // ---------- Base URL ----------
    // Call api from Spring Boot
    requestSpringBoot() {
        return `${this.springProtocol}://${this.springHost}:${this.springPort}${this.springApiPrefix}`;
    }

    // Call api from NodeJS
    requestNodeJS() {
        return `${this.nodeJsProtocol}://${this.nodeJsHost}:${this.nodeJsPort}${this.nodeJsApiPrefix}`;
    }

    // Giữ lại tên cũ để code đang gọi không bị lỗi
    requestPathFromNodeJS() {
        return this.requestNodeJS();
    }
}