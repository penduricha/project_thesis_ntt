import { QbankService } from '@/services/spring/QbankService.js';

/**
 * Lỗi API đã được chuẩn hoá để giao diện phân nhánh theo `code` thay vì so chuỗi thông điệp.
 *
 * Backend trả 2 kiểu thân lỗi: `{success:false, code, message, at}` (bộ xử lý lỗi dùng chung)
 * và `{status, message, data}` (controller cũ). Repository gom cả hai về đây.
 */
export class QbankApiError extends Error {
    constructor(code, message, status) {
        super(message);
        this.name = 'QbankApiError';
        this.code = code || 'UNKNOWN';
        this.status = status || 0;
    }
}

/** Thông điệp thân thiện cho các mã lỗi giao diện cần xử lý riêng. */
const CODE_MESSAGES = Object.freeze({
    VERSION_CONFLICT: 'Câu hỏi vừa được người khác sửa. Tải lại rồi thử lại.',
    QUESTION_ARCHIVED: 'Câu hỏi đã bị lưu trữ nên không sửa được. Khôi phục trước nếu cần.',
    ALREADY_ARCHIVED: 'Câu hỏi đã ở trạng thái lưu trữ.',
    NOT_ARCHIVED: 'Câu hỏi không ở trạng thái lưu trữ nên không cần khôi phục.',
    QUESTION_NOT_FOUND: 'Không tìm thấy câu hỏi.',
    CHECK_IN_PROGRESS: 'Câu hỏi này đang được kiểm định, chờ kết quả lần trước.',
    AI_WORKER_NOT_CONFIGURED: 'Máy chủ AI chưa được cấu hình (AI_WORKER_BASE_URL).',
    AI_WORKER_UNAVAILABLE: 'Máy chủ AI không phản hồi, thử lại sau.',
    AI_WORKER_REJECTED: 'Máy chủ AI từ chối yêu cầu.',
    TASK_NOT_FOUND: 'Không tìm thấy tác vụ kiểm định.',
    SUGGESTION_ALREADY_DECIDED: 'Đề xuất này đã được quyết định trước đó.',
    SUGGESTION_STALE: 'Câu hỏi đã thay đổi sau khi AI đề xuất, đề xuất không còn áp dụng được.',
    TARGET_NOT_APPLICABLE: 'Đề xuất không áp dụng được cho trường dữ liệu này.',
    SUBJECT_NOT_FOUND: 'Không tìm thấy môn học.',
    SUBJECT_INACTIVE: 'Môn học đã ngừng sử dụng.',
    TOPIC_NOT_IN_SUBJECT: 'Chương không thuộc môn học đã chọn.',
    VALIDATION_FAILED: 'Dữ liệu gửi lên chưa hợp lệ.',
    DB_CONSTRAINT: 'Dữ liệu vi phạm ràng buộc trong CSDL.',
    UNAUTHORIZED: 'Phiên đăng nhập đã hết hạn.',
    FORBIDDEN: 'Tài khoản của bạn không có quyền cho thao tác này.',
});

function toQbankError(error) {
    if (error instanceof QbankApiError) {
        return error;
    }
    const status = error?.response?.status ?? 0;
    const body = error?.response?.data;
    const code = body?.code || body?.data?.code || (status === 401 ? 'UNAUTHORIZED' : 'UNKNOWN');
    const message =
        CODE_MESSAGES[code] ||
        body?.message ||
        (status ? `Máy chủ trả lỗi ${status}.` : 'Không kết nối được máy chủ.');
    return new QbankApiError(code, message, status);
}

async function call(action) {
    try {
        return await action();
    } catch (error) {
        throw toQbankError(error);
    }
}

/**
 * Nghiệp vụ + chuẩn hoá dữ liệu cho giao diện ngân hàng câu hỏi.
 *
 * Khác biệt quan trọng so với bản demo: backend trả trang `{content, page, size, totalElements,
 * totalPages}` chứ không phải mảng. Repository đổi thành `{items, ...}` để trang không phải biết
 * chi tiết định dạng của Spring Data.
 */
export class QbankRepository {
    constructor(qbankService = new QbankService()) {
        this.qbankService = qbankService;
    }

    // ------------------------------------------------------------------ Tổng quan
    getDashboard(subjectId) {
        return call(() => this.qbankService.getDashboardSummary(subjectId));
    }

    getActivity(limit = 10) {
        return call(() => this.qbankService.getDashboardActivity(limit));
    }

    // ------------------------------------------------------------------ Câu hỏi
    async listQuestions(params) {
        const page = await call(() => this.qbankService.listQuestions(params));
        return {
            items: page?.content ?? [],
            page: page?.page ?? 0,
            size: page?.size ?? (params?.size || 20),
            totalElements: page?.totalElements ?? 0,
            totalPages: page?.totalPages ?? 0,
        };
    }

    getQuestion(questionId) {
        return call(() => this.qbankService.getQuestion(questionId));
    }

    async createQuestion(payload) {
        const detail = await call(() => this.qbankService.createQuestion(payload));
        // Nháp của form "tạo mới" không còn giá trị sau khi lưu thành công.
        await this.discardDraft(null);
        return detail;
    }

    async updateQuestion(questionId, payload) {
        const detail = await call(() => this.qbankService.updateQuestion(questionId, payload));
        await this.discardDraft(questionId);
        return detail;
    }

    archiveQuestion(questionId) {
        return call(() => this.qbankService.archiveQuestion(questionId));
    }

    restoreQuestion(questionId) {
        return call(() => this.qbankService.restoreQuestion(questionId));
    }

    bulkArchive(ids) {
        return call(() => this.qbankService.bulkArchive(ids));
    }

    bulkRestore(ids) {
        return call(() => this.qbankService.bulkRestore(ids));
    }

    getRevisions(questionId) {
        return call(() => this.qbankService.getRevisions(questionId));
    }

    getQuestionSuggestions(questionId) {
        return call(() => this.qbankService.getQuestionSuggestions(questionId));
    }

    startAiCheck(questionId) {
        return call(() => this.qbankService.startAiCheck(questionId));
    }

    // ------------------------------------------------------------------ Nháp form
    /** Trả `null` khi chưa có nháp (backend trả 204). */
    async getDraft(questionId) {
        try {
            return await this.qbankService.getDraft(questionId);
        } catch (error) {
            const normalized = toQbankError(error);
            if (normalized.status === 204 || normalized.status === 404) {
                return null;
            }
            throw normalized;
        }
    }

    saveDraft(draft, questionId) {
        return call(() => this.qbankService.saveDraft(draft, questionId));
    }

    /** Bỏ nháp là thao tác phụ: lỗi ở đây không được làm hỏng luồng lưu chính. */
    async discardDraft(questionId) {
        try {
            await this.qbankService.deleteDraft(questionId);
        } catch {
            /* bỏ qua */
        }
    }

    // ------------------------------------------------------------------ Danh mục
    listSubjects(includeInactive = false) {
        return call(() => this.qbankService.listSubjects(includeInactive));
    }

    listTopics(subjectId) {
        return call(() => this.qbankService.listTopics(subjectId));
    }

    listIssueTypes() {
        return call(() => this.qbankService.listIssueTypes());
    }

    // ------------------------------------------------------------------ Tác vụ AI
    getJob(taskId) {
        return call(() => this.qbankService.getJob(taskId));
    }

    getJobSuggestions(taskId) {
        return call(() => this.qbankService.getJobSuggestions(taskId));
    }

    decideSuggestion(suggestionId, payload) {
        return call(() => this.qbankService.decideSuggestion(suggestionId, payload));
    }
}
