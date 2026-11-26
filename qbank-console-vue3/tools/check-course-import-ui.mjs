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
  const { default: Component } = await server.ssrLoadModule('/src/components/CourseQuestionImport.vue')
  const { COURSES, S } = await server.ssrLoadModule('/src/store.js')
  const { auth } = await server.ssrLoadModule('/src/auth/authStore.js')
  const { qbankApi } = await server.ssrLoadModule('/src/api/qbankApi.js')
  const { t } = await server.ssrLoadModule('/src/i18n/index.js')
  const { descriptor } = parse(readFileSync('src/components/CourseQuestionImport.vue', 'utf8'))
  const script = compileScript(descriptor, { id: 'import-test' })
  const compiled = compileTemplate({ source: descriptor.template.content, filename: 'CourseQuestionImport.vue', id: 'import-test', compilerOptions: { mode: 'function', bindingMetadata: script.bindings } })
  assert.deepEqual(compiled.errors, [])
  Component.render = new Function('Vue', compiled.code)(vue)
  COURSES.splice(0, COURSES.length, { id: 'DB', name: 'Database', subjectId: 7, topics: ['Basics'], topicIds: [42], active: true })
  S.q = []; S.dataMode = 'real'; S.role = 'admin'; auth.isAuthenticated = true; auth.isDemo = false
  const posted = []
  let finishBatch
  qbankApi.importQuestions = payload => {
    posted.push(payload)
    const question = payload.questions[0]
    return new Promise(resolve => { finishBatch = () => resolve({ saved: 1, failedAt: null, uncertain: false, questions: [{ questionId: 99, subjectId: 7, topicId: 42, stem: question.stem, optionA: question.optionA, optionB: question.optionB, optionC: question.optionC, optionD: question.optionD, correctLabel: question.correctLabel, difficulty: payload.difficulty }] }) })
  }
  qbankApi.dashboardSummary = async () => null
  qbankApi.dashboardActivity = async () => []
  app = vue.createApp(Component, { courseIndex: 0 })
  app.provide(vue.ssrContextKey, { modules: new Set() })
  app.config.globalProperties.$t = t
  app.mount('#app')
  await vue.nextTick()
  const input = document.querySelector('input[type=file]')
  const selectFile = async value => {
    Object.defineProperty(input, 'files', { configurable: true, value: [new File([JSON.stringify(value)], 'questions.json')] })
    input.dispatchEvent(new dom.window.Event('change', { bubbles: true }))
    await new Promise(resolve => setTimeout(resolve, 20)); await vue.nextTick()
  }
  const submit = () => document.querySelector('button.pri')
  const row = { stem: 'Question', optionA: 'A', optionB: 'B', optionC: 'C', optionD: 'D', correctLabel: 'B' }
  await selectFile([{ ...row, correctLabel: 'E' }])
  assert.equal(submit().disabled, true, 'Invalid rows block saving')
  assert.equal(posted.length, 0)
  await selectFile([row])
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
  assert.equal(posted[0].topicId, 42)
  assert.equal(posted[0].questions[0].correctLabel, 'B')
  assert.equal(S.q[0].id, 99)
  assert.equal(submit().disabled, true, 'Completed imports cannot be submitted twice')
  console.log('Course import UI: invalid rows blocked, selected course/chapter posted once, saved row displayed')
} finally {
  app?.unmount()
  await server.close()
  dom.window.close()
}
