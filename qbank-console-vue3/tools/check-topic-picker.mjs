import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { JSDOM } from 'jsdom'

const dom = new JSDOM('<!doctype html><div id="app"></div>', { url: 'http://localhost:5173/' })
for (const key of ['window', 'document', 'navigator', 'Element', 'HTMLElement', 'HTMLInputElement', 'SVGElement', 'Document', 'ShadowRoot', 'Node', 'localStorage', 'sessionStorage']) {
  Object.defineProperty(globalThis, key, { configurable: true, value: dom.window[key] })
}
dom.window.HTMLElement.prototype.scrollIntoView = function () {}
globalThis.BroadcastChannel = undefined
globalThis.confirm = () => true
const { createServer } = await import('vite')
const vue = await import('vue')
const { parse, compileScript, compileTemplate } = await import('@vue/compiler-sfc')
const server = await createServer({ server: { middlewareMode: true }, appType: 'custom', logLevel: 'error' })
let app
try {
  const { default: QuestionDialog } = await server.ssrLoadModule('/src/components/QuestionDialog.vue')
  const { COURSES, S, loadQuestions } = await server.ssrLoadModule('/src/store.js')
  const { auth } = await server.ssrLoadModule('/src/auth/authStore.js')
  const { qbankApi } = await server.ssrLoadModule('/src/api/qbankApi.js')
  const { t } = await server.ssrLoadModule('/src/i18n/index.js')
  for (const name of ['BaseModal', 'CourseCombobox', 'TopicCombobox', 'CourseQuestionImport', 'QuestionDialog']) {
    const component = (await server.ssrLoadModule(`/src/components/${name}.vue`)).default
    const { descriptor } = parse(readFileSync(`src/components/${name}.vue`, 'utf8'))
    const script = compileScript(descriptor, { id: `test-${name}` })
    const compiled = compileTemplate({ source: descriptor.template.content, filename: `${name}.vue`, id: `test-${name}`, compilerOptions: { mode: 'function', bindingMetadata: script.bindings } })
    assert.deepEqual(compiled.errors, [])
    component.render = new Function('Vue', compiled.code)(vue)
  }
  const chapters = Array.from({ length: 30 }, (_, i) => ({ topicId: 100 + i, topicName: `Chương ${i + 1}: ${i === 2 ? 'Spring Boot và REST API' : 'Nội dung'}` }))
  const requests = []
  qbankApi.subjects = async () => [{ subjectId: 7, subjectCode: 'ABC1234', subjectName: 'Lập trình Java', active: true }, { subjectId: 9, subjectCode: 'DEF1234', subjectName: 'Cơ sở dữ liệu', active: true }]
  qbankApi.topics = async id => { requests.push(id); return id === 7 ? chapters : [{ topicId: 700, topicName: 'Chương khác' }] }
  qbankApi.listQuestions = async () => ({ content: [], totalPages: 1 })
  qbankApi.dashboardSummary = async () => null
  qbankApi.dashboardActivity = async () => []
  qbankApi.listSuggestions = async () => ({ content: [] })
  auth.isAuthenticated = true; auth.isDemo = false; S.role = 'admin'
  assert.equal(await loadQuestions(), true)
  assert.deepEqual(requests.sort(), [7, 9], 'Both subjects load their topic lists from the API')
  assert.equal(COURSES[0].topics.length, 30)
  let payload
  qbankApi.createQuestion = async body => { payload = body; return { questionId: 88, ...body, version: 1, validationStatus: 'not_checked' } }
  const isOpen = vue.ref(true)
  app = vue.createApp({ render: () => isOpen.value ? vue.h(QuestionDialog, { id: 0, ctx: { c: 0 }, onClose: () => { isOpen.value = false } }) : null })
  app.provide(vue.ssrContextKey, { modules: new Set() })
  app.config.globalProperties.$t = t
  app.mount('#app'); await vue.nextTick()
  const input = () => document.querySelector('#question-topic-input')
  const items = () => [...document.querySelectorAll('#question-topic-list [role=option]')]
  const type = (el, value) => { el.value = value; el.dispatchEvent(new dom.window.Event('input', { bubbles: true })) }
  input().focus(); await vue.nextTick()
  assert.equal(items().length, 30, 'Long chapter lists are all available')
  assert.equal(input().getAttribute('aria-expanded'), 'true')
  const target = items().find(el => el.textContent.includes('Chương 3:'))
  target.dispatchEvent(new dom.window.MouseEvent('mousedown', { bubbles: true, cancelable: true }))
  target.click(); await vue.nextTick()
  assert.equal(input().value, chapters[2].topicName, 'Selecting the third chapter sticks')
  assert.equal(input().getAttribute('aria-expanded'), 'false', 'Selecting closes the list')
  const courseInput = document.querySelector('#cf-c')
  courseInput.focus(); courseInput.blur(); await vue.nextTick()
  assert.equal(input().value, chapters[2].topicName, 'Leaving the unchanged course does not reset the chapter')
  courseInput.focus(); courseInput.click(); await vue.nextTick()
  const otherCourse = [...document.querySelectorAll('#cf-lb [role=option]')].find(el => el.textContent.includes('Cơ sở dữ liệu'))
  assert.ok(otherCourse)
  otherCourse.dispatchEvent(new dom.window.MouseEvent('mousedown', { bubbles: true, cancelable: true })); otherCourse.click(); await vue.nextTick()
  assert.equal(courseInput.value, 'Cơ sở dữ liệu', 'Clicking a course without typing selects it')
  assert.equal(courseInput.getAttribute('aria-expanded'), 'false', 'Selecting a course closes the list')
  assert.equal(input().value, 'Chương khác', 'Changing subject selects its own first chapter')
  input().focus(); await vue.nextTick()
  assert.equal(items().length, 1, 'The chapter list no longer contains the previous course chapters')
  input().blur(); await vue.nextTick()
  courseInput.focus(); type(courseInput, 'java'); await vue.nextTick()
  const originalCourse = [...document.querySelectorAll('#cf-lb [role=option]')].find(el => el.textContent.includes('Lập trình Java'))
  originalCourse.dispatchEvent(new dom.window.MouseEvent('mousedown', { bubbles: true, cancelable: true })); originalCourse.click(); await vue.nextTick()
  assert.equal(input().value, chapters[0].topicName, 'Returning to the course uses its own chapters')
  input().focus(); type(input(), 'chuong 17'); await vue.nextTick()
  assert.equal(items().length, 1, 'Search ignores Vietnamese accents')
  input().dispatchEvent(new dom.window.KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }))
  await vue.nextTick()
  assert.equal(input().value, chapters[16].topicName, 'Keyboard selection chooses the filtered result')
  input().focus(); type(input(), 'not present'); await vue.nextTick()
  assert.equal(items().length, 0)
  assert.ok(document.querySelector('#question-topic-list .none'))
  input().dispatchEvent(new dom.window.KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }))
  await vue.nextTick()
  assert.equal(input().value, chapters[16].topicName, 'Escape restores selection')
  assert.equal(isOpen.value, true, 'Escape closes the list, not the dialog')
  input().focus(); type(input(), 'chuong 3'); await vue.nextTick()
  const third = items().find(el => el.textContent.includes('Chương 3:'))
  third.dispatchEvent(new dom.window.MouseEvent('mousedown', { bubbles: true, cancelable: true })); third.click()
  await vue.nextTick()
  type(document.querySelector('#f-t'), 'Câu hỏi thử nghiệm')
  for (let i = 0; i < 4; i++) type(document.querySelector(`#f-o${i}`), `Phương án ${i}`)
  document.querySelector('.modal-footer .btn.pri').click()
  await new Promise(resolve => setTimeout(resolve, 20)); await vue.nextTick()
  assert.equal(payload?.subjectId, 7)
  assert.equal(payload?.topicId, 102, 'Save sends the actual third topic ID, not an index or a fabricated ID')
  assert.equal(isOpen.value, false)

  // A live catalog reorder must not silently redirect an already-open editor to another topic ID.
  payload = undefined; isOpen.value = true; await vue.nextTick()
  input().focus(); await vue.nextTick()
  const existingThird = items().find(el => el.textContent.includes('Chương 3:'))
  existingThird.dispatchEvent(new dom.window.MouseEvent('mousedown', { bubbles: true, cancelable: true })); existingThird.click(); await vue.nextTick()
  COURSES[0].topicIds[2] = 9999
  type(document.querySelector('#f-t'), 'Another question')
  for (let i = 0; i < 4; i++) type(document.querySelector(`#f-o${i}`), `Option ${i}`)
  document.querySelector('.modal-footer .btn.pri').click(); await vue.nextTick()
  assert.equal(payload, undefined, 'A catalog change blocks a write to the wrong chapter')
  assert.ok(document.querySelector('.fe[role=alert]'))
  await new Promise(resolve => setTimeout(resolve, 0))
  app.unmount()

  // Creating inside a course uses that course without showing another course selector.
  payload = undefined
  const fixedOpen = vue.ref(true)
  app = vue.createApp({ render: () => fixedOpen.value ? vue.h(QuestionDialog, { id: 0, ctx: { c: 1, tp: 0, courseScoped: true, courseImport: true }, onClose: () => { fixedOpen.value = false } }) : null })
  app.provide(vue.ssrContextKey, { modules: new Set() }); app.config.globalProperties.$t = t; app.mount('#app'); await vue.nextTick()
  assert.equal(document.querySelector('#cf-c'), null, 'Course selector is hidden only for course-scoped creation')
  assert.equal(document.querySelector('#question-topic-input').value, 'Chương khác')
  type(document.querySelector('#f-t'), 'Question in chosen course')
  for (let i = 0; i < 4; i++) type(document.querySelector(`#f-o${i}`), `Fixed option ${i}`)
  document.querySelector('.modal-footer .btn.pri').click()
  await new Promise(resolve => setTimeout(resolve, 20)); await vue.nextTick()
  assert.equal(payload?.subjectId, 9, 'Course-scoped dialog saves to the current course')
  assert.equal(payload?.topicId, 700, 'Course-scoped dialog saves that course’s topic')
  assert.equal(fixedOpen.value, false)
  app.unmount()
  const TopicCombobox = (await server.ssrLoadModule('/src/components/TopicCombobox.vue')).default
  const chosen = vue.ref('')
  const course = vue.ref({ subjectId: 7, topics: chapters.map(x => x.topicName), topicIds: chapters.map(x => x.topicId) })
  app = vue.createApp({ render: () => vue.h(TopicCombobox, { uid: 'filter-test', course: course.value, all: true, modelValue: chosen.value, 'onUpdate:modelValue': v => { chosen.value = v } }) })
  app.provide(vue.ssrContextKey, { modules: new Set() }); app.config.globalProperties.$t = t; app.mount('#app'); await vue.nextTick()
  const filterInput = document.querySelector('#filter-test-input')
  filterInput.focus(); await vue.nextTick()
  assert.equal(document.querySelectorAll('#filter-test-list [role=option]').length, 31, 'Filter includes all chapters and a reset option')
  const filterThird = [...document.querySelectorAll('#filter-test-list [role=option]')].find(el => el.textContent.includes('Chương 3:'))
  filterThird.dispatchEvent(new dom.window.MouseEvent('mousedown', { bubbles: true, cancelable: true })); filterThird.click(); await vue.nextTick()
  assert.equal(chosen.value, 2)
  filterInput.focus(); filterInput.click(); await vue.nextTick()
  const all = document.querySelector('#filter-test-option-0')
  all.dispatchEvent(new dom.window.MouseEvent('mousedown', { bubbles: true, cancelable: true })); all.click(); await vue.nextTick()
  assert.equal(chosen.value, '', 'All chapters clears the filter')
  console.log('API-backed topic list: chapter 3, search, keyboard, course changes, stale catalog, filter and save payload passed')
} finally {
  app?.unmount()
  await server.close()
  dom.window.close()
}
