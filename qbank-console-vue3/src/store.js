import { reactive } from 'vue'
import { COURSES as RAW_COURSES, USERS, MY_ADMIN_ID } from './data/mock.js'
import { nextTick } from 'vue'
import { llm } from './services/llmSimulator.js'
import { questionRepository as repo } from './repositories/questionRepository.js'
import { auth } from './auth/authStore.js'
import { qbankApi, apiErrorMessage } from './api/qbankApi.js'
import { subjectToCourse, detailToQuestion, questionToCreate, questionToUpdate, suggestionsToUi, suggestionReplacement } from './api/mappers.js'
import { TokenLogin } from './auth/TokenLogin.js'
import { EnrollmentRepository } from './auth/EnrollmentRepository.js'
import { t as tr } from './i18n/index.js'

/* Danh sách môn học dạng reactive để thêm/sửa/xóa là giao diện tự cập nhật */
const DEMO_COURSES = RAW_COURSES.map(c => ({ ...c, topics: [...c.topics], topicIds: [...c.topicIds] }))
export const COURSES = reactive(RAW_COURSES)

const LM0 = [['https://calm-otter-star.trycloudflare.com', 'k3F9xQ7mZp2LwR8tVb5NcY1dHs6JgA4e', 18.4], ['https://quiet-river-1042.trycloudflare.com', 'Wm8Zt2QxL5vN9cRb4YpD7sHk1JfA3gTe', 17.1], ['https://amber-fox-run.trycloudflare.com', 'Pq6Ls9XaV3nB8cTr2MdY5wHj7KfG4zEu', 16.8]]
function lmInit() {
  return Array.from({ length: 5 }, (_, i) => {
    const d = LM0[i], c = { url: d?.[0] || '', tok: d?.[1] || '', tps: d?.[2] || 0, en: !!d, ok: null, busy: false, note: '', nk: '' }
    c.sv = { url: c.url, tok: c.tok, en: c.en }; return c
  })
}
export const blankF = (hideAns = false) => ({ q: '', c: '', tp: '', l: '', s: '', sort: 'id', page: 1, hideAns })
export const S = reactive({
  ci: 0, n: 12, cq: '', rf: 'all', tf: 'all', rsel: new Set(),
  notifyTasks: true, confirmDelete: true, nl: '', nlKw: '', imp: { c: 0, tp: 0, l: 1 }, xmlName: 'qbank-export-2026-11-18.xml', xml: '',
  asId: null, userQuery: '', userStatus: '', meReady: false, idShow: false,
  tfa: { st: 'NOT_ENABLED', until: 0 }, tfaSec: '', tfaPeriod: 30, tfaKeyExpiresAt: 0, tfaBusy: false, now: Date.now(),
  users: USERS.map((u, i) => ({ ...u, idCard: u.adminId === MY_ADMIN_ID ? '060203000027' : '0792010' + String(10000 + i),
    subjects: ({ 'Giảng viên A': ['INT3306', 'INT3202'], 'Giảng viên B': ['INT3304', 'INT3401'], 'Giảng viên C': ['INT3306'] })[u.name] || [] })),
  api: { base: 'http://localhost:8082/api', jwt: 'demo-internal-token', on: true, busy: false },
  lm: lmInit(), dlg: { kind: null, props: {}, n: 0 }, role: 'admin', name: 'Từ Quang Nhật', colab: true, tn: 1, status: 'idle',
  q: [], sug: [], tasks: [], sel: new Set(), f: blankF(),
  // Chế độ dữ liệu: 'real' = gọi Spring Boot, 'mock' = dữ liệu mẫu (demo hoặc backend chưa sẵn sàng).
  dataMode: 'unavailable', dashboard: null, activity: [], suggestionsStatus: 'idle',
  log: [['09:45:12', { key: 'activity.initial', params: {} }, 'ok']],
  toast: { on: false, msg: '', tone: '', label: '', cb: null },
})

export const code = q => 'CH' + String(q.id).padStart(3, '0')
export const topicName = (ci, tpi) => COURSES[ci]?.topics[tpi] || 'Chương mặc định'
const live = /pending|running/
export const act = () => S.tasks.filter(t => live.test(t.st)).length
export const busy = q => S.tasks.some(t => t.qid === q.id && live.test(t.st))
export const resetFilters = () => { S.f = blankF(S.f.hideAns) }
export const clearFilters = () => Object.assign(S.f, { q: '', c: '', tp: '', l: '', s: '', page: 1 })
export function log(m, tone = 'info') { S.log.unshift([new Date().toLocaleTimeString('vi-VN'), m, tone]); if (S.log.length > 50) S.log.length = 50 }
// Store keys and parameters for local activity messages so they react to locale changes.
// Existing plain strings (including any server-provided text) remain untouched.
export function logKey(key, params = {}, tone = 'info') { log({ key, params }, tone) }
export const logText = value => value && typeof value === 'object' && typeof value.key === 'string' ? tr(value.key, value.params) : value

let th
const arm = ms => { clearTimeout(th); th = setTimeout(() => { S.toast.on = false }, ms) }
export const toastHold = () => clearTimeout(th)      /* tạm dừng khi rê chuột / có focus (v10) */
export const toastRelease = () => arm(2500)
export function toast(msg, tone = '', label = '', cb = null) {
  Object.assign(S.toast, { on: true, msg, tone, label, cb })
  arm(cb ? 8000 : 4000)
}
export const notYet = what => toast(`${what} chưa được chuyển sang Vue trong bản demo này`, 'wa')

const tsk = s => { const m = (s || '').match(/(\d+)\/(\d+) (\d+):(\d+)/); return m ? +m[2] * 1e6 + +m[1] * 1e4 + +m[3] * 100 + +m[4] : 0 }
export function list(isCourse) {
  const f = S.f, kw = f.q.toLowerCase()
  let arr = S.q.filter(q =>
    (!kw || (q.text + q.o.join(' ') + code(q) + (COURSES[q.c]?.id || '') + (COURSES[q.c]?.name || '')).toLowerCase().includes(kw)) &&
    subjOk(q.c) &&
    // q.c/q.tp/q.l là SỐ (từ mapper), còn bộ lọc f.* là CHUỖI (blankF trả '', chip gán String(idx)).
    // Vì vậy phải +f.* để so SỐ với SỐ — dùng === trên hai kiểu khác nhau sẽ LUÔN false
    // và bộ lọc môn/chương/mức độ/trạng thái sẽ lọc ra rỗng.
    (isCourse ? q.c === S.ci : (f.c === '' || q.c === +f.c)) &&
    (f.tp === '' || q.tp === +f.tp) && (f.l === '' || q.l === +f.l) && (f.s === '' || q.st === f.s))
  if (f.sort === 'level_asc') arr = [...arr].sort((a, b) => a.l - b.l)
  else if (f.sort === 'level') arr = [...arr].sort((a, b) => b.l - a.l)
  else if (f.sort === 'recent') arr = [...arr].sort((a, b) => tsk(b.updatedAt) - tsk(a.updatedAt))
  else arr = [...arr].sort((a, b) => a.id - b.id)
  return arr
}

/* Reads belong to the authenticated app lifetime. Never abort a write: cancellation does not
   prove that the server rolled it back. Sequence checks prevent older GETs overwriting writes. */
export const useRealApi = () => auth.isAuthenticated && !auth.isDemo
let sessionGeneration = 0, questionGeneration = 0, dashboardGeneration = 0, suggestionGeneration = 0
let questionController = null
let sessionController = new AbortController()
const activeWrites = new Set()
const uncertainSubjects = new Set()
const sessionStamp = () => sessionGeneration
const sessionCurrent = stamp => stamp === sessionGeneration && auth.isAuthenticated
const hasCurrentWrites = () => [...activeWrites].some(key => key.startsWith(`${sessionGeneration}:`))
function invalidateQuestionReads() {
  questionGeneration++; suggestionGeneration++; dashboardGeneration++
  if (S.suggestionsStatus === 'loading') S.suggestionsStatus = 'idle'
  questionController?.abort()
  questionController = null
  if (S.status === 'loading') S.status = S.dataMode === 'real' ? 'ready' : 'idle'
}
export function resetQuestionSession() {
  sessionController.abort(); sessionController = new AbortController()
  sessionGeneration++
  uncertainSubjects.clear()
  invalidateQuestionReads()
  dashboardGeneration++; suggestionGeneration++
  S.q = []; S.sug = []; S.tasks = []; S.dashboard = null; S.activity = []
  S.sel.clear(); S.rsel.clear(); S.status = 'idle'; S.suggestionsStatus = 'idle'
  S.dataMode = 'unavailable'; S.ci = 0; S.f = blankF(S.f.hideAns); S.imp.c = 0; S.imp.tp = 0
  S.dlg.kind = null; S.dlg.props = {}
  S.toast.on = false; S.toast.cb = null
  COURSES.splice(0, COURSES.length)
}

export function beginQuestionMutation() {
  const stamp = sessionStamp(), key = `${stamp}:import`
  activeWrites.add(key)
  invalidateQuestionReads()
  return {
    finish: () => { activeWrites.delete(key); if (sessionCurrent(stamp)) invalidateQuestionReads() },
    isCurrent: () => sessionCurrent(stamp),
    invalidateReads: () => { if (sessionCurrent(stamp)) invalidateQuestionReads() },
  }
}

async function fetchCatalog(signal) {
  const subjects = await qbankApi.subjects(true, { signal })
  const courses = subjects.map(subjectToCourse)
  await Promise.all(courses.map(async c => {
    const topics = await qbankApi.topics(c.subjectId, { signal })
    c.topics = topics.map(t => t.topicName)
    c.topicIds = topics.map(t => t.topicId)
  }))
  return courses
}

export async function loadDashboard(options = {}) {
  if (!useRealApi()) return
  options = { signal: sessionController.signal, ...options }
  const stamp = sessionStamp(), generation = ++dashboardGeneration
  try {
    const [summary, activity] = await Promise.all([
      qbankApi.dashboardSummary(undefined, options), qbankApi.dashboardActivity(10, options),
    ])
    if (!sessionCurrent(stamp) || generation !== dashboardGeneration || options.signal?.aborted) return
    S.dashboard = summary
    S.activity = Array.isArray(activity) ? activity : []
  } catch (e) {
    if (!options.signal?.aborted) console.warn('[qbank] loadDashboard failed:', e?.message)
  }
}

export async function loadSuggestions(options = {}) {
  if (!useRealApi()) return
  options = { signal: sessionController.signal, ...options }
  const stamp = sessionStamp(), generation = ++suggestionGeneration
  S.suggestionsStatus = 'loading'
  try {
    const flagged = S.q.filter(q => q.st === 'flag')
    const loaded = await Promise.all(flagged.map(async q => {
      let list
      try {
        list = await qbankApi.latestSuggestions(q.id, options)
      } catch (error) {
        // The server hides another lecturer's task as 404. Keep loading this
        // user's accessible tasks, but do not hide network or other errors.
        if (error?.response?.status === 404 && error?.response?.data?.code === 'TASK_NOT_FOUND') return null
        throw error
      }
      const pending = (list || []).filter(s => !s.decision || s.decision === 'pending')
      return pending.length ? suggestionsToUi(pending, q, pending[0]?.taskId) : null
    }))
    if (!sessionCurrent(stamp) || generation !== suggestionGeneration || options.signal?.aborted) return
    S.sug = loaded.filter(Boolean)
    S.suggestionsStatus = 'ready'
  } catch (e) {
    if (!sessionCurrent(stamp) || generation !== suggestionGeneration || options.signal?.aborted) return
    S.suggestionsStatus = 'error'
    console.warn('[qbank] loadSuggestions failed:', e?.message)
  }
}

export async function loadQuestions(options = {}) {
  if (!auth.isAuthenticated || hasCurrentWrites()) return false
  invalidateQuestionReads()
  const generation = questionGeneration, stamp = sessionStamp()
  const controller = new AbortController()
  questionController = controller
  const cancel = () => controller.abort()
  if (options.signal?.aborted) controller.abort()
  else options.signal?.addEventListener('abort', cancel, { once: true })
  const current = () => sessionCurrent(stamp) && generation === questionGeneration && !controller.signal.aborted
  S.status = 'loading'
  try {
    if (auth.isDemo) {
      const items = await repo.findAll()
      if (!current()) return false
      COURSES.splice(0, COURSES.length, ...DEMO_COURSES.map(c => ({ ...c, topics: [...c.topics], topicIds: [...c.topicIds] })))
      S.q = items; S.dataMode = 'mock'; S.status = 'ready'; return true
    }
    const courses = await fetchCatalog(controller.signal)
    // UI question/course routes use array indexes; preserve known subject positions on refresh.
    const previousOrder = new Map(COURSES.map((course, index) => [course.subjectId, index]))
    courses.sort((a, b) => (previousOrder.get(a.subjectId) ?? Infinity) - (previousOrder.get(b.subjectId) ?? Infinity))
    const items = []
    let page = 0
    for (;;) {
      const res = await qbankApi.listQuestions({ status: 'active', page, size: 100, includeOptions: true }, { signal: controller.signal })
      if (!current()) return false
      items.push(...(res.content || []))
      if (page + 1 >= (res.totalPages || 1)) break
      page++
    }
    const mapped = items.map(it => {
      const detail = {
        questionId: it.questionId, subjectId: it.subjectId, topicId: it.topicId,
        title: it.title, stem: it.stem ?? it.stemPreview ?? '',
        optionA: it.options?.[0] ?? '', optionB: it.options?.[1] ?? '',
        optionC: it.options?.[2] ?? '', optionD: it.options?.[3] ?? '',
        correctLabel: it.correctLabel ?? 'A', contentFormat: it.contentFormat ?? 'plain',
        generalFeedback: it.generalFeedback ?? '', difficulty: it.difficulty, status: it.status,
        validationStatus: it.validationStatus, source: it.source, updatedAt: it.updatedAt,
        updatedBy: null, version: it.version, revisionNo: null,
      }
      const ci = courses.findIndex(c => c.subjectId === detail.subjectId)
      if (ci < 0) throw new Error('Question references an unknown subject')
      const tp = Math.max(0, courses[ci].topicIds.indexOf(detail.topicId))
      return detailToQuestion(detail, ci, tp)
    })
    if (!current()) return false
    const catalogChanged = JSON.stringify(COURSES.map(c => [c.subjectId, c.topicIds])) !== JSON.stringify(courses.map(c => [c.subjectId, c.topicIds]))
    if (catalogChanged) S.f = blankF(S.f.hideAns)
    COURSES.splice(0, COURSES.length, ...courses)
    S.ci = Math.min(S.ci, Math.max(0, courses.length - 1))
    S.q = mapped; S.dataMode = 'real'; S.status = 'ready'; uncertainSubjects.clear()
    await Promise.all([loadDashboard({ signal: controller.signal }), loadSuggestions({ signal: controller.signal })])
    return current()
  } catch (e) {
    if (!current()) return false
    console.warn('[qbank] loadQuestions failed:', e?.message)
    // Never turn a real session into a writable mock session on failure.
    S.status = 'error'
    toast(apiErrorMessage(e, tr('operation.loadFailed')), 'er')
    return false
  } finally {
    options.signal?.removeEventListener('abort', cancel)
    if (questionController === controller) questionController = null
  }
}

// Locally generated task messages are stored as translation keys so they update
// when the user changes language. Raw server messages stay in `msg` unchanged.
function setTaskMessage(task, key, params = {}) { task.msg = ''; task.msgKey = key; task.msgParams = params }

export function runAI(ids) {
  if (useRealApi() && S.dataMode === 'real') return runAiReal(ids)
  if (auth.isAuthenticated && !auth.isDemo) return toast(tr('operation.loadFailed'), 'er')
  let n = 0
  ids.forEach(id => {
    const q = S.q.find(x => x.id === id)
    if (!q || S.tasks.some(t => t.qid === id && live.test(t.st))) return
    n++
    const t = reactive({ id: 'T-' + String(S.tn++).padStart(4, '0'), qid: id, st: 'pending', ms: 0, t0: Date.now(), started: new Date().toLocaleTimeString('vi-VN'), msg: '' })
    S.tasks.unshift(t); if (S.tasks.length > 50) S.tasks.length = 50
    setTimeout(() => { t.st = 'running' }, 600)
    setTimeout(() => {
      t.ms = Date.now() - t.t0
      if (!S.colab) {
        t.st = 'failed'; setTaskMessage(t, 'job.llmTimeout')
        logKey('job.logLlmFailed', { id: t.id, code: code(q) }, 'er')
        toast(tr('job.failedToast', { id: t.id }), 'er')
        return
      }
      const r = llm(q); t.st = 'succeeded'
      r.is.length ? setTaskMessage(t, 'job.foundDifficulty', { n: r.is.length, d: r.diff }) : setTaskMessage(t, 'job.passed')
      if (r.is.length) {
        S.sug = S.sug.filter(s => s.qid !== id)
        S.sug.push({ qid: id, tx: r.tx, is: r.is, diff: r.diff, sev: r.sev, tid: t.id })
        q.st = 'flag'; logKey('job.logSuggestions', { id: t.id, code: code(q), n: r.is.length }, 'wa')
        if (S.notifyTasks) toast(tr('job.toastSuggestions', { id: t.id, code: code(q), n: r.is.length }), 'wa')
      } else { q.st = 'ok'; logKey('job.logPassed', { id: t.id, code: code(q) }, 'ok'); if (S.notifyTasks) toast(tr('job.toastPassed', { id: t.id, code: code(q) }), 'ok') }
    }, 2200 + Math.random() * 1000)
  })
  if (!n) return toast(tr('job.alreadyRunning'), 'wa')
  logKey('job.logAccepted', { n }, 'info')
  toast(tr('job.accepted', { n }), 'ok')
}

/* Kiểm định AI thật: POST /questions/{id}/ai-check (202) rồi poll GET /ai/jobs/{taskId}
   theo pollAfterMs của backend cho tới khi succeeded/failed/cancelled. */
async function runAiReal(ids) {
  const stamp = sessionStamp(), signal = sessionController.signal
  let n = 0
  for (const id of ids) {
    if (!sessionCurrent(stamp) || signal.aborted) return
    const q = S.q.find((x) => x.id === id)
    if (!q || S.tasks.some((t) => t.qid === id && live.test(t.st))) continue
    n++
    const t = reactive({ id: '…', qid: id, st: 'pending', ms: 0, t0: Date.now(), started: new Date().toLocaleTimeString('vi-VN'), msg: '', msgKey: 'job.sending', msgParams: {} })
    S.tasks.unshift(t); if (S.tasks.length > 50) S.tasks.length = 50
    try {
      const job = await qbankApi.aiCheck(id)
      if (!sessionCurrent(stamp) || signal.aborted) return
      t.id = job.taskId
      t.st = String(job.status || 'pending').toLowerCase()
      if (!live.test(t.st)) t.msgKey = ''
      // Poll tới khi kết thúc (giới hạn 2 phút để không treo tab).
      const deadline = Date.now() + 120000
      while (live.test(t.st) && Date.now() < deadline) {
        await new Promise(resolve => {
          const timer = setTimeout(done, job.pollAfterMs || 1500)
          function done() { clearTimeout(timer); signal.removeEventListener('abort', done); resolve() }
          signal.addEventListener('abort', done, { once: true })
          if (signal.aborted) done()
        })
        if (!sessionCurrent(stamp) || signal.aborted) return
        const cur = await qbankApi.job(job.taskId, { signal })
        if (!sessionCurrent(stamp) || signal.aborted) return
        t.st = String(cur.status || t.st).toLowerCase()
        if (cur.errorCode) setTaskMessage(t, 'job.errorCode', { code: cur.errorCode })
        else { t.msg = ''; t.msgKey = '' }
      }
      t.ms = Date.now() - t.t0
      if (live.test(t.st)) {
        t.st = 'unknown'; t.msgKey = ''; t.msg = tr('crud.taskUnknown')
        toast(t.msg, 'wa'); continue
      }
      if (t.st === 'succeeded') {
        let list
        try { list = await qbankApi.jobSuggestions(job.taskId, { signal }); if (!sessionCurrent(stamp) || signal.aborted) return }
        catch (e) {
          if (!sessionCurrent(stamp) || signal.aborted) return
          S.suggestionsStatus = 'error'
          t.msgKey = ''; t.msg = tr('crud.suggestionsUnknown')
          toast(t.msg, 'wa')
          continue
        }
        const pending = (list || []).filter((s) => !s.decision || s.decision === 'pending')
        if (pending.length) {
          S.sug = S.sug.filter((s) => s.qid !== id)
          S.sug.push(suggestionsToUi(pending, q, job.taskId))
          q.st = 'flag'
          setTaskMessage(t, 'job.found', { n: pending.length })
          logKey('job.logSuggestions', { id: job.taskId, code: code(q), n: pending.length }, 'wa')
          if (S.notifyTasks) toast(tr('job.toastSuggestions', { id: job.taskId, code: code(q), n: pending.length }), 'wa')
        } else {
          setTaskMessage(t, 'job.passed')
          logKey('job.logPassed', { id: job.taskId, code: code(q) }, 'ok')
          if (S.notifyTasks) toast(tr('job.toastPassed', { id: job.taskId, code: code(q) }), 'ok')
        }
      } else if (t.st === 'failed' || t.st === 'cancelled') {
        if (!t.msg && !t.msgKey) setTaskMessage(t, 'job.failed')
        logKey('job.logFailed', { id: job.taskId, code: code(q) }, 'er')
        toast(tr('job.failedToast', { id: job.taskId }), 'er')
      }
      await loadDashboard({ signal })
      if (!sessionCurrent(stamp) || signal.aborted) return
    } catch (e) {
      if (!sessionCurrent(stamp) || signal.aborted) return
      t.st = 'failed'; t.ms = Date.now() - t.t0
      t.msgKey = ''; t.msg = apiErrorMessage(e, tr('job.sendFailed'))
      logKey('job.logSendFailed', { code: code(q), message: t.msg }, 'er')
      toast(t.msg, 'er')
    }
  }
  if (!n) return toast(tr('job.alreadyRunning'), 'wa')
  logKey('job.logAccepted', { n }, 'info')
  toast(tr('job.accepted', { n }), 'ok')
}

// Limit parallel writes and never start additional requests after the session changes.
async function settleWrites(ids, operation, stamp, concurrency = 4) {
  const results = new Array(ids.length)
  let next = 0
  async function worker() {
    for (;;) {
      const index = next++
      if (index >= ids.length) return
      if (!sessionCurrent(stamp)) { results[index] = { status: 'rejected', reason: new Error('session ended') }; continue }
      try { results[index] = { status: 'fulfilled', value: await operation(ids[index]) } }
      catch (reason) { results[index] = { status: 'rejected', reason } }
    }
  }
  await Promise.all(Array.from({ length: Math.min(concurrency, ids.length) }, worker))
  return results
}

export async function removeQuestions(ids) {
  const uniqueIds = [...new Set(ids)].filter(id => S.q.some(q => q.id === id))
  if (!uniqueIds.length) return
  if (!auth.isDemo && (!useRealApi() || S.dataMode !== 'real')) {
    toast(tr('operation.loadFailed'), 'er'); return
  }
  const stamp = sessionStamp(), keys = uniqueIds.map(id => `${stamp}:question:${id}`)
  if (keys.some(key => activeWrites.has(key))) return
  keys.forEach(key => activeWrites.add(key))
  invalidateQuestionReads()
  try {
    if (useRealApi()) {
      // Do not optimistically restore all rows on partial failure: each endpoint commits independently.
      const results = await settleWrites(uniqueIds, id => qbankApi.archiveQuestion(id), stamp)
      if (!sessionCurrent(stamp)) return
      invalidateQuestionReads()
      const done = uniqueIds.filter((_, i) => results[i].status === 'fulfilled')
      const failed = results.length - done.length
      const uncertain = results.some(r => r.status === 'rejected' && (!r.reason?.response || r.reason.response.status >= 500))
      S.q = S.q.filter(q => !done.includes(q.id)); S.sug = S.sug.filter(s => !done.includes(s.qid))
      done.forEach(id => S.sel.delete(id))
      if (failed) {
        // Reconcile after all operations settle, never automatically retry a write.
        keys.forEach(key => activeWrites.delete(key))
        await loadQuestions()
        if (sessionCurrent(stamp)) toast(tr(uncertain ? 'crud.archiveUncertain' : 'crud.archivePartial', { done: done.length, total: uniqueIds.length }), 'wa')
      } else {
        logKey('activity.bulkDelete', { n: done.length }, 'wa')
        toast(tr('operation.archivedMany', { n: done.length }), 'wa', tr('bank.undoAction'), async () => {
          const restoreStamp = sessionStamp()
          if (!sessionCurrent(restoreStamp)) return
          const restoreKeys = done.map(id => `${restoreStamp}:question:${id}`)
          if (restoreKeys.some(key => activeWrites.has(key))) return
          restoreKeys.forEach(key => activeWrites.add(key))
          invalidateQuestionReads()
          try {
            const restored = await settleWrites(done, id => qbankApi.restoreQuestion(id), restoreStamp)
            if (!sessionCurrent(restoreStamp)) return
            invalidateQuestionReads()
            if (restored.every(r => r.status === 'fulfilled')) toast(tr('operation.restoredMany', { n: done.length }), 'ok')
            else toast(tr('crud.restoreUncertain'), 'wa')
            restoreKeys.forEach(key => activeWrites.delete(key))
            await loadQuestions()
          } finally { restoreKeys.forEach(key => activeWrites.delete(key)) }
        })
        await loadDashboard()
      }
      return
    }
    const bq = S.q.filter(q => uniqueIds.includes(q.id)), bs = S.sug.filter(s => uniqueIds.includes(s.qid))
    await repo.removeMany(uniqueIds)
    if (!sessionCurrent(stamp)) return
    invalidateQuestionReads()
    S.q = S.q.filter(q => !uniqueIds.includes(q.id)); S.sug = S.sug.filter(s => !uniqueIds.includes(s.qid)); uniqueIds.forEach(id => S.sel.delete(id))
    toast(tr('operation.deletedMany', { n: uniqueIds.length }), 'wa', tr('bank.undoAction'), async () => {
      await repo.restoreMany(bq)
      if (!sessionCurrent(stamp)) return
      invalidateQuestionReads(); S.q.push(...bq); S.sug.push(...bs); toast(tr('operation.restoredDeleted'), 'ok')
    })
  } catch (e) {
    if (sessionCurrent(stamp)) toast(apiErrorMessage(e, tr('operation.deleteFailedRestored')), 'er')
  } finally {
    keys.forEach(key => activeWrites.delete(key))
  }
}

/* ---------- Hộp thoại ---------- */
export const nowStr = () => { const d = new Date(); return String(d.getDate()).padStart(2, '0') + '/' + String(d.getMonth() + 1).padStart(2, '0') + ' ' + d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }) }
export const openDialog = (kind, props = {}) => {
  if (auth.isAuthenticated && !auth.isDemo && ['topics', 'import'].includes(kind)) return toast(tr('crud.catalogUnsupported'), 'wa')
  S.dlg.n++; S.dlg.kind = kind; S.dlg.props = props
}
export const closeDialog = () => { S.dlg.kind = null; S.dlg.props = {} }

/* ---------- Câu hỏi ---------- */
export function editQuestion(id, ctx = {}) {
  if (!COURSES.length) return toast(tr('operation.createCourseFirst'), 'wa')
  if (id && busy({ id })) return toast(tr('operation.questionBusy'), 'wa')
  openDialog('question', { id, ctx })
}
/** Đọc lại 1 câu hỏi từ backend rồi cập nhật tại chỗ (giữ nguyên object để Vue không mất tham chiếu). */
async function refreshFromServer(q) {
  const stamp = sessionStamp(), signal = sessionController.signal
  try {
    const d = await qbankApi.getQuestion(q.id, { signal })
    if (!sessionCurrent(stamp) || signal.aborted) return null
    const ci = COURSES.findIndex((c) => c.subjectId === d.subjectId)
    const course = COURSES[ci]
    const tp = course ? Math.max(0, course.topicIds.indexOf(d.topicId)) : q.tp
    Object.assign(q, detailToQuestion(d, ci >= 0 ? ci : q.c, tp))
    return d
  } catch (e) {
    console.warn('[qbank] refreshFromServer failed:', e?.message)
    return null
  }
}

export async function saveQuestion(d, eid, expectedVersion) {
  if (!auth.isAuthenticated || (!auth.isDemo && S.dataMode !== 'real')) {
    return { ok: false, message: tr('operation.loadFailed') }
  }
  const stamp = sessionStamp(), key = `${stamp}:${eid ? `question:${eid}` : 'question:create'}`
  if (activeWrites.has(key)) return { ok: false, message: tr('crud.inProgress') }
  activeWrites.add(key)
  invalidateQuestionReads()
  try {
    if (useRealApi()) {
      const course = COURSES[d.c]
      if (!course || course.subjectId == null) return { ok: false, message: tr('operation.courseNotLinked') }
      let res
      if (eid) {
        const cur = S.q.find(x => x.id === eid)
        if (!cur) return { ok: false, message: tr('operation.loadFailed') }
        if (cur.subjectId != null && course.subjectId !== cur.subjectId) return { ok: false, message: tr('crud.subjectImmutable') }
        const body = questionToUpdate({ ...cur, ...d, ver: expectedVersion ?? cur.ver }, course)
        res = await qbankApi.updateQuestion(eid, body)
      } else {
        res = await qbankApi.createQuestion(questionToCreate(d, course))
      }
      if (!sessionCurrent(stamp)) return { ok: false, abandoned: true }
      if (!res || res.questionId == null) throw new Error('Invalid save response')
      invalidateQuestionReads()
      const mapped = detailToQuestion(res, d.c, d.tp)
      const current = S.q.find(x => x.id === mapped.id)
      if (current) Object.assign(current, mapped); else S.q.push(mapped)
      if (eid) S.sug = S.sug.filter(s => s.qid !== eid)
      logKey(eid ? 'activity.questionUpdated' : 'activity.questionAdded', { code: code(mapped) }, eid ? 'info' : 'ok')
      toast(tr(eid ? 'operation.questionUpdated' : 'operation.questionAdded'), 'ok')
      void loadDashboard()
      return { ok: true }
    }
    if (eid) {
      const cur = S.q.find(x => x.id === eid)
      if (!cur) return { ok: false, message: tr('operation.loadFailed') }
      const next = { ...cur, ...d, st: cur.st === 'draft' ? 'draft' : 'stale', ver: (cur.ver || 1) + 1 }
      await repo.upsert(next)
      if (!sessionCurrent(stamp)) return { ok: false, abandoned: true }
      invalidateQuestionReads(); Object.assign(cur, next)
      S.sug = S.sug.filter(s => s.qid !== eid); logKey('activity.questionUpdated', { code: code({ id: eid }) }, 'info')
      toast(tr('operation.questionUpdated'), 'ok')
    } else {
      const title = (d.title || '').trim().replace(/\s+/g, ' ').toLowerCase()
      const clash = title && S.q.find(x => x.c === d.c && (x.title || '').trim().replace(/\s+/g, ' ').toLowerCase() === title)
      if (clash) return { ok: false, message: tr('validation.titleTaken', { code: code(clash) }) }
      const q = { id: S.n++, st: 'draft', src: 'manual', ver: 1, ...d }
      await repo.upsert(q)
      if (!sessionCurrent(stamp)) return { ok: false, abandoned: true }
      invalidateQuestionReads(); S.q.push(q); logKey('activity.questionAdded', { code: code(q) }, 'ok')
      toast(tr('operation.questionAdded'), 'ok')
    }
    return { ok: true }
  } catch (e) {
    if (!sessionCurrent(stamp)) return { ok: false, abandoned: true }
    const status = e?.response?.status, errCode = e?.response?.data?.code
    const uncertain = useRealApi() && (!e?.response || status >= 500)
    const message = uncertain ? tr('crud.saveUncertain')
      : errCode === 'TITLE_DUPLICATED' ? apiErrorMessage(e, tr('operation.titleTaken'))
        : status === 409 ? tr('operation.versionConflict') : apiErrorMessage(e, tr('operation.questionSaveFailed'))
    return { ok: false, uncertain, message }
  } finally {
    activeWrites.delete(key)
  }
}
export function askDeleteQuestion(id) {
  const q = S.q.find(x => x.id === id); if (!q) return
  if (busy(q)) return toast(tr('operation.deleteBusy'), 'er')
  if (S.confirmDelete === false) return deleteQuestion(id)
  openDialog('confirm', { title: tr('operation.deleteTitle', { code: code(q) }), text: tr('operation.deleteDescription'), cancel: tr('selection.keep'), ok: tr('common.delete'), danger: true, action: () => deleteQuestion(id) })
}
async function deleteQuestion(id) {
  return removeQuestions([id])
}

/* ---------- Môn học ---------- */
// Subject writes are never silently redirected to the local mock in a real session.
async function persistSubject(operation, subjectId, data, expectedUpdatedAt) {
  if (!useRealApi() || S.dataMode !== 'real' || S.status !== 'ready') return { ok: false, message: tr('operation.loadFailed') }
  if (operation !== 'create' && !expectedUpdatedAt) return { ok: false, message: tr('subject.refreshRequired') }
  const stamp = sessionStamp(), key = `${stamp}:subject:${subjectId ?? 'create'}`
  if (uncertainSubjects.has(key)) return { ok: false, uncertain: true, message: tr('subject.saveUncertain') }
  if (activeWrites.has(key)) return { ok: false, message: tr('crud.inProgress') }
  activeWrites.add(key); invalidateQuestionReads()
  try {
    let result
    if (operation === 'create') {
      result = await qbankApi.createSubject({ subjectCode: data.id, subjectName: data.name,
        description: data.dept || null, active: data.active, firstTopic: data.topic })
    } else if (operation === 'update') {
      result = await qbankApi.updateSubject(subjectId, { subjectCode: data.id, subjectName: data.name,
        description: data.dept || null, active: data.active, expectedUpdatedAt })
    } else {
      result = await qbankApi.archiveSubject(subjectId, expectedUpdatedAt)
    }
    if (!sessionCurrent(stamp)) return { ok: false, abandoned: true }
    if (!result || result.subjectId == null || !result.updatedAt) throw new Error('Invalid subject response')
    invalidateQuestionReads()
    const index = COURSES.findIndex(c => c.subjectId === result.subjectId)
    const mapped = subjectToCourse(result)
    if (index >= 0) {
      mapped.topics = COURSES[index].topics; mapped.topicIds = COURSES[index].topicIds
      Object.assign(COURSES[index], mapped)
    } else COURSES.push(mapped) // Topic IDs are loaded from the server, never fabricated.
    S.cq = ''
    activeWrites.delete(key)
    const refreshed = await loadQuestions()
    if (!sessionCurrent(stamp)) return { ok: false, abandoned: true }
    toast(tr(refreshed ? (operation === 'archive' ? 'subject.archived' : 'operation.courseSaved') : 'subject.savedRefreshFailed'), refreshed ? 'ok' : 'wa')
    return { ok: true }
  } catch (e) {
    if (!sessionCurrent(stamp)) return { ok: false, abandoned: true }
    const status = e?.response?.status, code = e?.response?.data?.code
    const uncertain = !e?.response || status >= 500
    if (uncertain) uncertainSubjects.add(key)
    return { ok: false, uncertain, message: uncertain ? tr('subject.saveUncertain')
      : code === 'SUBJECT_VERSION_CONFLICT' ? tr('subject.versionConflict')
        : apiErrorMessage(e, tr('subject.saveFailed')) }
  } finally { activeWrites.delete(key) }
}

export function addCourse({ id, name, dept, topic, active }) {
  if (useRealApi()) return persistSubject('create', null, { id, name, dept, topic, active })
  if (auth.isAuthenticated && !auth.isDemo) return { ok: false, message: tr('operation.loadFailed') }
  COURSES.push({ id, name, dept, topics: [topic], subjectId: null, topicIds: [null], active }); S.cq = ''
  logKey('activity.courseAdded', { course: `${id} – ${name}` }, 'ok'); toast(tr('operation.courseSaved'), 'ok')
  return { ok: true }
}
export function updateCourse(i, { id, name, dept, active }, snapshot = null) {
  const course = COURSES[i]
  if (useRealApi()) {
    if (!course || !snapshot || course.subjectId !== snapshot.subjectId) return { ok: false, message: tr('subject.refreshRequired') }
    return persistSubject('update', snapshot.subjectId, { id, name, dept, active }, snapshot.updatedAt)
  }
  if (auth.isAuthenticated && !auth.isDemo) return { ok: false, message: tr('operation.loadFailed') }
  const old = { ...course }; Object.assign(course, { id, name, dept, active })
  logKey('activity.courseUpdated', { old: `${old.id} – ${old.name}`, course: `${id} – ${name}` }, 'info'); toast(tr('operation.courseUpdated'), 'ok')
  return { ok: true }
}
export function deleteCourse(i) {
  if (useRealApi() || (auth.isAuthenticated && !auth.isDemo)) return { ok: false, message: tr('operation.loadFailed') }
  const course = COURSES[i]
  if (!course) return { ok: false }
  course.active = false
  toast(tr('subject.archived'), 'ok')
  return { ok: true }
}
export function askDeleteCourse(i, after) {
  const c = COURSES[i]; if (!c) return
  if (useRealApi()) {
    if (S.dataMode !== 'real') return toast(tr('operation.loadFailed'), 'er')
    const snapshot = { subjectId: c.subjectId, updatedAt: c.updatedAt }
    return openDialog('confirm', { title: tr('subject.archiveTitle', { course: `${c.id} – ${c.name}` }),
      text: tr('subject.archiveDescription'), cancel: tr('selection.keep'), ok: tr('subject.archiveButton'), danger: true,
      action: async () => {
        const result = await persistSubject('archive', snapshot.subjectId, null, snapshot.updatedAt)
        if (result.ok) after?.()
        else if (!result.abandoned) toast(result.message, 'er')
      } })
  }
  if (auth.isAuthenticated && !auth.isDemo) return toast(tr('operation.loadFailed'), 'er')
  openDialog('confirm', { title: tr('subject.archiveTitle', { course: `${c.id} – ${c.name}` }), text: tr('subject.archiveDescription'), cancel: tr('selection.keep'), ok: tr('subject.archiveButton'), danger: true, action: () => { if (deleteCourse(i).ok) after?.() } })
}

/* ---------- Xem xét đề xuất AI (có hoàn tác) ---------- */
/* Chế độ thật: mỗi đề xuất là 1 dòng trong ntt_ai_suggestions -> gọi POST /ai/suggestions/{id}/decision.
   Backend KHÔNG cho áp đề xuất target_field='question' (AI không được đổi cấu trúc/đáp án),
   nên các đề xuất đó được giữ nguyên và người duyệt phải sửa tay trong form câu hỏi. */
async function decideReal(id, ok) {
  const s = S.sug.find(x => x.qid === id), q = S.q.find(x => x.id === id)
  if (!s || !q) return
  const pending = (s.raw || []).filter(x => !x.decision || x.decision === 'pending')
  const applicable = ok ? pending.filter(x => suggestionReplacement(q, x) != null) : pending
  if (!applicable.length) return toast(tr('crud.suggestionsFailed'), 'wa')
  const stamp = sessionStamp(), key = `${stamp}:question:${id}`
  if (activeWrites.has(key)) return toast(tr('crud.inProgress'), 'wa')
  activeWrites.add(key); invalidateQuestionReads()
  try {
    // Accepting one suggestion creates a revision and invalidates the remaining suggestions.
    // Never submit the rest against that old revision.
    const decisions = ok ? applicable.slice(0, 1) : applicable
    let last
    for (const item of decisions) {
      last = await qbankApi.decideSuggestion(item.suggestionId, {
        decision: ok ? 'accepted' : 'rejected',
        finalText: ok ? item.proposedText || undefined : undefined,
        rejectReason: ok ? undefined : 'Người duyệt giữ bản gốc',
        expectedVersion: ok ? q.ver : undefined,
      })
      if (!sessionCurrent(stamp)) return
    }
    invalidateQuestionReads()
    if (last) {
      const ci = COURSES.findIndex(c => c.subjectId === last.subjectId)
      const tp = COURSES[ci]?.topicIds.indexOf(last.topicId) ?? q.tp
      const current = S.q.find(x => x.id === id)
      if (current) Object.assign(current, detailToQuestion(last, ci >= 0 ? ci : q.c, Math.max(0, tp)))
    }
    S.sug = S.sug.filter(x => x.qid !== id); S.rsel.delete(id)
    toast(ok ? tr('crud.suggestionApplied') : tr('operation.questionUpdated'), 'ok')
    void loadDashboard()
    return true
  } catch (e) {
    if (!sessionCurrent(stamp)) return false
    invalidateQuestionReads()
    const uncertain = !e?.response || e.response.status >= 500
    const message = uncertain ? tr('crud.decisionUncertain') : apiErrorMessage(e, tr('operation.questionSaveFailed'))
    // Some earlier rejections may already have committed. Refresh rather than assume all failed.
    activeWrites.delete(key)
    await loadQuestions()
    if (sessionCurrent(stamp)) toast(message, 'er')
    return false
  } finally {
    activeWrites.delete(key)
  }
}

export function decide(id, ok) {
  if (useRealApi() && S.dataMode === 'real') return decideReal(id, ok)
  if (auth.isAuthenticated && !auth.isDemo) return toast(tr('operation.loadFailed'), 'er')
  const s = S.sug.find(x => x.qid === id), q = S.q.find(x => x.id === id); if (!s || !q) return
  const prev = { text: q.text, st: q.st, updatedAt: q.updatedAt, by: q.by }, bak = { ...s }
  if (ok) { q.text = s.tx; q.updatedAt = nowStr(); q.by = S.name + ' (Duyệt AI)'; log(`Chấp nhận đề xuất AI cho ${code(q)} (có lưu vết)`, 'ok') }
  else log(`Từ chối đề xuất AI cho ${code(q)}, giữ nội dung gốc`, 'info')
  q.st = 'ok'; S.sug = S.sug.filter(x => x.qid !== id); S.rsel.delete(id)
  repo.upsert({ ...q, o: [...q.o] })
  toast(ok ? `Đã cập nhật ${code(q)} theo đề xuất AI` : `Đã giữ nguyên bản gốc ${code(q)}`, ok ? 'ok' : '', tr('bank.undoAction'), () => {
    Object.assign(q, prev); S.sug.push(bak); repo.upsert({ ...q, o: [...q.o] }); toast(`Đã hoàn tác quyết định cho ${code(q)}`, 'info')
  })
}
export async function bulkDecide(ok) {
  const ids = [...S.rsel].filter(id => S.sug.some(s => s.qid === id))
  if (!ids.length) return toast('Hãy chọn ít nhất một đề xuất để xử lý', 'wa')
  if (useRealApi() && S.dataMode === 'real') {
    // Duyệt hàng loạt tuần tự (không Promise.all) để backend xử lý từng câu một,
    // tránh nhiều revision đụng nhau trên cùng khoá lạc quan.
    const stamp = sessionStamp()
    let doneCount = 0, failCount = 0
    for (const id of ids) {
      if (!sessionCurrent(stamp)) return
      try {
        const succeeded = await decideReal(id, ok)
        if (!sessionCurrent(stamp)) return
        if (succeeded === true) doneCount++
        else failCount++
      } catch { failCount++ }
    }
    log(`${ok ? 'Chấp nhận' : 'Từ chối'} hàng loạt: ${doneCount} thành công, ${failCount} cần xử lý tay`, ok ? 'ok' : 'info')
    if (failCount) toast(`${failCount} đề xuất chưa xử lý được (cần sửa tay hoặc đã đổi ở nơi khác)`, 'wa')
    return
  }
  if (auth.isAuthenticated && !auth.isDemo) return toast(tr('operation.loadFailed'), 'er')
  ids.forEach(id => {
    const s = S.sug.find(x => x.qid === id), q = S.q.find(x => x.id === id)
    if (ok && s && q) { q.text = s.tx; q.updatedAt = nowStr(); q.by = S.name + ' (Duyệt AI)' }
    if (q) { q.st = 'ok'; repo.upsert({ ...q, o: [...q.o] }) }
  })
  S.sug = S.sug.filter(s => !ids.includes(s.qid)); S.rsel.clear()
  log(`${ok ? 'Chấp nhận' : 'Từ chối'} hàng loạt ${ids.length} đề xuất AI`, ok ? 'ok' : 'info')
  toast(`Đã ${ok ? 'chấp nhận' : 'từ chối'} ${ids.length} đề xuất`, ok ? 'ok' : '')
}

/* ---------- Nhập Moodle XML ---------- */
export async function importQuestions(items) {
  const { c, tp, l } = S.imp
  for (const it of items) {
    const q = { id: S.n++, text: it.text, o: it.o, a: it.a, c, tp, l, st: 'draft', updatedAt: nowStr(), by: S.name + ' (Moodle)', src: 'moodle_import', ver: 1 }
    await repo.upsert(q); S.q.push(q)
  }
  return items.length
}

/* ---------- Vai trò xem thử và phạm vi môn của giảng viên ---------- */
export const asUser = () => S.users.find(u => u.adminId === S.asId) || S.users.find(u => u.role === 'Giảng viên')
export const myScope = () => (S.role === 'admin' ? null : new Set(asUser()?.subjects || []))
export const subjOk = ci => { const sc = myScope(); return !sc || sc.has(COURSES[ci]?.id) }
export function previewRole(v) {
  const [r, id] = String(v).split(':'); if (!['admin', 'gv'].includes(r)) return
  S.role = r; S.asId = r === 'gv' ? id : null; S.cq = ''; toast(tr('operation.previewChanged'), 'ok')
}

/* ---------- Quản lý tài khoản (mock) ---------- */
const PW = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789'
export const tmpPwd = () => Array.from({ length: 12 }, () => PW[Math.random() * PW.length | 0]).join('')
export const userBy = id => S.users.find(u => u.adminId === id)
export const userUsed = u => S.q.some(q => (q.by || '').startsWith(u.name))
export const adminCount = () => S.users.filter(x => x.role === 'Quản trị viên' && x.status !== 'disabled').length
export function createUser(d) {
  S.users.push({ adminId: d.id, idCard: d.cc, name: d.n, role: d.r, totp: 'NOT_ENABLED', status: 'pending', passwordChangedAt: null, subjects: d.subs })
  log('Tạo tài khoản ' + d.n + ' (' + d.r + ')', 'ok'); openDialog('pw', { adminId: d.id, pw: tmpPwd(), title: 'Đã tạo tài khoản' })
}
export function resetPassword(id) {
  const u = userBy(id); u.passwordChangedAt = null; log('Cấp lại mật khẩu cho ' + u.name, 'wa')
  openDialog('pw', { adminId: id, pw: tmpPwd(), title: 'Mật khẩu tạm mới' })
}
export function saveRole(id, r, subs) {
  const u = userBy(id); if (!u || S.role !== 'admin') return ''
  if (u.role === 'Quản trị viên' && r !== u.role && adminCount() < 2) return tr('operation.lastAdmin')
  const s = r === 'Giảng viên' ? subs : [], chg = r !== u.role || s.join() !== (u.subjects || []).join()
  if (r !== u.role) { u.role = r; log('Đổi vai trò của ' + u.name + ' thành ' + r, 'info') }
  u.subjects = s; if (chg) { log('Cập nhật phạm vi môn của ' + u.name, 'info'); toast('Đã cập nhật vai trò và phạm vi môn', 'ok') }
  return ''
}
export function removeUser(id) {
  const u = userBy(id), used = userUsed(u)
  if (used) u.status = 'disabled'; else S.users.splice(S.users.indexOf(u), 1)
  log((used ? 'Vô hiệu hóa tài khoản ' : 'Xóa tài khoản ') + u.name, 'wa'); toast(used ? 'Đã vô hiệu hóa tài khoản' : 'Đã xóa tài khoản', 'ok')
}

/* ---------- Xác thực hai yếu tố (NOT_ENABLED → PENDING → ENABLED) ----------
   Mock: sinh khóa cục bộ, PENDING tự hết hạn sau 5 phút, nút "mô phỏng" để sang ENABLED.
   Real: dùng đúng API của be-web-admin —
     POST /2fa/enrollment            -> khóa Base32, Redis TTL 5 phút
     POST /2fa/enrollment/activate   -> ghi ntt_totp_credentials (pending), chờ Flutter xác nhận
     GET  /2fa/status                -> { totpEnabled, pending, expiresInSeconds, periodSeconds }
     DELETE /2fa/enrollment/pending  -> hủy chờ
     GET  /2fa/events (SSE)          -> server báo ngay khi mobile xác nhận
   Poll 3s chỉ là lưới an toàn cho trường hợp SSE bị proxy chặn. */
let tt, stopSse = null
const enrollmentRepo = new EnrollmentRepository()
const currentTokens = () => new TokenLogin()
const focusTfa = () => nextTick(() => setTimeout(() => { const h = document.getElementById('tfa-t'); if (h) { h.tabIndex = -1; h.focus({ preventScroll: true }) } }, 60))
function stopTfaWatch() { clearInterval(tt); tt = null; if (stopSse) { stopSse(); stopSse = null } }

/** Đọc trạng thái 2FA hiện tại (mở trang Cài đặt, hoặc khi SSE báo đổi). */
export async function loadTfaStatus() {
  if (!useRealApi()) return
  const res = await enrollmentRepo.getTwoFaStatus(currentTokens())
  if (!res.success) return
  S.tfa = { st: res.status, until: res.status === 'PENDING' ? Date.now() + (res.pendingExpiresInSeconds || 0) * 1000 : 0 }
  S.now = Date.now()
  if (res.status === 'PENDING') watchTfaReal()
  else stopTfaWatch()
}

function watchTfaReal() {
  clearInterval(tt)
  tt = setInterval(async () => {
    S.now = Date.now()
    if (S.tfa.st !== 'PENDING') return stopTfaWatch()
    if (S.tfa.until - S.now <= 0) { await loadTfaStatus(); return }
    await loadTfaStatus()
  }, 3000)
  if (!stopSse) {
    stopSse = enrollmentRepo.watchTwoFaStatus(currentTokens(), {
      onChange: () => { loadTfaStatus() },
    })
  }
}

export async function tfaOpen() {
  if (!useRealApi()) {
    S.tfaSec = Array.from({ length: 16 }, () => 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'[Math.random() * 32 | 0]).join('')
    return openDialog('tfa')
  }
  S.tfaBusy = true
  try {
    const res = await enrollmentRepo.startEnrollment(currentTokens(), 30)
    if (!res.success) {
      if (res.status === 409) { await loadTfaStatus(); toast(tr('enrollment.alreadyEnabled'), 'wa') }
      else toast(res.message, 'er')
      return
    }
    S.tfaSec = res.data.secretKey
    S.tfaPeriod = res.data.periodSeconds || 30
    S.tfaKeyExpiresAt = Date.now() + (res.data.expiresInSeconds || 300) * 1000
    openDialog('tfa')
  } finally {
    S.tfaBusy = false
  }
}

/** "Tiếp tục" trong modal: ghi khóa xuống MariaDB ở trạng thái chờ xác nhận. */
export async function tfaStart() {
  if (useRealApi()) {
    S.tfaBusy = true
    try {
      const res = await enrollmentRepo.activateEnrollment(currentTokens(), S.tfaPeriod || 30)
      if (!res.success) {
        toast(res.message, 'er')
        // Khóa chờ ở Redis đã hết hạn (410): xin khóa mới để người dùng nhập lại.
        if (res.expired) { closeDialog(); await tfaOpen() }
        return false
      }
      S.tfa = { st: 'PENDING', until: Date.now() + (res.data.expiresInSeconds || 600) * 1000 }
      S.now = Date.now()
      logKey('twoFactor.logKeyWritten', {}, 'info')
      toast(tr('twoFactor.waiting'), 'ok')
      watchTfaReal()
      focusTfa()
      return true
    } finally {
      S.tfaBusy = false
    }
  }
  S.tfa = { st: 'PENDING', until: Date.now() + 300000 }; S.now = Date.now(); clearInterval(tt)
  tt = setInterval(() => {
    S.now = Date.now(); if (S.tfa.st !== 'PENDING') return clearInterval(tt)
    if (S.tfa.until - S.now <= 0) { S.tfa = { st: 'NOT_ENABLED', until: 0 }; clearInterval(tt); toast(tr('twoFactor.pendingExpired'), 'wa') }
  }, 1000)
  logKey('twoFactor.logStarted', {}, 'info'); toast(tr('twoFactor.keyCreated'), 'ok'); focusTfa()
}

export function tfaAsk() {
  openDialog('confirm', { title: tr('twoFactor.cancelTitle'), text: tr('twoFactor.cancelDescription'), cancel: tr('auth.back'), ok: tr('twoFactor.cancelAction'), action: tfaReset })
}

export async function tfaReset() {
  stopTfaWatch()
  if (useRealApi()) {
    const ok = await enrollmentRepo.cancelPendingEnrollment(currentTokens())
    S.tfa = { st: 'NOT_ENABLED', until: 0 }
    S.tfaSec = ''
    logKey('twoFactor.logCancelled', {}, 'wa')
    toast(ok ? tr('twoFactor.cancelled') : tr('twoFactor.cancelUnconfirmed'), ok ? 'ok' : 'wa')
    focusTfa()
    return
  }
  S.tfa = { st: 'NOT_ENABLED', until: 0 }; logKey('twoFactor.logCancelled', {}, 'wa'); toast(tr('twoFactor.cancelled'), 'ok'); focusTfa()
}

/** Chỉ dùng ở chế độ demo: mô phỏng ứng dụng di động đã xác nhận. */
export function tfaDone() { stopTfaWatch(); S.tfa = { st: 'ENABLED', until: 0 }; logKey('twoFactor.logEnabled', {}, 'ok'); toast(tr('twoFactor.enabled'), 'ok'); focusTfa() }

/* Đóng modal/hủy trang khi còn khóa chờ ở Redis: dọn để không để rác 5 phút.
   (pagehide dùng keepalive giống ModalActivate2FA của fe_web_admin) */
export async function tfaDiscardKey() {
  if (!useRealApi()) return
  S.tfaSec = ''
  try { await enrollmentRepo.cancelEnrollment(currentTokens()) } catch { /* bỏ qua */ }
}
