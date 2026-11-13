import {TokenLogin} from "@/model/TokenLogin.js";
import SpringConfigHeader from "@/services/spring/SpringConfigHeader.js";

// Khớp các controller Spring Boot: EnrollSessionControllerWeb + EnrollmentController.
// (Tiền tố /web do baseURL của SpringConfigHeader đã gắn, giống đường dẫn ENROLLMENT đang chạy được.)
const ENDPOINTS = Object.freeze({
    ENROLLMENT: '/admin/2fa/enrollment',                    // POST sinh khóa | DELETE xóa khóa ở Redis
    ACTIVATE: '/admin/2fa/enrollment/activate',             // POST ghi MariaDB, chờ mobile xác nhận
    PENDING: '/admin/2fa/enrollment/pending',               // DELETE hủy phần chờ xác nhận
    STATUS: '/admin/2fa/status',                            // GET trạng thái 2FA
    EVENTS: '/admin/2fa/events',                            // GET luồng SSE: server báo đổi trạng thái 2FA
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
            return await this.#getAuthorizedClient(tokenLogin)
                .post(ENDPOINTS.ENROLLMENT, {periodSeconds});
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
            return await this.#getAuthorizedClient(tokenLogin).delete(ENDPOINTS.ENROLLMENT);
        } catch (error) {
            console.error('Cancel enrollment error:', error);
            return error.response ?? null;
        }
    }

    /**
     * Bấm "Kích hoạt" trong modal: ghi khóa xuống MariaDB ở trạng thái CHỜ XÁC NHẬN
     * (totp_enabled vẫn false) và nhận 10 mã dự phòng (chỉ hiển thị một lần).
     *
     * @param {number} [periodSeconds] chu kỳ admin đang chọn trong ô "Tần suất đổi mã"
     * @returns {Promise<import('axios').AxiosResponse|null>}
     *   HTTP 201: data = { success, message, data: { credentialId, periodSeconds, expiresInSeconds, backupCodes[] } }
     *   400 / 401 / 409 (đã bật 2FA) / 410 (khóa chờ hết hạn 5 phút) -> error.response; mất mạng -> null
     */
    async postDataToActivateEnrollmentRequest(tokenLogin, periodSeconds) {
        assertInstanceOf(tokenLogin, TokenLogin, 'TokenLogin');

        try {
            return await this.#getAuthorizedClient(tokenLogin)
                .post(ENDPOINTS.ACTIVATE, {periodSeconds});
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
            return await this.#getAuthorizedClient(tokenLogin).get(ENDPOINTS.STATUS);
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
            return await this.#getAuthorizedClient(tokenLogin).delete(ENDPOINTS.PENDING);
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

    /**
     * Mở luồng SSE (text/event-stream) và ĐỌC cho tới khi luồng đóng hoặc bị abort.
     * Dùng fetch thay cho EventSource vì EventSource không gắn được header Authorization.
     *
     * @param {TokenLogin} tokenLogin
     * @param {{signal: AbortSignal, onOpen: function(): void,
     *          onEvent: function(string, string): void}} handlers
     *   onEvent(tên sự kiện, chuỗi data); dòng comment (heartbeat) được bỏ qua.
     * @returns {Promise<{opened: boolean, status: number|null}>}
     *   opened=false: server từ chối (401...) hoặc không nối được. Luồng đã mở rồi mới đóng: opened=true.
     *   Bị abort thì ném AbortError cho nơi gọi.
     */
    async readStatusStream(tokenLogin, {signal, onOpen, onEvent}) {
        assertInstanceOf(tokenLogin, TokenLogin, 'TokenLogin');

        const accessToken = tokenLogin.getAccessTokenFromStorage();
        if (!accessToken) {
            return {opened: false, status: 401};
        }

        const baseUrl = this.springConfigHeader.getAPIClientNoHeaders().defaults.baseURL ?? '';
        const response = await fetch(`${baseUrl}${ENDPOINTS.EVENTS}`, {
            method: 'GET',
            headers: {Authorization: `Bearer ${accessToken}`, Accept: 'text/event-stream'},
            cache: 'no-store',
            signal,
        });
        if (!response.ok || !response.body) {
            return {opened: false, status: response.status};
        }

        onOpen?.();

        const reader = response.body.getReader();
        const decoder = new TextDecoder('utf-8');
        let buffer = '';
        try {
            for (;;) {
                const {done, value} = await reader.read();
                if (done) break;

                buffer += decoder.decode(value, {stream: true}).replace(/\r\n/g, '\n');
                // Mỗi sự kiện kết thúc bằng một dòng trống
                let end;
                while ((end = buffer.indexOf('\n\n')) !== -1) {
                    const block = buffer.slice(0, end);
                    buffer = buffer.slice(end + 2);
                    const parsed = EnrollmentService.#parseSseBlock(block);
                    if (parsed) onEvent?.(parsed.event, parsed.data);
                }
            }
        } finally {
            reader.cancel().catch(() => {});
        }
        return {opened: true, status: response.status};
    }

    // ---------- Private helpers ----------

    // Một khối SSE: các dòng "event: x", "data: y", ":comment". Trả null nếu chỉ có comment / rỗng.
    static #parseSseBlock(block) {
        let event = 'message';
        const dataLines = [];
        for (const line of block.split('\n')) {
            if (line === '' || line.startsWith(':')) continue;
            const colon = line.indexOf(':');
            const field = colon === -1 ? line : line.slice(0, colon);
            const value = colon === -1 ? '' : line.slice(colon + 1).replace(/^ /, '');
            if (field === 'event') event = value;
            else if (field === 'data') dataLines.push(value);
        }
        return dataLines.length === 0 && event === 'message' ? null : {event, data: dataLines.join('\n')};
    }

    // Client gắn sẵn "Authorization: Bearer <access token mới nhất trong storage>"
    #getAuthorizedClient(tokenLogin) {
        return this.springConfigHeader
            .getAPIClientWithHeadersToken(tokenLogin.getAccessTokenFromStorage());
    }
}