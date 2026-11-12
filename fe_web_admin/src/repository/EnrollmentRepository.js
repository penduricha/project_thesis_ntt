import {EnrollmentService} from "@/services/spring/EnrollmentService.js";

const HTTP_OK = 200;
const HTTP_CREATED = 201;
const HTTP_UNAUTHORIZED = 401;

const MESSAGES = Object.freeze({
    DEFAULT_ERROR: 'Không thể tạo khóa bí mật. Vui lòng thử lại.',
    SESSION_EXPIRED: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
    NETWORK_ERROR: 'Không thể kết nối tới máy chủ. Vui lòng kiểm tra mạng.',
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
    constructor(enrollSessionService = new EnrollmentService()) {
        this.enrollSessionService = enrollSessionService;
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
        const response = await this.enrollSessionService
            .postDataToStartEnrollmentRequest(tokenLogin, periodSeconds);

        if (response === null) {
            return buildFailure(null, MESSAGES.NETWORK_ERROR);
        }

        const body = response.data;
        const data = body?.data;

        if (response.status === HTTP_CREATED
            && body?.success === true
            && typeof data?.secretKey === 'string') {
            return {
                success: true,
                status: response.status,
                message: body.message,
                data: {
                    secretKey: data.secretKey,
                    periodSeconds: data.periodSeconds,
                    expiresInSeconds: data.expiresInSeconds,
                },
            };
        }

        if (response.status === HTTP_UNAUTHORIZED) {
            return buildFailure(response.status, MESSAGES.SESSION_EXPIRED);
        }
        return buildFailure(response.status, body?.message ?? MESSAGES.DEFAULT_ERROR);
    }

    /**
     * Đóng modal / chuyển trang trong app: xóa khóa đang chờ.
     * @returns {Promise<boolean>} true nếu server xác nhận (kể cả khi khóa đã hết hạn từ trước)
     */
    async cancelEnrollment(tokenLogin) {
        const response = await this.enrollSessionService
            .deleteDataToCancelEnrollmentRequest(tokenLogin);

        return response?.status === HTTP_OK;
    }

    /**
     * Tắt tab / đóng trình duyệt: gửi yêu cầu xóa theo kiểu "bắn rồi bỏ" (không chờ kết quả).
     * Gọi trong sự kiện pagehide.
     */
    cancelEnrollmentOnPageHide(tokenLogin) {
        this.enrollSessionService.deleteDataToCancelEnrollmentOnPageHide(tokenLogin);
    }
}