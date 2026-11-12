import {TokenLogin} from "@/model/TokenLogin.js";
import SpringConfigHeader from "@/services/spring/SpringConfigHeader.js";
import { silentRefresher } from '@/services/auth/SilentTokenRefresher.js'

// Khớp các controller Spring Boot: EnrollSessionControllerWeb + EnrollmentController.
// (Tiền tố /web do baseURL của SpringConfigHeader đã gắn, giống đường dẫn ENROLLMENT đang chạy được.)
const ENDPOINTS = Object.freeze({
    ENROLLMENT: '/admin/2fa/enrollment',                    // POST sinh khóa | DELETE xóa khóa ở Redis
    ACTIVATE: '/admin/2fa/enrollment/activate',             // POST ghi MariaDB, chờ mobile xác nhận
    PENDING: '/admin/2fa/enrollment/pending',               // DELETE hủy phần chờ xác nhận
    STATUS: '/admin/2fa/status',                            // GET trạng thái 2FA
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
    // Mọi method (trừ OnPageHide) đều đi qua #requestWithSilentRefresh:
    // gặp 401 do access token hết hạn -> làm mới ngầm rồi gọi lại ĐÚNG 1 lần,
    // người dùng không thấy thông báo lỗi. Các lỗi khác (400, 409, 410...) vẫn trả về như cũ.

    /**
     * Mở modal: xin server sinh khóa Base32 mới và lưu Redis 5 phút.
     *
     * @param {TokenLogin} tokenLogin
     * @param {number} [periodSeconds] chu kỳ đổi mã 15 | 30 | 45 | 60 (bỏ trống thì server dùng 30)
     * @returns {Promise<import('axios').AxiosResponse|null>}
     *   - Thành công: HTTP 201, response.data =
     *       { success: true, message, data: { secretKey, periodSeconds, expiresInSeconds } }
     *   - Server từ chối (400, 401, 409...): trả về error.response để Repository đọc status / message.
     *   - Không gọi được server (mất mạng...): null.
     */
    async postDataToStartEnrollmentRequest(tokenLogin, periodSeconds) {
        assertInstanceOf(tokenLogin, TokenLogin, 'TokenLogin');

        try {
            return await this.#requestWithSilentRefresh(tokenLogin, () =>
                this.#getAuthorizedClient(tokenLogin).post(ENDPOINTS.ENROLLMENT, {periodSeconds}));
        } catch (error) {
            // Không window.location.reload() ở đây: axios ném lỗi cho cả 401 / 409 (lỗi nghiệp vụ bình thường),
            // reload sẽ làm mất thông báo lỗi và nháy trang. Repository đã đổi mã lỗi thành câu báo cho người dùng.
            console.error('Start enrollment error:', error);
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
            return await this.#requestWithSilentRefresh(tokenLogin, () =>
                this.#getAuthorizedClient(tokenLogin).delete(ENDPOINTS.ENROLLMENT));
        } catch (error) {
            console.error('Cancel enrollment error:', error);
            return error.response ?? null;
        }
    }

    /**
     * Bấm "Kích hoạt" trong modal: ghi khóa xuống MariaDB ở trạng thái CHỜ XÁC NHẬN
     * (totp_enabled vẫn false) và nhận 10 mã dự phòng (chỉ hiển thị một lần).
     *
     * @param tokenLogin
     * @param {number} [periodSeconds] chu kỳ admin đang chọn trong ô "Tần suất đổi mã"
     * @returns {Promise<import('axios').AxiosResponse|null>}
     *   HTTP 201: data = { success, message, data: { credentialId, periodSeconds, expiresInSeconds, backupCodes[] } }
     *   400 / 401 / 409 (đã bật 2FA) / 410 (khóa chờ hết hạn 5 phút) -> error.response; mất mạng -> null
     */
    async postDataToActivateEnrollmentRequest(tokenLogin, periodSeconds) {
        assertInstanceOf(tokenLogin, TokenLogin, 'TokenLogin');

        try {
            return await this.#requestWithSilentRefresh(tokenLogin, () =>
                this.#getAuthorizedClient(tokenLogin).post(ENDPOINTS.ACTIVATE, {periodSeconds}));
        } catch (error) {
            console.error('Activate enrollment error:', error);
            return error.response ?? null;
        }
    }

    /**
     * Trang thông tin: hỏi trạng thái 2FA (chưa cài / chờ xác nhận / đã bật). Cũng dùng để polling.
     * @returns {Promise<import('axios').AxiosResponse|null>}
     *   HTTP 200: data.data = { totpEnabled, pending, expiresInSeconds, periodSeconds }
     */
    async getDataTwoFaStatusRequest(tokenLogin) {
        assertInstanceOf(tokenLogin, TokenLogin, 'TokenLogin');

        try {
            return await this.#requestWithSilentRefresh(tokenLogin, () =>
                this.#getAuthorizedClient(tokenLogin).get(ENDPOINTS.STATUS));
        } catch (error) {
            console.error('Get 2FA status error:', error);
            return error.response ?? null;
        }
    }

    /**
     * "Hủy và cài lại" hoặc hết 10 phút chờ: xóa phần đã ghi MariaDB nhưng chưa được mobile xác nhận.
     * Không đụng 2FA đã bật. Luôn 200 nếu token hợp lệ.
     */
    async deleteDataToCancelPendingRequest(tokenLogin) {
        assertInstanceOf(tokenLogin, TokenLogin, 'TokenLogin');

        try {
            return await this.#requestWithSilentRefresh(tokenLogin, () =>
                this.#getAuthorizedClient(tokenLogin).delete(ENDPOINTS.PENDING));
        } catch (error) {
            console.error('Cancel pending enrollment error:', error);
            return error.response ?? null;
        }
    }

    /**
     * Dùng cho sự kiện tắt tab / đóng trình duyệt (pagehide, beforeunload).
     * Axios (XHR) hay bị trình duyệt hủy lúc trang đang đóng, nên dùng fetch + keepalive
     * để yêu cầu vẫn được gửi đi. Không chờ kết quả, không báo lỗi: nếu lỡ không tới
     * được server thì Redis vẫn tự xóa khóa sau 5 phút (TTL).
     *
     * Cố ý KHÔNG làm mới token ở đây: lúc trang đang đóng không kịp chờ refresh,
     * nếu access token vừa hết hạn thì TTL 5 phút của Redis đã là phương án dự phòng.
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

    /**
     * Gửi request; nếu 401 (access token hết hạn) thì làm mới ngầm rồi gửi lại đúng 1 lần.
     *
     * - `sendRequest` phải tạo client MỚI mỗi lần gọi (qua #getAuthorizedClient) để lần gửi lại
     *   lấy được access token mới trong storage.
     * - Lần gửi lại vẫn lỗi (kể cả 401) thì ném tiếp để method gọi xử lý như lỗi thường:
     *   không đệ quy vô hạn.
     * - 401 xảy ra trước khi server xử lý nghiệp vụ nên gửi lại POST/DELETE không bị trùng việc.
     * - Refresh thất bại: refresh token bị từ chối -> SilentTokenRefresher tự đưa về /login;
     *   lỗi mạng -> ném lỗi 401 gốc, method gọi trả error.response như trước.
     */
    async #requestWithSilentRefresh(tokenLogin, sendRequest) {
        const sentToken = tokenLogin.getAccessTokenFromStorage();

        try {
            return await sendRequest();
        } catch (error) {
            if (error.response?.status !== 401) {
                throw error;
            }

            const refreshed = await silentRefresher.refreshNow(sentToken);
            if (!refreshed) {
                throw error;
            }

            // Đồng bộ token trong bộ nhớ của instance TokenLogin này với storage
            tokenLogin.setAccessToken(tokenLogin.getAccessTokenFromStorage());
            tokenLogin.setRefreshToken(tokenLogin.getRefreshTokenFromStorage());

            return await sendRequest();
        }
    }
}