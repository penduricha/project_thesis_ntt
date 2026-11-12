import {TokenLogin} from "@/model/TokenLogin.js";
import SpringConfigHeader from "@/services/spring/SpringConfigHeader.js";

const ENDPOINTS = Object.freeze({
    ENROLLMENT: '/admin/2fa/enrollment',
});

function assertInstanceOf(value, expectedClass, label) {
    if (!(value instanceof expectedClass)) {
        throw new Error(`Invalid ${label} instance`);
    }
}

export class EnrollmentService {
    constructor(springConfigHeader = new SpringConfigHeader()) {
        this.springConfigHeader = springConfigHeader;
    }

    // ---------- Public API ----------

    /**
     * Bấm "Kích hoạt 2FA" (mở modal): xin server sinh khóa Base32 mới và lưu Redis 5 phút.
     *
     * @param {TokenLogin} tokenLogin
     * @param {number} [periodSeconds] chu kỳ đổi mã 15 | 30 | 60 (bỏ trống thì server dùng 30)
     * @returns {Promise<import('axios').AxiosResponse|null>}
     *   - Thành công: HTTP 201, response.data =
     *       { success: true, message, data: { secretKey, periodSeconds, expiresInSeconds } }
     *   - Server từ chối (401, 400...): trả về error.response để Repository đọc status / message.
     *   - Không gọi được server (mất mạng...): null.
     */
    async postDataToStartEnrollmentRequest(tokenLogin, periodSeconds) {
        assertInstanceOf(tokenLogin, TokenLogin, 'TokenLogin');

        try {
            return await this.#getAuthorizedClient(tokenLogin)
                .post(ENDPOINTS.ENROLLMENT, {periodSeconds});
        } catch (error) {
            console.error('Start enrollment error:', error);

            //Load lại để ổn định
            window.location.reload()
            return error.response ?? null;
        }
    }

    /**
     * Đóng modal / rời trang trong khi vẫn dùng app: xóa khóa đang chờ trên Redis.
     * Gọi nhiều lần vẫn an toàn (server luôn trả 200 nếu token hợp lệ).
     *
     * @returns {Promise<import('axios').AxiosResponse|null>} response.data.data.deleted = true | false
     */
    async deleteDataToCancelEnrollmentRequest(tokenLogin) {
        assertInstanceOf(tokenLogin, TokenLogin, 'TokenLogin');

        try {
            return await this.#getAuthorizedClient(tokenLogin).delete(ENDPOINTS.ENROLLMENT);
        } catch (error) {
            console.error('Cancel enrollment error:', error);
            return error.response ?? null;
        }
    }

    /**
     * Dùng cho sự kiện tắt tab / đóng trình duyệt (pagehide, beforeunload).
     * Axios (XHR) hay bị trình duyệt hủy lúc trang đang đóng, nên dùng fetch + keepalive
     * để yêu cầu vẫn được gửi đi. Không chờ kết quả, không báo lỗi: nếu lỡ không tới
     * được server thì Redis vẫn tự xóa khóa sau 5 phút (TTL).
     */
    deleteDataToCancelEnrollmentOnPageHide(tokenLogin) {
        assertInstanceOf(tokenLogin, TokenLogin, 'TokenLogin');

        const accessToken = tokenLogin.getAccessTokenFromStorage();
        if (!accessToken) {
            return;
        }

        const baseUrl = this.springConfigHeader.getAPIClientNoHeaders().defaults.baseURL ?? '';
        fetch(`${baseUrl}${ENDPOINTS.ENROLLMENT}`, {
            method: 'DELETE',
            headers: {Authorization: `Bearer ${accessToken}`},
            keepalive: true,
        }).catch(() => {
            // best-effort: bỏ qua lỗi, đã có TTL dự phòng
        });
    }

    // ---------- Private helpers ----------

    // Client gắn sẵn "Authorization: Bearer <access token mới nhất trong storage>"
    #getAuthorizedClient(tokenLogin) {
        return this.springConfigHeader
            .getAPIClientWithHeadersToken(tokenLogin.getAccessTokenFromStorage());
    }
}