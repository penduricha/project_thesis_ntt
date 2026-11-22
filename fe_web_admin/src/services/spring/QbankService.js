import SpringConfigHeader from '@/services/spring/SpringConfigHeader.js';
import { TokenLogin } from '@/model/TokenLogin.js';

/**
 * Đường dẫn API ngân hàng câu hỏi.
 *
 * `baseURL` đã gồm tiền tố `/api/web` (xem NetworkConfiguration + VITE_SPRING_API_PREFIX),
 * nên ở đây chỉ khai báo phần sau `/api/web`. Ví dụ QUESTIONS = '/admin/questions'
 * → http://localhost:8080/api/web/admin/questions
 */
const ENDPOINTS = Object.freeze({
    DASHBOARD_SUMMARY: '/admin/dashboard/summary',
    DASHBOARD_ACTIVITY: '/admin/dashboard/activity',

    QUESTIONS: '/admin/questions',
    QUESTION: (id) => `/admin/questions/${id}`,
    QUESTION_REVISIONS: (id) => `/admin/questions/${id}/revisions`,
    QUESTION_SUGGESTIONS: (id) => `/admin/questions/${id}/ai-suggestions`,
    QUESTION_AI_CHECK: (id) => `/admin/questions/${id}/ai-check`,
    QUESTION_RESTORE: (id) => `/admin/questions/${id}/restore`,
    QUESTIONS_BULK_ARCHIVE: '/admin/questions/bulk-archive',
    QUESTIONS_BULK_RESTORE: '/admin/questions/bulk-restore',
    QUESTION_DRAFT: '/admin/questions/draft',

    CATALOG_SUBJECTS: '/admin/catalog/subjects',
    CATALOG_TOPICS: (subjectId) => `/admin/catalog/subjects/${subjectId}/topics`,
    CATALOG_ISSUE_TYPES: '/admin/catalog/issue-types',

    AI_JOB: (taskId) => `/admin/ai/jobs/${encodeURIComponent(taskId)}`,
    AI_JOB_SUGGESTIONS: (taskId) => `/admin/ai/jobs/${encodeURIComponent(taskId)}/suggestions`,
    AI_SUGGESTION_DECISION: (suggestionId) => `/admin/ai/suggestions/${suggestionId}/decision`,
});

/**
 * Tầng HTTP thuần cho module ngân hàng câu hỏi: một hàm = một endpoint, không xử lý nghiệp vụ.
 * Không tạo axios instance riêng — dùng `getAuthorizedAPIClient` để chung cơ chế gắn
 * Bearer token + tự làm mới phiên (SilentTokenRefresher) với toàn bộ app.
 */
export class QbankService {
    constructor(
        springConfigHeader = new SpringConfigHeader(),
        tokenLogin = new TokenLogin(),
    ) {
        this.springConfigHeader = springConfigHeader;
        this.tokenLogin = tokenLogin;
    }

    #client() {
        return this.springConfigHeader.getAuthorizedAPIClient(this.tokenLogin);
    }

    async #get(path, params) {
        const response = await this.#client().get(path, { params });
        return response.data;
    }

    async #post(path, body) {
        const response = await this.#client().post(path, body);
        return response.data;
    }

    async #put(path, body) {
        const response = await this.#client().put(path, body);
        return response.data;
    }

    async #delete(path, params) {
        const response = await this.#client().delete(path, { params });
        return response.data;
    }

    // ------------------------------------------------------------------ Tổng quan
    getDashboardSummary(subjectId) {
        return this.#get(ENDPOINTS.DASHBOARD_SUMMARY, subjectId ? { subjectId } : undefined);
    }

    getDashboardActivity(limit = 10) {
        return this.#get(ENDPOINTS.DASHBOARD_ACTIVITY, { limit });
    }

    // ------------------------------------------------------------------ Câu hỏi
    listQuestions({ subjectId, topicId, status, validationStatus, q, page = 0, size = 20 } = {}) {
        return this.#get(ENDPOINTS.QUESTIONS, {
            subjectId: subjectId || undefined,
            topicId: topicId || undefined,
            status: status || undefined,
            validationStatus: validationStatus || undefined,
            q: q || undefined,
            page,
            size,
        });
    }

    getQuestion(questionId) {
        return this.#get(ENDPOINTS.QUESTION(questionId));
    }

    createQuestion(payload) {
        return this.#post(ENDPOINTS.QUESTIONS, payload);
    }

    /** `version` = version client đang giữ; backend nhận cả tên `expectedVersion` (đã có @JsonAlias). */
    updateQuestion(questionId, payload) {
        return this.#put(ENDPOINTS.QUESTION(questionId), payload);
    }

    /** Xoá mềm: backend chuyển status = archived, dữ liệu vẫn còn để thống kê. */
    archiveQuestion(questionId) {
        return this.#delete(ENDPOINTS.QUESTION(questionId));
    }

    restoreQuestion(questionId) {
        return this.#post(ENDPOINTS.QUESTION_RESTORE(questionId));
    }

    bulkArchive(ids) {
        return this.#post(ENDPOINTS.QUESTIONS_BULK_ARCHIVE, { ids });
    }

    bulkRestore(ids) {
        return this.#post(ENDPOINTS.QUESTIONS_BULK_RESTORE, { ids });
    }

    getRevisions(questionId) {
        return this.#get(ENDPOINTS.QUESTION_REVISIONS(questionId));
    }

    getQuestionSuggestions(questionId) {
        return this.#get(ENDPOINTS.QUESTION_SUGGESTIONS(questionId));
    }

    startAiCheck(questionId) {
        return this.#post(ENDPOINTS.QUESTION_AI_CHECK(questionId));
    }

    // ------------------------------------------------------------------ Nháp form (Redis, 24h)
    getDraft(questionId) {
        return this.#get(ENDPOINTS.QUESTION_DRAFT, questionId ? { questionId } : undefined);
    }

    saveDraft(draft, questionId) {
        return this.#put(
            `${ENDPOINTS.QUESTION_DRAFT}${questionId ? `?questionId=${questionId}` : ''}`,
            draft,
        );
    }

    deleteDraft(questionId) {
        return this.#delete(ENDPOINTS.QUESTION_DRAFT, questionId ? { questionId } : undefined);
    }

    // ------------------------------------------------------------------ Danh mục
    listSubjects(includeInactive = false) {
        return this.#get(ENDPOINTS.CATALOG_SUBJECTS, { includeInactive });
    }

    listTopics(subjectId) {
        return this.#get(ENDPOINTS.CATALOG_TOPICS(subjectId));
    }

    listIssueTypes() {
        return this.#get(ENDPOINTS.CATALOG_ISSUE_TYPES);
    }

    // ------------------------------------------------------------------ Tác vụ AI
    getJob(taskId) {
        return this.#get(ENDPOINTS.AI_JOB(taskId));
    }

    getJobSuggestions(taskId) {
        return this.#get(ENDPOINTS.AI_JOB_SUGGESTIONS(taskId));
    }

    decideSuggestion(suggestionId, { decision, finalText, rejectReason, expectedVersion }) {
        return this.#post(ENDPOINTS.AI_SUGGESTION_DECISION(suggestionId), {
            decision,
            finalText: finalText || undefined,
            rejectReason: rejectReason || undefined,
            expectedVersion,
        });
    }
}
