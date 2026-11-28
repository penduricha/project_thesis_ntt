// Lớp gọi API ngân hàng câu hỏi của Spring Boot (be-web-admin).
// API_BASE = {host}:{port}/api/web  (context-path /api + prefix /web), nên mọi đường dẫn ở đây
// phải đi qua ADMIN = '/admin' để khớp @RequestMapping("/web/admin/...") của backend.
//   vd: GET {API_BASE}/admin/questions -> http://localhost:8080/api/web/admin/questions
import SpringConfigHeader from '../auth/http.js'
import { TokenLogin } from '../auth/TokenLogin.js'
import { t } from '../i18n/index.js'

const header = new SpringConfigHeader()
const tokenLogin = new TokenLogin()
const authorizedClient = header.getAuthorizedAPIClient(tokenLogin)
const client = () => authorizedClient
// Tiền tố chung: /web/admin/questions, /web/admin/catalog, /web/admin/ai, /web/admin/dashboard.
const ADMIN = '/admin'
const url = (path) => `${ADMIN}${path}`

// QBankExceptionHandler của backend trả { success:false, code, message } kèm HTTP status.
// Helper này trích message/code để UI hiển thị, thay vì chỉ hiện "Request failed".
export function apiErrorMessage(error, fallback = t('apiError.network')) {
  const body = error?.response?.data
  if (body && typeof body === 'object') {
    if (body.message) return body.message
    if (body.code) return body.code
  }
  if (error?.response?.status === 401) return t('apiError.sessionExpired')
  if (error?.response?.status === 503) return t('apiError.aiNotConfigured')
  return fallback
}

export const qbankApi = {
  // ---------- Danh mục ----------
  async subjects(includeInactive = false, options = {}) {
    const { data } = await client().get(url('/catalog/subjects'), { ...options, params: { includeInactive } })
    return data
  },
  async getSubject(subjectId, options = {}) {
    const { data } = await client().get(url(`/catalog/subjects/${subjectId}`), options)
    return data
  },
  async createSubject(body) {
    const { data } = await client().post(url('/catalog/subjects'), body)
    return data
  },
  async updateSubject(subjectId, body) {
    const { data } = await client().put(url(`/catalog/subjects/${subjectId}`), body)
    return data
  },
  async archiveSubject(subjectId, expectedUpdatedAt) {
    const { data } = await client().delete(url(`/catalog/subjects/${subjectId}`), { data: { expectedUpdatedAt } })
    return data
  },
  async topics(subjectId, options = {}) {
    const { data } = await client().get(url(`/catalog/subjects/${subjectId}/topics`), options)
    return data
  },
  async issueTypes() {
    const { data } = await client().get(url('/catalog/issue-types'))
    return data
  },

  // ---------- Câu hỏi ----------
  // GET /questions?subjectId&topicId&status&validationStatus&q&page&size -> PageResponse
  async listQuestions(params = {}, options = {}) {
    const { data } = await client().get(url('/questions'), { ...options, params })
    return data
  },
  async getQuestion(questionId, options = {}) {
    const { data } = await client().get(url(`/questions/${questionId}`), options)
    return data
  },
  async createQuestion(body) {
    const { data } = await client().post(url('/questions'), body)
    return data
  },
  // Server validates the complete batch, then commits each question separately.
  async importQuestions(body) {
    const { data } = await client().post(url('/questions/import'), body, { timeout: 180000 })
    return data
  },
  async updateQuestion(questionId, body) {
    const { data } = await client().put(url(`/questions/${questionId}`), body)
    return data
  },
  // DELETE = lưu trữ mềm (archive), không xóa cứng (FK RESTRICT với task/eval).
  async archiveQuestion(questionId) {
    const { data } = await client().delete(url(`/questions/${questionId}`))
    return data
  },
  /**
   * Bỏ lưu trữ (khôi phục) câu hỏi. Backend: POST /questions/{id}/restore.
   *
   * Vì sao là POST riêng chứ không dùng lại updateQuestion(): update() cố ý chặn câu đã lưu trữ
   * (QUESTION_ARCHIVED) và đòi expectedVersion + toàn bộ nội dung, nên không thể dùng để bỏ cờ.
   */
  async restoreQuestion(questionId) {
    const { data } = await client().post(url(`/questions/${questionId}/restore`))
    return data
  },
  async revisions(questionId) {
    const { data } = await client().get(url(`/questions/${questionId}/revisions`))
    return data
  },
  async latestSuggestions(questionId, options = {}) {
    const { data } = await client().get(url(`/questions/${questionId}/ai-suggestions`), options)
    return data
  },

  // ---------- Kiểm định AI ----------
  // POST -> 202 AiJobView { taskId, questionId, status, pollAfterMs, ... }
  async aiCheck(questionId) {
    const { data } = await client().post(url(`/questions/${questionId}/ai-check`))
    return data
  },
  async job(taskId, options = {}) {
    const { data } = await client().get(url(`/ai/jobs/${taskId}`), options)
    return data
  },
  async jobSuggestions(taskId, options = {}) {
    const { data } = await client().get(url(`/ai/jobs/${taskId}/suggestions`), options)
    return data
  },
  async decideSuggestion(suggestionId, body) {
    const { data } = await client().post(url(`/ai/suggestions/${suggestionId}/decision`), body)
    return data
  },

  // ---------- Dashboard ----------
  async dashboardSummary(subjectId, options = {}) {
    const { data } = await client().get(url('/dashboard/summary'), { ...options, params: subjectId ? { subjectId } : {} })
    return data
  },
  async dashboardActivity(limit = 10, options = {}) {
    const { data } = await client().get(url('/dashboard/activity'), { ...options, params: { limit } })
    return data
  },
}
