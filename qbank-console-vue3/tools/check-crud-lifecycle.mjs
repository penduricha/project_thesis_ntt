import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { JSDOM } from 'jsdom'

const dom = new JSDOM('<!doctype html><div id="app"></div>', { url: 'http://localhost:5173/' })
for (const key of ['window', 'document', 'navigator', 'Element', 'HTMLElement', 'HTMLInputElement', 'SVGElement', 'Document', 'ShadowRoot', 'Node', 'localStorage', 'sessionStorage', 'DOMParser']) {
  Object.defineProperty(globalThis, key, { configurable: true, value: dom.window[key] })
}
globalThis.BroadcastChannel = undefined
const { createServer } = await import('vite')
const vue = await import('vue')
const { parse, compileScript, compileTemplate } = await import('@vue/compiler-sfc')
const server = await createServer({ server: { middlewareMode: true }, appType: 'custom', logLevel: 'error' })
let app
const deferred = () => { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
const detail = (id, stem = 'Question') => ({ questionId: id, subjectId: 7, topicId: 42, stem, optionA: 'A', optionB: 'B', optionC: 'C', optionD: 'D', correctLabel: 'A', difficulty: 2, version: 1, validationStatus: 'not_checked' })
const listItem = (id, stem = 'Question') => ({ ...detail(id, stem), options: ['A', 'B', 'C', 'D'] })
try {
  const store = await server.ssrLoadModule('/src/store.js')
  const { S, COURSES, loadQuestions, resetQuestionSession, saveQuestion, removeQuestions, askDeleteQuestion, decide, loadSuggestions } = store
  const { auth } = await server.ssrLoadModule('/src/auth/authStore.js')
  const { qbankApi } = await server.ssrLoadModule('/src/api/qbankApi.js')
  const { t } = await server.ssrLoadModule('/src/i18n/index.js')
  const { suggestionReplacement, suggestionsToUi, questionToUpdate, detailToQuestion } = await server.ssrLoadModule('/src/api/mappers.js')
  const previewQuestion = { id: 1, text: 'Before typo after', o: ['yes', 'no', 'x', 'y'] }
  const fix = { targetField: 'stem', originalText: 'typo', proposedText: 'text' }
  assert.equal(suggestionReplacement(previewQuestion, fix), 'Before text after')
  assert.equal(suggestionsToUi([fix], previewQuestion).tx, 'Before text after')
  assert.equal(suggestionReplacement({ ...previewQuestion, text: 'typo typo' }, fix), null)
  const original = detailToQuestion({ ...detail(1), topicId: null, difficulty: 5, contentFormat: 'html' }, 0, 0)
  const update = questionToUpdate({ ...original, topicChanged: false }, { topicIds: [42] })
  assert.equal(update.topicId, null, 'unchanged null topic is preserved')
  assert.equal(update.difficulty, 5, 'unrepresented difficulty is not silently changed')
  assert.equal(update.contentFormat, 'html', 'content format is preserved')
  assert.equal(questionToUpdate({ ...original, topicChanged: true }, { topicIds: [42] }).topicId, 42)
  assert.equal(questionToUpdate({ ...original, l: 0 }, { topicIds: [42] }).difficulty, 1)
  auth.isAuthenticated = true; auth.adminId = 7; auth.isDemo = false
  qbankApi.subjects = async () => [{ subjectId: 7, subjectCode: 'DB', subjectName: 'Database' }]
  qbankApi.topics = async () => [{ topicId: 42, topicName: 'Basics' }]
  qbankApi.dashboardSummary = async () => null
  qbankApi.dashboardActivity = async () => []
  qbankApi.latestSuggestions = async () => []

  // The newer read wins even when an adapter ignores abort and resolves out of order.
  resetQuestionSession()
  const first = deferred(), second = deferred(); let calls = 0
  qbankApi.listQuestions = () => (++calls === 1 ? first.promise : second.promise)
  const one = loadQuestions(); await new Promise(r => setTimeout(r, 0))
  const two = loadQuestions(); await new Promise(r => setTimeout(r, 0))
  second.resolve({ content: [listItem(2, 'New')], totalPages: 1 }); await two
  first.resolve({ content: [listItem(1, 'Old')], totalPages: 1 }); await one
  assert.deepEqual(S.q.map(q => q.id), [2], 'old response must not overwrite new response')

  // A component/app lifetime abort suppresses a late adapter response even if the adapter ignores it.
  const late = deferred(), lifetime = new AbortController(); qbankApi.listQuestions = () => late.promise
  const aborted = loadQuestions({ signal: lifetime.signal }); await new Promise(r => setTimeout(r, 0))
  lifetime.abort(); late.resolve({ content: [listItem(4)], totalPages: 1 }); await aborted
  assert.deepEqual(S.q.map(q => q.id), [2])

  // Aborted app lifetime/log-out cannot repopulate the shared store.
  const oldRead = deferred(); qbankApi.listQuestions = () => oldRead.promise
  const pending = loadQuestions(); await new Promise(r => setTimeout(r, 0))
  resetQuestionSession(); auth.isAuthenticated = false
  oldRead.resolve({ content: [listItem(3)], totalPages: 1 }); await pending
  assert.equal(S.q.length, 0); assert.equal(COURSES.length, 0)

  auth.isAuthenticated = true
  qbankApi.subjects = async () => { throw new Error('offline') }
  await loadQuestions()
  assert.equal(S.status, 'error'); assert.equal(S.dataMode, 'unavailable'); assert.equal(S.q.length, 0, 'no mock fallback for real login')

  qbankApi.subjects = async () => [{ subjectId: 7, subjectCode: 'DB', subjectName: 'Database' }]
  qbankApi.listQuestions = async () => ({ content: [listItem(1), listItem(2)], totalPages: 1 })
  await loadQuestions()
  const input = { c: 0, tp: 0, text: 'Edited', o: ['A', 'B', 'C', 'D'], a: 0, l: 1, title: '', fb: '' }
  qbankApi.updateQuestion = async () => { throw { response: { status: 409, data: { code: 'VERSION_CONFLICT' } } } }
  const conflict = await saveQuestion(input, 1, 1)
  assert.equal(conflict.ok, false); assert.equal(conflict.uncertain, false); assert.equal(S.q[0].text, 'Question')
  qbankApi.createQuestion = async () => { throw new Error('timeout') }
  const unknown = await saveQuestion(input, 0)
  assert.equal(unknown.ok, false); assert.equal(unknown.uncertain, true); assert.equal(S.q.length, 2)

  const write = deferred(); let posted = 0
  qbankApi.createQuestion = () => { posted++; return write.promise }
  const creating = saveQuestion(input, 0)
  assert.equal((await saveQuestion(input, 0)).ok, false); assert.equal(posted, 1)
  assert.equal(await loadQuestions(), false, 'reads cannot race an in-flight write')
  write.resolve(detail(3, 'Created')); assert.equal((await creating).ok, true)
  assert.ok(S.q.some(q => q.id === 3))

  // Single delete goes to the real archive endpoint, not the in-memory repository.
  const archived = []
  qbankApi.archiveQuestion = async id => { archived.push(id) }
  S.confirmDelete = false; await askDeleteQuestion(3)
  assert.deepEqual(archived, [3]); assert.ok(!S.q.some(q => q.id === 3))

  // Partial archive: only successes are removed; reconciliation fetches the authoritative list.
  qbankApi.archiveQuestion = async id => { if (id === 2) throw { response: { status: 403 } } }
  qbankApi.listQuestions = async () => ({ content: [listItem(2)], totalPages: 1 })
  await removeQuestions([1, 2])
  assert.deepEqual(S.q.map(q => q.id), [2]); assert.equal(S.toast.msg, t('crud.archivePartial', { done: 1, total: 2 }))

  // Failed suggestions are explicitly an error, not an empty/clean result.
  S.q[0].st = 'flag'
  qbankApi.latestSuggestions = async () => { throw new Error('offline') }
  await loadSuggestions(); assert.equal(S.suggestionsStatus, 'error')
  qbankApi.latestSuggestions = async () => []
  S.suggestionsStatus = 'ready'; S.q[0].st = 'draft'

  // Accept creates one revision; other pending suggestions are for the old revision.
  S.sug = [{ qid: 2, raw: [
    { suggestionId: 10, targetField: 'stem', originalText: 'Question', proposedText: 'A', decision: 'pending' },
    { suggestionId: 11, targetField: 'option_a', originalText: 'A', proposedText: 'B', decision: 'pending' },
  ] }]
  const decisions = []
  qbankApi.decideSuggestion = async id => { decisions.push(id); return detail(2, 'Revised') }
  await decide(2, true)
  assert.deepEqual(decisions, [10]); assert.equal(S.sug.length, 0); assert.equal(S.q[0].text, 'Revised')

  // Actual dialog: 409 and timeout keep the user's edits; timeout blocks accidental retry.
  for (const name of ['BaseModal', 'CourseCombobox', 'TopicCombobox', 'CourseQuestionImport', 'QuestionDialog']) {
    const component = (await server.ssrLoadModule(`/src/components/${name}.vue`)).default
    const { descriptor } = parse(readFileSync(`src/components/${name}.vue`, 'utf8'))
    const script = compileScript(descriptor, { id: `test-${name}` })
    const compiled = compileTemplate({ source: descriptor.template.content, filename: `${name}.vue`, id: `test-${name}`, compilerOptions: { mode: 'function', bindingMetadata: script.bindings } })
    assert.deepEqual(compiled.errors, [])
    component.render = new Function('Vue', compiled.code)(vue)
  }
  const Component = (await server.ssrLoadModule('/src/components/QuestionDialog.vue')).default
  const isOpen = vue.ref(true)
  let submittedBody
  qbankApi.updateQuestion = async (_id, body) => { submittedBody = body; throw { response: { status: 409, data: { code: 'VERSION_CONFLICT' } } } }
  app = vue.createApp({ render: () => isOpen.value ? vue.h(Component, { id: 2, onClose: () => { isOpen.value = false } }) : null })
  app.provide(vue.ssrContextKey, { modules: new Set() }); app.config.globalProperties.$t = t; app.mount('#app'); await vue.nextTick()
  const stem = document.querySelector('#f-t'); stem.value = 'Edited'; stem.dispatchEvent(new dom.window.Event('input', { bubbles: true }))
  S.q[0].ver = 9 // A background refresh must not silently change the version the editor opened with.
  document.querySelector('.modal-footer .pri').click()
  await new Promise(r => setTimeout(r, 10)); await vue.nextTick()
  assert.equal(submittedBody.expectedVersion, 1); assert.equal(isOpen.value, true); assert.equal(stem.value, 'Edited')
  assert.equal(document.querySelector('#f-e').textContent, t('operation.versionConflict'))
  qbankApi.updateQuestion = async () => { throw new Error('timeout') }
  document.querySelector('.modal-footer .pri').click()
  await new Promise(r => setTimeout(r, 10)); await vue.nextTick()
  assert.equal(isOpen.value, true); assert.equal(document.querySelector('#f-e').textContent, t('crud.saveUncertain'))
  assert.equal(document.querySelector('.modal-footer .pri').disabled, true, 'uncertain write cannot be repeated accidentally')
  console.log('CRUD lifecycle: stale reads, session cleanup, real-mode failure, conflicts, uncertain writes, duplicate submit and partial archive passed')
} finally {
  app?.unmount(); await server.close(); dom.window.close()
}
