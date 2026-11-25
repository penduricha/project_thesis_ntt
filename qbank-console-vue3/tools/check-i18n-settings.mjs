import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createSSRApp, computed } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { routerKey, routeLocationKey } from 'vue-router'

const storage = new Map([['qbank-locale', 'es-ES']])
const attributes = new Map()
globalThis.localStorage = { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value) }
globalThis.document = { documentElement: { getAttribute: key => attributes.get(key) ?? null, setAttribute: (key, value) => attributes.set(key, value), style: {} } }
const server = await createServer({ server: { middlewareMode: true }, appType: 'custom', logLevel: 'error' })
try {
  const { default: NotFoundView } = await server.ssrLoadModule('/src/views/NotFoundView.vue')
  const { default: CourseCombobox } = await server.ssrLoadModule('/src/components/CourseCombobox.vue')
  const { auth } = await server.ssrLoadModule('/src/auth/authStore.js')
  const { parseMoodleXml } = await server.ssrLoadModule('/src/services/moodleXml.js')
  const { EnrollmentRepository } = await server.ssrLoadModule('/src/auth/EnrollmentRepository.js')
  const { default: TasksView } = await server.ssrLoadModule('/src/views/TasksView.vue')
  const { default: SearchView } = await server.ssrLoadModule('/src/views/SearchView.vue')
  const { default: MoodleView } = await server.ssrLoadModule('/src/views/MoodleView.vue')
  const { resolveSampleQuery } = await server.ssrLoadModule('/src/services/searchExamples.js')
  assert.equal(resolveSampleQuery('easy questions', { label: 'easy questions', query: 'câu hỏi dễ' }), 'câu hỏi dễ')
  assert.equal(resolveSampleQuery('user edit', { label: 'easy questions', query: 'câu hỏi dễ' }), 'user edit')
  const { default: ReviewView } = await server.ssrLoadModule('/src/views/ReviewView.vue')
  const { apiErrorMessage, qbankApi } = await server.ssrLoadModule('/src/api/qbankApi.js')
  const { default: LlmView } = await server.ssrLoadModule('/src/views/LlmView.vue')
  const { default: Settings } = await server.ssrLoadModule('/src/views/SettingsView.vue')
  const { default: UserCreateDialog } = await server.ssrLoadModule('/src/components/UserCreateDialog.vue')
  const { default: UserDetailDialog } = await server.ssrLoadModule('/src/components/UserDetailDialog.vue')
  const { S, COURSES, askDeleteQuestion, saveRole, runAI, tfaAsk, logKey, logText, askDeleteCourse, addCourse, updateCourse, deleteCourse } = await server.ssrLoadModule('/src/store.js')
  const { default: TopicManagerDialog } = await server.ssrLoadModule('/src/components/TopicManagerDialog.vue')
  const { i18n, setLocale, t, missingKeys, locale, LOCALES } = await server.ssrLoadModule('/src/i18n/index.js')
  assert.equal(locale.code, 'es-ES', 'restores Spanish from storage')
  assert.ok(LOCALES.some(l => l.code === 'es-ES' && l.label === 'Español (España)'))
  const reactiveLabel = computed(() => t('settings.themeLight'))
  const expected = {
    vi: ['Sáng', 'Thông báo khi tác vụ AI hoàn tất'],
    en: ['Light', 'Notify when an AI task finishes'],
    zh: ['浅色', 'AI'], ko: ['밝게', 'AI'], ja: ['ライト', 'AI'],
    'es-ES': ['Claro', 'Avisar cuando termine una tarea de IA'],
  }
  assert.deepEqual(missingKeys(), {})
  const localeTables = await Promise.all(['vi', 'en', 'zh', 'ko', 'ja', 'es-ES'].map(code => import(`../src/i18n/locales/${code === 'es-ES' ? 'es' : code}.js`).then(m => [code, m.default])))
  const base = localeTables[0][1]
  const params = text => (text.match(/\{\w+\}/g) || []).sort().join(',')
  for (const [code, table] of localeTables) {
    assert.deepEqual(Object.keys(table).sort(), Object.keys(base).sort(), `${code}: locale keys differ`)
    for (const key of Object.keys(base)) assert.equal(params(table[key]), params(base[key]), `${code}: parameters for ${key}`)
  }
  for (const key of ['selection.keep', 'error.goHome', 'taskStatus.cancelled']) {
    assert.ok(Object.hasOwn(base, key), `required key: ${key}`)
  }
  const storedActivity = { key: 'job.logAccepted', params: { n: 2 } }
  setLocale('en'); const englishActivity = logText(storedActivity)
  setLocale('zh'); assert.notEqual(logText(storedActivity), englishActivity)
  assert.equal(logText('Original server text'), 'Original server text')
  for (const [language, [theme, notification]] of Object.entries(expected)) {
    setLocale(language)
    assert.equal(storage.get('qbank-locale'), language)
    assert.equal(attributes.get('lang'), language === 'zh' ? 'zh-CN' : language)
    const html = await renderToString(createSSRApp(Settings).use(i18n))
    S.lm[0].note = 'llmState.nodeHealthy'
    S.lm[0].latency = '0.6'
    const llmHtml = await renderToString(createSSRApp(LlmView).use(i18n))
    assert.ok(llmHtml.includes(t('llmState.nodeHealthy', { seconds: '0.6' })), `${language}: translated node response`)
    assert.ok(llmHtml.includes(t('llmState.freeSlots', { free: 2, total: 5 })), `${language}: translated slot count`)
    assert.ok(!llmHtml.includes('llmState.'), `${language}: untranslated LLM key`)
    S.lm[0].note = ''
    let response = null
    const enrollment = new EnrollmentRepository({
      postDataToStartEnrollmentRequest: async () => response,
      postDataToActivateEnrollmentRequest: async () => response,
      getDataTwoFaStatusRequest: async () => response,
    })
    assert.equal((await enrollment.startEnrollment(null)).message, t('apiError.network'))
    response = { status: 401, data: {} }
    assert.equal((await enrollment.startEnrollment(null)).message, t('apiError.sessionExpired'))
    response = { status: 410, data: {} }
    const expired = await enrollment.activateEnrollment(null)
    assert.equal(expired.message, t('enrollment.secretExpired'))
    assert.equal(expired.expired, true)
    response = { status: 400, data: { message: 'Original API message' } }
    assert.equal((await enrollment.startEnrollment(null)).message, 'Original API message')
    const comboboxHtml = await renderToString(createSSRApp(CourseCombobox).use(i18n))
    assert.ok(comboboxHtml.includes(t('bank.allCourses')), `${language}: default course placeholder`)
    const oldAuth = auth.isAuthenticated
    for (const loggedIn of [true, false]) {
      auth.isAuthenticated = loggedIn
      const notFoundApp = createSSRApp(NotFoundView).use(i18n)
      notFoundApp.provide(routerKey, { replace: async () => {}, back() {} })
      notFoundApp.provide(routeLocationKey, { fullPath: '/not-found' })
      const notFoundHtml = await renderToString(notFoundApp)
      assert.ok(notFoundHtml.includes(t(loggedIn ? 'error.goHome' : 'error.goLogin')), `${language}: 404 destination`)
    }
    auth.isAuthenticated = oldAuth
    assert.equal(apiErrorMessage({}), t('apiError.network'), `${language}: localized network fallback`)
    assert.equal(apiErrorMessage({ response: { status: 401 } }), t('apiError.sessionExpired'))
    assert.equal(apiErrorMessage({ response: { status: 400, data: { message: 'Original server message' } } }), 'Original server message')
    const topicsContext = {}
    await renderToString(createSSRApp(TopicManagerDialog, { ci: 0 }).use(i18n), topicsContext)
    assert.ok(topicsContext.teleports.body.includes(t('topics.title', { id: COURSES[0].id, name: COURSES[0].name })), `${language}: chapter manager title`)
    S.q = [{ id: 123, c: 0, tp: 0, text: 'API question', o: ['a', 'b', 'c', 'd'], a: 0 }]
    S.sug = [{ qid: 123, tx: 'API question', is: [{ k: 'Chính tả', d: 'Original API explanation' }], diff: 'Dễ', sev: 'Cao', tid: 'T-1' }]
    S.tasks = [
      { id: 'T-1', qid: 123, st: 'succeeded', msgKey: 'job.foundDifficulty', msgParams: { n: 2, d: 'Dễ' } },
      { id: 'T-2', qid: 124, st: 'failed', msg: 'Original server detail' },
      { id: 'T-3', qid: 125, st: 'cancelled', msg: '' },
    ]
    const taskApp = createSSRApp(TasksView).use(i18n)
    taskApp.provide(routerKey, { push() {} })
    const taskHtml = await renderToString(taskApp)
    assert.ok(taskHtml.includes(t('job.foundDifficulty', { n: 2, d: t('difficulty.0') })), `${language}: task message`)
    assert.ok(taskHtml.includes('Original server detail'), `${language}: server task message preserved`)
    assert.ok(taskHtml.includes(t('taskStatus.cancelled')), `${language}: cancelled status`)
    S.tasks = []
    tfaAsk()
    assert.equal(S.dlg.props.title, t('twoFactor.cancelTitle'))
    assert.equal(S.dlg.props.text, t('twoFactor.cancelDescription'))
    const course = COURSES[0]
    askDeleteCourse(0)
    assert.equal(S.dlg.props.title, t('operation.deleteCourseTitle', { course: `${course.id} – ${course.name}` }))
    assert.equal(S.dlg.props.text, t('operation.deleteCourseDescription'))
    assert.equal(S.dlg.props.ok, t('course.deleteCourse'))
    S.confirmDelete = true
    askDeleteQuestion(123)
    assert.equal(S.dlg.props.title, t('operation.deleteTitle', { code: 'CH123' }))
    assert.equal(S.dlg.props.text, t('operation.deleteDescription'))
    const otherStatus = S.users[1].status
    S.users[1].status = 'disabled'
    assert.equal(saveRole(S.users[0].adminId, 'Giảng viên', []), t('operation.lastAdmin'))
    assert.equal(S.users[0].role, 'Quản trị viên')
    S.users[1].status = otherStatus
    const reviewApp = createSSRApp(ReviewView).use(i18n)
    reviewApp.provide(routerKey, { push() {} })
    reviewApp.component('RouterLink', { template: '<a><slot /></a>' })
    const reviewHtml = await renderToString(reviewApp)
    assert.ok(reviewHtml.includes(t('issue.spelling')), `${language}: issue label`)
    assert.ok(reviewHtml.includes(t('severity.high')), `${language}: severity label`)
    assert.ok(reviewHtml.includes(t('difficulty.0')), `${language}: difficulty label`)
    assert.ok(reviewHtml.includes('Original API explanation'), `${language}: API text preserved`)
    assert.equal(S.sug[0].is[0].k, 'Chính tả', 'filter value preserved')
    S.q = []; S.sug = []
    // Fake DOM nodes isolate XML validation and preserve the original question text.
    const answer = (text, correct = false) => ({ getAttribute: () => correct ? '100' : '0', querySelector: () => ({ textContent: text }) })
    let questionText = '', answers = []
    const questionNode = { querySelector: selector => ({ textContent: selector === 'questiontext > text' ? questionText : '' }), querySelectorAll: () => answers }
    globalThis.DOMParser = class { parseFromString() { return { querySelector: () => null, querySelectorAll: () => [questionNode] } } }
    let parsed = parseMoodleXml('<quiz/>')
    assert.equal(parsed.reasons[0], t('importXml.missingStem', { label: t('importXml.questionNumber', { n: 1 }) }))
    questionText = 'Original stem'
    answers = [answer('A')]
    parsed = parseMoodleXml('<quiz/>')
    assert.equal(parsed.reasons[0], t('importXml.optionCount', { label: t('importXml.questionNumber', { n: 1 }), n: 1 }))
    answers = [answer('A', true), answer('B'), answer('C'), answer('D')]
    parsed = parseMoodleXml('<quiz/>')
    assert.equal(parsed.items[0].text, 'Original stem')
    assert.equal(parsed.items[0].a, 0)
    assert.deepEqual(parsed.reasons, [])
    const searchApp = createSSRApp(SearchView).use(i18n)
    searchApp.provide(routerKey, { push() {} })
    const searchHtml = await renderToString(searchApp)
    assert.ok(searchHtml.includes(t('search.exampleDatabase')), `${language}: search example`)
    assert.ok(searchHtml.includes(t('search.exampleAi')), `${language}: search example`)
    S.xml = ''; S.sel.clear()
    const exportHtml = await renderToString(createSSRApp(MoodleView).use(i18n))
    assert.ok(exportHtml.includes(t('moodle.noFileYet')), `${language}: export empty state`)
    const userContext = {}
    await renderToString(createSSRApp(UserCreateDialog).use(i18n), userContext)
    const dialog = userContext.teleports.body
    assert.match(dialog, /value="Giảng viên"/u, `${language}: stable lecturer value`)
    assert.match(dialog, /value="Quản trị viên"/u, `${language}: stable administrator value`)
    assert.ok(dialog.includes(t('admin.roleLecturer')), `${language}: lecturer label`)
    assert.ok(dialog.includes(t('admin.roleAdmin')), `${language}: administrator label`)
    const detailsContext = {}
    await renderToString(createSSRApp(UserDetailDialog, { adminId: S.users[2].adminId }).use(i18n), detailsContext)
    assert.ok(detailsContext.teleports.body.includes(t('admin.roleLecturer')), `${language}: account detail role`)
    assert.ok(detailsContext.teleports.body.includes(t('account.status.active')), `${language}: account status`)
    assert.ok(html.includes('Español (España)'), `${language}: Spanish option missing`)
    assert.ok(html.includes(theme), `${language}: theme label missing`)
    assert.ok(html.includes(notification), `${language}: notification label missing`)
    assert.equal(reactiveLabel.value, t('settings.themeLight'))
    assert.ok(!html.includes('tfa.state.'), `${language}: untranslated key`)
    assert.ok(!html.includes('settings.themeLight'), `${language}: untranslated key`)
    for (const state of ['NOT_ENABLED', 'PENDING', 'ENABLED']) {
      S.tfa.st = state
      const statusHtml = await renderToString(createSSRApp(Settings).use(i18n))
      assert.ok(statusHtml.includes(t('tfa.state.' + state)), `${language}: ${state} status`)
      assert.ok(statusHtml.includes(t('tfa.description.' + state)), `${language}: ${state} description`)
    }
    S.tfa.st = 'NOT_ENABLED'
  }
  // Course actions keep the underlying names untouched while localising notifications and log entries.
  setLocale('en')
  const originalLength = COURSES.length
  addCourse({ id: 'TEST', name: 'Original name', dept: '', topic: 'Original topic', active: true })
  assert.equal(S.toast.msg, t('operation.courseSaved'))
  assert.equal(COURSES.at(-1).topics[0], 'Original topic')
  updateCourse(originalLength, { id: 'TEST', name: 'Changed name', dept: '', active: true })
  assert.equal(S.toast.msg, t('operation.courseUpdated'))
  assert.ok(logText(S.log[0][1]).includes('Changed name'))
  deleteCourse(originalLength)
  assert.equal(S.toast.msg, t('operation.courseDeleted', { n: 0 }))
  assert.equal(COURSES.length, originalLength)
  // The same task and activity must render in the newly selected language.
  S.tasks = [{ id: 'switch', qid: 123, st: 'succeeded', msgKey: 'job.passed', msgParams: {} }]
  const switchApp = () => { const app = createSSRApp(TasksView).use(i18n); app.provide(routerKey, { push() {} }); return app }
  setLocale('en'); const beforeSwitch = await renderToString(switchApp())
  setLocale('ja'); const afterSwitch = await renderToString(switchApp())
  assert.ok(beforeSwitch.includes(base['job.passed']) === false)
  assert.ok(afterSwitch.includes(t('job.passed')))
  assert.equal(S.tasks[0].msgKey, 'job.passed')
  // Test mock AI success and timeout without changing VITE_DEMO_MODE or contacting a server.
  const savedTimeout = globalThis.setTimeout
  const timers = []
  globalThis.setTimeout = (callback, delay) => { timers.push({ callback, delay }); return 123456 }
  try {
    auth.isAuthenticated = false
    S.dataMode = 'mock'; S.colab = true; S.notifyTasks = false
    S.tasks = []; S.sug = []
    S.q = [{ id: 444, text: 'Test?', o: ['one', 'two', 'three', 'four'], a: 0 }]
    runAI([444])
    timers.find(x => x.delay === 600).callback()
    timers.find(x => x.delay >= 2200 && x.delay < 3200).callback()
    assert.equal(S.tasks[0].st, 'succeeded')
    assert.ok(['job.passed', 'job.foundDifficulty'].includes(S.tasks[0].msgKey))
    timers.length = 0; S.tasks = []; S.colab = false
    runAI([444])
    timers.find(x => x.delay >= 2200 && x.delay < 3200).callback()
    assert.equal(S.tasks[0].st, 'failed')
    assert.equal(S.tasks[0].msgKey, 'job.llmTimeout')
  } finally { globalThis.setTimeout = savedTimeout }
  // Exercise the real API poll paths using mocked API methods, never external services.
  const savedMethods = { ...qbankApi }
  try {
    auth.isAuthenticated = true; auth.isDemo = false; S.dataMode = 'real'; S.notifyTasks = false
    S.q = [{ id: 445, text: 'Original question', o: ['a','b','c','d'], a: 0 }]
    qbankApi.dashboardSummary = async () => null
    qbankApi.dashboardActivity = async () => []
    qbankApi.aiCheck = async () => ({ taskId: 'server-task', status: 'pending', pollAfterMs: 1 })
    qbankApi.jobSuggestions = async () => []
    let calls = 0
    qbankApi.job = async () => ++calls === 1 ? { status: 'running' } : { status: 'succeeded' }
    S.tasks = []; await runAI([445])
    assert.equal(calls, 2); assert.equal(S.tasks[0].msgKey, 'job.passed')
    qbankApi.job = async () => ({ status: 'failed', errorCode: 'SERVER_ERROR' })
    S.tasks = []; await runAI([445])
    assert.equal(S.tasks[0].msgKey, 'job.errorCode')
    assert.equal(S.tasks[0].msgParams.code, 'SERVER_ERROR')
    qbankApi.aiCheck = async () => { throw { response: { data: { message: 'Server error detail' } } } }
    S.tasks = []; await runAI([445])
    assert.equal(S.tasks[0].msg, 'Server error detail')
  } finally { Object.assign(qbankApi, savedMethods) }
  console.log('Settings, dialogs, LLM, review, search, import/export and API errors: 6 locales rendered successfully')
} catch (error) {
  console.error(error)
  process.exitCode = 1
} finally {
  await server.close()
}
process.exit(process.exitCode || 0)
