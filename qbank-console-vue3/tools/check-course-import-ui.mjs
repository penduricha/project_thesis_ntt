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
try {
  const { default: Component } = await server.ssrLoadModule('/src/components/QuestionDialog.vue')
  const { COURSES, S } = await server.ssrLoadModule('/src/store.js')
  const { auth } = await server.ssrLoadModule('/src/auth/authStore.js')
  const { qbankApi } = await server.ssrLoadModule('/src/api/qbankApi.js')
  const { t } = await server.ssrLoadModule('/src/i18n/index.js')
  // SSR loading supplies setup; attach client renders to exercise the real dialog,
  // child ref and footer together in jsdom.
  for (const name of ['BaseModal', 'CourseCombobox', 'TopicCombobox', 'CourseQuestionImport', 'QuestionDialog']) {
    const component = (await server.ssrLoadModule(`/src/components/${name}.vue`)).default
    const { descriptor } = parse(readFileSync(`src/components/${name}.vue`, 'utf8'))
    const script = compileScript(descriptor, { id: `test-${name}` })
    const compiled = compileTemplate({ source: descriptor.template.content, filename: `${name}.vue`, id: `test-${name}`, compilerOptions: { mode: 'function', bindingMetadata: script.bindings } })
    assert.deepEqual(compiled.errors, [])
    component.render = new Function('Vue', compiled.code)(vue)
  }
  COURSES.splice(0, COURSES.length, { id: 'DB', name: 'Database', subjectId: 7, topics: ['Basics', 'Intermediate', 'Advanced'], topicIds: [42, 43, 44], active: true })
  S.q = []; S.dataMode = 'real'; S.role = 'admin'; auth.isAuthenticated = true; auth.isDemo = false
  const posted = []
  let finishBatch
  qbankApi.importQuestions = payload => {
    posted.push(payload)
    const question = payload.questions[0]
    return new Promise(resolve => { finishBatch = () => resolve({ saved: 1, failedAt: null, uncertain: false, questions: [{ questionId: 99, subjectId: 7, topicId: payload.topicId, stem: question.stem, optionA: question.optionA, optionB: question.optionB, optionC: question.optionC, optionD: question.optionD, correctLabel: question.correctLabel, difficulty: payload.difficulty }] }) })
  }
  qbankApi.dashboardSummary = async () => null
  qbankApi.dashboardActivity = async () => []
  const isOpen = vue.ref(true)
  app = vue.createApp({ render: () => isOpen.value ? vue.h(Component, { id: 0, ctx: { c: 0, tp: 0, courseImport: true }, onClose: () => { isOpen.value = false } }) : null })
  app.provide(vue.ssrContextKey, { modules: new Set() })
  app.config.globalProperties.$t = t
  app.mount('#app')
  await vue.nextTick()
  assert.equal(document.querySelector('.api-prev'), null, 'Manual form must not show payload preview')
  assert.equal(document.querySelectorAll('.modal-footer button').length, 2, 'Manual form keeps its actions')
  const modeButton = [...document.querySelectorAll('.import-mode button')].find(button => button.textContent.trim() === t('courseImport.mode'))
  assert.ok(modeButton)
  modeButton.click()
  await vue.nextTick()
  assert.equal(document.querySelectorAll('.modal-footer button').length, 1, 'Import footer has one action')
  assert.equal(document.querySelectorAll('.course-import button.pri').length, 0, 'Import action is not in the scrollable body')
  assert.ok(document.querySelector('.modal-header .dlg-x'), 'Header close remains available')
  const chapterLabel = document.querySelector('.course-import label')
  assert.ok(!chapterLabel.textContent.includes('*'), 'Chapter has no asterisk')
  const chapterInput = document.querySelector('#course-import-topic-input')
  chapterInput.focus(); await vue.nextTick()
  const chapterThree = [...document.querySelectorAll('#course-import-topic-list [role=option]')].find(el => el.textContent === 'Advanced')
  assert.ok(chapterThree)
  chapterThree.dispatchEvent(new dom.window.MouseEvent('mousedown', { bubbles: true, cancelable: true }))
  chapterThree.click(); await vue.nextTick()
  assert.equal(chapterInput.value, 'Advanced')
  const selectFile = async value => {
    const input = document.querySelector('input[type=file]')
    Object.defineProperty(input, 'files', { configurable: true, value: [new File([JSON.stringify(value)], 'questions.json')] })
    input.dispatchEvent(new dom.window.Event('change', { bubbles: true }))
    await new Promise(resolve => setTimeout(resolve, 20)); await vue.nextTick()
  }
  const submit = () => document.querySelector('.modal-footer button.pri')
  const row = { stem: 'Question', optionA: 'A', optionB: 'B', optionC: 'C', optionD: 'D', correctLabel: 'B' }
  await selectFile([{ ...row, correctLabel: 'E' }])
  assert.equal(submit().disabled, true, 'Invalid rows block saving')
  assert.equal(posted.length, 0)
  await selectFile([row])
  assert.equal(submit().disabled, false)
  COURSES[0].topicIds[2] = 999
  await vue.nextTick()
  assert.equal(submit().disabled, true, 'Changed chapter mapping blocks import')
  assert.ok(document.querySelector('.course-import [role=alert]'), 'Changed catalog is explained')
  COURSES[0].topicIds[2] = 44
  await vue.nextTick()
  assert.equal(submit().disabled, false)
  submit().click()
  await vue.nextTick()
  assert.equal(submit().disabled, true, 'A pending import cannot be submitted twice')
  submit().click()
  assert.equal(posted.length, 1)
  finishBatch()
  await new Promise(resolve => setTimeout(resolve, 20)); await vue.nextTick()
  assert.equal(posted.length, 1)
  assert.equal(posted[0].subjectId, 7)
  assert.equal(posted[0].topicId, 44, 'Import sends the selected API topic ID')
  assert.equal(posted[0].questions[0].correctLabel, 'B')
  assert.equal(S.q[0].id, 99)
  assert.equal(isOpen.value, false, 'A fully confirmed import closes the dialog')
  assert.equal(document.querySelector('.modal'), null)
  assert.equal(S.toast.on, true)
  assert.equal(S.toast.tone, 'ok')
  assert.equal(S.toast.msg, t('courseImport.saved', { n: 1, total: 1 }), 'Success toast reports saved count')

  const failureCases = [
    { name: 'partial', value: { saved: 1, failedAt: 2, uncertain: false, status: 409, questions: [{ questionId: 100, subjectId: 7, topicId: 42, ...row }] }, rows: [row, row] },
    { name: 'uncertain response', value: { saved: 0, failedAt: 1, uncertain: true, questions: [] }, rows: [row] },
    { name: 'network timeout', error: new Error('timeout'), rows: [row] },
    { name: 'rejected batch', error: { response: { status: 400 } }, rows: [row] },
    { name: 'malformed response', value: { saved: 2, questions: [] }, rows: [row] },
    { name: 'inconsistent success response', value: { saved: 1, failedAt: null, uncertain: true, questions: [{ questionId: 101, subjectId: 7, topicId: 42, ...row }] }, rows: [row] },
  ]
  for (const test of failureCases) {
    S.toast.on = false
    qbankApi.importQuestions = async () => { if (test.error) throw test.error; return test.value }
    isOpen.value = true
    await vue.nextTick()
    ;[...document.querySelectorAll('.import-mode button')].find(button => button.textContent.trim() === t('courseImport.mode')).click()
    await vue.nextTick()
    await selectFile(test.rows)
    submit().click()
    await new Promise(resolve => setTimeout(resolve, 20)); await vue.nextTick()
    assert.equal(isOpen.value, true, `${test.name} keeps the dialog open`)
    assert.equal(S.toast.on, false, `${test.name} does not show a success toast`)
    assert.ok(document.querySelector('.import-result .err'), `${test.name} shows an error`)
    assert.equal(submit().disabled, true, `${test.name} cannot be retried accidentally`)
    isOpen.value = false
    await vue.nextTick()
  }
  console.log('Course dialog UI: success closes and shows toast; failures remain visible without retry')
} finally {
  app?.unmount()
  await server.close()
  dom.window.close()
}
