import {EnrollmentService} from "./EnrollmentService.js";
import { t } from "../i18n/index.js";

const HTTP_OK = 200;
const HTTP_CREATED = 201;
const HTTP_UNAUTHORIZED = 401;
const HTTP_CONFLICT = 409;
const HTTP_GONE = 410;
const HTTP_PRECONDITION_REQUIRED = 428;

// Giá trị trạng thái 2FA mà InformationPage dùng (khớp TWO_FA_STATUS trong trang).
export const TWO_FA_STATUS = Object.freeze({
    NOT_ENABLED: 'NOT_ENABLED',
    PENDING: 'PENDING',
    ENABLED: 'ENABLED',
});

const MESSAGES = Object.freeze({
    DEFAULT_ERROR: 'enrollment.startError',
    SESSION_EXPIRED: 'apiError.sessionExpired',
    NETWORK_ERROR: 'apiError.network',
    ACTIVATE_ERROR: 'enrollment.activateError',
    ALREADY_ENABLED: 'enrollment.inProgress',
    KEY_EXPIRED: 'enrollment.secretExpired',
    UPDATE_REQUIRED: 'enrollment.updateRequired',
    STATUS_ERROR: 'enrollment.statusError',
});

function buildFailure(status, message) {
    return {success: false, status, message, data: null};
}

/**
 * Lớp trung gian giữa Service (gói tin axios thô) và Vue Component:
 * đọc gói tin của Spring Boot rồi trả về đối tượng gọn, Component không phải biết
 * cấu trúc response hay mã HTTP.
 */
export class EnrollmentRepository {
    constructor(enrollmentService = new EnrollmentService()) {
        this.enrollmentService = enrollmentService;
    }

    /**
     * Mở modal: xin khóa Base32 mới.
     *
     * @returns {Promise<{success: boolean, status: number|null, message: string,
     *   data: {secretKey: string, periodSeconds: number, expiresInSeconds: number}|null}>}
     *   success = true  -> data chứa khóa để hiển thị trong modal.
     *   success = false -> message là câu báo lỗi đã sẵn để hiện cho người dùng.
     */
    async startEnrollment(tokenLogin, periodSeconds) {
        const response = await this.enrollmentService
            .postDataToStartEnrollmentRequest(tokenLogin, periodSeconds);

        if (response === null) {
            return buildFailure(null, t(MESSAGES.NETWORK_ERROR));
        }

        const body = response.data;
        const data = body?.data;

        if (response.status === HTTP_CREATED
            && body?.success === true
            && typeof data?.secretKey === 'string' && typeof data?.generation === 'string') {
            return {
                success: true,
                status: response.status,
                message: body.message,
                data: {
                    secretKey: data.secretKey,
                    generation: data.generation,
                    periodSeconds: data.periodSeconds,
                    expiresInSeconds: data.expiresInSeconds,
                },
            };
        }

        if (response.status === HTTP_PRECONDITION_REQUIRED ||
            (response.status === HTTP_CREATED && body?.success === true && typeof data?.generation !== 'string')) {
            return buildFailure(response.status, t(MESSAGES.UPDATE_REQUIRED));
        }
        if (response.status === HTTP_UNAUTHORIZED) {
            return buildFailure(response.status, t(MESSAGES.SESSION_EXPIRED));
        }
        if (response.status === HTTP_CONFLICT) {
            return buildFailure(response.status, t(MESSAGES.ALREADY_ENABLED));
        }
        return buildFailure(response.status, body?.message ?? t(MESSAGES.DEFAULT_ERROR));
    }

    /**
     * Đóng modal / chuyển trang trong app: xóa khóa đang chờ ở Redis.
     * @returns {Promise<boolean>} true nếu server xác nhận (kể cả khi khóa đã hết hạn từ trước)
     */
    async cancelEnrollment(tokenLogin, generation) {
        const response = await this.enrollmentService
            .deleteDataToCancelEnrollmentRequest(tokenLogin, generation);

        return response?.status === HTTP_OK;
    }

    /**
     * Tắt tab / đóng trình duyệt: gửi yêu cầu xóa theo kiểu "bắn rồi bỏ" (không chờ kết quả).
     * Gọi trong sự kiện pagehide.
     */
    cancelEnrollmentOnPageHide(tokenLogin, generation) {
        this.enrollmentService.deleteDataToCancelEnrollmentOnPageHide(tokenLogin, generation);
    }

    /**
     * Bấm "Kích hoạt" trong modal: ghi khóa xuống MariaDB (chờ mobile xác nhận).
     *
     * @returns {Promise<{success: boolean, status: number|null, message: string, expired?: boolean,
     *   data: {expiresInSeconds: number, periodSeconds: number}|null}>}
     *   expired = true khi khóa chờ ở Redis đã hết 5 phút (HTTP 410): modal nên xin khóa mới.
     */
    async activateEnrollment(tokenLogin, periodSeconds, generation) {
        const response = await this.enrollmentService
            .postDataToActivateEnrollmentRequest(tokenLogin, periodSeconds, generation);

        if (response === null) {
            return buildFailure(null, t(MESSAGES.NETWORK_ERROR));
        }

        const body = response.data;
        const data = body?.data;

        if (response.status === HTTP_CREATED && body?.success === true &&
            Number.isSafeInteger(data?.credentialId) && data.credentialId > 0) {
            return {
                success: true,
                status: response.status,
                message: body.message,
                data: {
                    expiresInSeconds: data?.expiresInSeconds,
                    periodSeconds: data?.periodSeconds,
                    credentialId: data?.credentialId,
                },
            };
        }

        if (response.status === HTTP_PRECONDITION_REQUIRED) {
            return buildFailure(response.status, t(MESSAGES.UPDATE_REQUIRED));
        }
        if (response.status === HTTP_UNAUTHORIZED) {
            return buildFailure(response.status, t(MESSAGES.SESSION_EXPIRED));
        }
        if (response.status === HTTP_CONFLICT) {
            return buildFailure(response.status, t(MESSAGES.ALREADY_ENABLED));
        }
        if (response.status === HTTP_GONE) {
            return {...buildFailure(response.status, t(MESSAGES.KEY_EXPIRED)), expired: true};
        }
        return buildFailure(response.status, body?.message ?? t(MESSAGES.ACTIVATE_ERROR));
    }

    async reconcileEnrollment(tokenLogin, generation) {
        const response = await this.enrollmentService.postReconcileEnrollmentRequest(tokenLogin, generation);
        const data = response?.data?.data;
        if (response?.status !== HTTP_OK || response?.data?.success !== true ||
            !['CANCELLED', 'COMMITTED'].includes(data?.outcome) || typeof data?.claimReleased !== 'boolean') {
            return {success: false};
        }
        return {success: true, outcome: data.outcome, claimReleased: data.claimReleased};
    }

    /**
     * Trang thông tin hỏi trạng thái 2FA (cũng dùng để polling).
     *
     * @returns {Promise<{success: boolean, httpStatus: number|null, message: string,
     *   status: 'NOT_ENABLED'|'PENDING'|'ENABLED'|null, pendingExpiresInSeconds: number}>}
     *   Ở đây `status` là TRẠNG THÁI 2FA (khớp TWO_FA_STATUS của InformationPage), không phải mã HTTP.
     *   pendingExpiresInSeconds: số giây còn lại để mobile xác nhận (chỉ có nghĩa khi PENDING).
     */
    async getTwoFaStatus(tokenLogin) {
        const response = await this.enrollmentService.getDataTwoFaStatusRequest(tokenLogin);

        const failure = (httpStatus, message) => ({
            success: false, httpStatus, message, status: null, pendingExpiresInSeconds: 0,
        });

        if (response === null) {
            return failure(null, t(MESSAGES.NETWORK_ERROR));
        }

        const body = response.data;
        const data = body?.data;

        if (response.status === HTTP_OK && body?.success === true && typeof data?.totpEnabled === 'boolean') {
            let status = TWO_FA_STATUS.NOT_ENABLED;
            if (data.totpEnabled) status = TWO_FA_STATUS.ENABLED;
            else if (data.pending === true) status = TWO_FA_STATUS.PENDING;

            return {
                success: true,
                httpStatus: response.status,
                message: body.message,
                status,
                pendingExpiresInSeconds: Number(data.expiresInSeconds) || 0,
                credentialId: data.pending === true && Number.isSafeInteger(data.credentialId) && data.credentialId > 0
                    ? data.credentialId : null,
            };
        }
        if (response.status === HTTP_UNAUTHORIZED) {
            return failure(response.status, t(MESSAGES.SESSION_EXPIRED));
        }
        return failure(response.status, body?.message ?? t(MESSAGES.STATUS_ERROR));
    }

    /**
     * "Hủy và cài lại" hoặc hết 10 phút chờ: xóa phần chờ xác nhận ở MariaDB.
     * @returns {Promise<boolean>} true nếu server xác nhận đã xóa
     */
    async cancelPendingEnrollment(tokenLogin, credentialId) {
        if (!Number.isSafeInteger(credentialId) || credentialId <= 0) return false;
        const response = await this.enrollmentService.deleteDataToCancelPendingRequest(tokenLogin, credentialId);
        return response?.status === HTTP_OK && response?.data?.success === true &&
            response?.data?.data?.cancelled === true && response?.data?.data?.credentialId === credentialId;
    }

    /**
     * Lắng nghe server báo đổi trạng thái 2FA (SSE). Tự nối lại khi đứt, chờ tăng dần 1s -> 30s.
     * Mỗi lần (nối lại) mở được luồng đều gọi onChange() một lần để Component hỏi lại trạng thái,
     * phòng sự kiện xảy ra đúng lúc đang đứt kết nối.
     *
     * @param {TokenLogin} tokenLogin
     * @param {{onChange: function(string|null): void,
     *          onConnectionChange?: function(boolean): void}} handlers
     *   onChange(status): status = 'PENDING' | 'ENABLED' | 'NOT_ENABLED' (null khi chỉ là "hỏi lại cho chắc")
     * @returns {function(): void} hàm dừng: gọi khi Component bị gỡ
     */
    watchTwoFaStatus(tokenLogin, {onChange, onConnectionChange}) {
        const controller = new AbortController();
        const MIN_DELAY_MS = 1000;
        const MAX_DELAY_MS = 30000;
        const UNAUTHORIZED_DELAY_MS = 60000; // token hết hạn: chờ axios làm mới token ở nơi khác, đừng dồn dập

        const sleep = (ms) => new Promise((resolve) => {
            const timer = setTimeout(resolve, ms);
            controller.signal.addEventListener('abort', () => {
                clearTimeout(timer);
                resolve();
            }, {once: true});
        });

        const run = async () => {
            let delay = MIN_DELAY_MS;
            while (!controller.signal.aborted) {
                let waitMs = delay;
                try {
                    const result = await this.enrollmentService.readStatusStream(tokenLogin, {
                        signal: controller.signal,
                        onOpen: () => {
                            delay = MIN_DELAY_MS; // nối được rồi: lần đứt sau chờ lại từ 1s
                            onConnectionChange?.(true);
                            onChange(null);
                        },
                        onEvent: (event, data) => {
                            if (event !== '2fa-status') return; // bỏ "ready" và sự kiện lạ
                            let status = null;
                            try {
                                status = JSON.parse(data)?.status ?? null;
                            } catch {
                                // data hỏng: vẫn báo để Component hỏi lại server
                            }
                            onChange(status);
                        },
                    });
                    if (!result.opened && result.status === HTTP_UNAUTHORIZED) {
                        waitMs = UNAUTHORIZED_DELAY_MS;
                    }
                } catch (error) {
                    if (controller.signal.aborted) break; // Component bị gỡ
                    console.warn('2FA status stream interrupted.');
                }
                onConnectionChange?.(false);
                if (controller.signal.aborted) break;
                await sleep(waitMs);
                delay = Math.min(delay * 2, MAX_DELAY_MS);
            }
        };

        run().then(r => {});
        return () => controller.abort();
    }
}