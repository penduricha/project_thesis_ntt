import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { JSDOM } from 'jsdom'

const dom = new JSDOM('<!doctype html><div id="app"></div>', { url: 'http://localhost:5173/' })
for (const key of ['window', 'document', 'navigator', 'Element', 'HTMLElement', 'HTMLInputElement', 'SVGElement', 'Document', 'ShadowRoot', 'Node', 'localStorage', 'sessionStorage', 'DOMParser']) {
  Object.defineProperty(globalThis, key, { configurable: true, value: dom.window[key] })
}
globalThis.BroadcastChannel = undefined
globalThis.confirm = () => true
const { createServer } = await import('vite')
const vue = await import('vue')
const { parse, compileScript, compileTemplate } = await import('@vue/compiler-sfc')
const server = await createServer({ server: { middlewareMode: true }, appType: 'custom', logLevel: 'error' })
let app
const tick = async () => { await new Promise(resolve => setTimeout(resolve, 10)); await vue.nextTick() }
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }
try {
  const store = await server.ssrLoadModule('/src/store.js')
  const { S, COURSES, askDeleteCourse } = store
  const { auth } = await server.ssrLoadModule('/src/auth/authStore.js')
  const { qbankApi } = await server.ssrLoadModule('/src/api/qbankApi.js')
  const { t } = await server.ssrLoadModule('/src/i18n/index.js')
  const { validateSubject } = await server.ssrLoadModule('/src/services/subjectValidation.js')
  const validateCode = id => validateSubject({ id, name: 'Môn học', t1: 'Chương 1', dept: '' }).id
  for (const code of ['INT3202', 'BAH21399', 'ABC123456']) assert.equal(validateCode(code), undefined, code)
  for (const code of ['haG8342', '325as', 'fds', 'INT320', 'INT3202000', 'INT-3202', 'INT_3202', ' INT3202', 'INT3202 ', 'INT3202\n', 'ÁBC1234', 'ABC１２３４']) assert.equal(validateCode(code), 'subject.codeInvalid', code)
  assert.equal(validateCode(''), 'validation.courseIdEmpty')
  assert.equal(validateSubject({ id: 'NET0101', name: 'Mạng máy tính (C++)', t1: 'Chương 1: Tổng quan', dept: '' }).name, undefined)
  assert.equal(validateSubject({ id: 'NET0101', name: 'Cơ sở <script>', t1: 'Chương 1', dept: '' }).name, 'subject.nameInvalid')
  assert.equal(validateSubject({ id: 'NET0101', name: 'Cơ sở', t1: 'Chương 1', dept: 'test\u0001' }).dept, 'subject.descriptionInvalid')

  for (const name of ['BaseModal', 'CourseDialog']) {
    const component = (await server.ssrLoadModule(`/src/components/${name}.vue`)).default
    const { descriptor } = parse(readFileSync(`src/components/${name}.vue`, 'utf8'))
    const script = compileScript(descriptor, { id: `test-${name}` })
    const compiled = compileTemplate({ source: descriptor.template.content, filename: `${name}.vue`, id: `test-${name}`, compilerOptions: { mode: 'function', bindingMetadata: script.bindings } })
    assert.deepEqual(compiled.errors, [])
    component.render = new Function('Vue', compiled.code)(vue)
  }
  const Component = (await server.ssrLoadModule('/src/components/CourseDialog.vue')).default
  auth.isAuthenticated = true; auth.isDemo = false; auth.adminId = 7
  S.role = 'admin'; S.dataMode = 'real'; S.status = 'ready'
  const original = { subjectId: 7, subjectCode: 'DBC0101', subjectName: 'Cơ sở dữ liệu', description: 'DB', active: true, updatedAt: '2026-01-01T00:00:00.123456Z' }
  let db = [original]
  COURSES.splice(0, COURSES.length, { id: 'DBC0101', name: original.subjectName, dept: 'DB', subjectId: 7, updatedAt: original.updatedAt, topics: ['Chương 1'], topicIds: [42], active: true })
  S.q = [{ id: 100, c: 0, tp: 0, text: 'Question', o: ['A', 'B', 'C', 'D'], a: 0, st: 'draft' }]
  qbankApi.subjects = async () => db
  qbankApi.topics = async subjectId => [{ topicId: subjectId === 7 ? 42 : 43, topicName: 'Chương 1' }]
  qbankApi.listQuestions = async () => ({ content: [{ questionId: 100, subjectId: 7, topicId: 42, stem: 'Question', options: ['A', 'B', 'C', 'D'], correctLabel: 'A', version: 1, difficulty: 2, validationStatus: 'not_checked' }], totalPages: 1 })
  qbankApi.dashboardSummary = async () => null; qbankApi.dashboardActivity = async () => []

  const isOpen = vue.ref(true), index = vue.ref(-1)
  app = vue.createApp({ render: () => isOpen.value ? vue.h(Component, { index: index.value, onClose: () => { isOpen.value = false } }) : null })
  app.provide(vue.ssrContextKey, { modules: new Set() }); app.config.globalProperties.$t = t; app.mount('#app'); await tick()
  const input = async (selector, value) => { const el = document.querySelector(selector); el.value = value; el.dispatchEvent(new dom.window.Event('input', { bubbles: true })); await vue.nextTick() }
  const save = () => document.querySelector('.modal-footer .pri')
  let posted = 0
  qbankApi.createSubject = async () => { posted++; throw new Error('unexpected') }
  await input('#f-cid', 'MẠNG')
  assert.equal(document.querySelector('#subject-code-error').textContent, t('subject.codeInvalid'))
  assert.equal(save().disabled, true); assert.equal(posted, 0)
  await input('#f-cid', 'haG8342')
  assert.equal(document.querySelector('#subject-code-error').textContent, t('subject.codeInvalid'))
  await input('#f-cid', 'DBC0101')
  assert.equal(document.querySelector('#subject-code-error').textContent, t('validation.courseIdTaken'))
  // A composing input must validate the visible value, not the previous v-model value.
  const codeEl = document.querySelector('#f-cid')
  codeEl.dispatchEvent(new dom.window.CompositionEvent('compositionstart', { bubbles: true }))
  await input('#f-cid', 'fds')
  assert.equal(document.querySelector('#subject-code-error').textContent, t('subject.codeInvalid'))
  assert.equal(codeEl.getAttribute('aria-invalid'), 'true')
  codeEl.dispatchEvent(new dom.window.CompositionEvent('compositionend', { bubbles: true }))
  await input('#f-cid', 'NET0101')
  await input('#f-cn', 'Tên <script>')
  assert.equal(document.querySelector('#subject-name-error').textContent, t('subject.nameInvalid'))
  await input('#f-cn', 'Mạng máy tính (C++)')
  await input('#f-ct1', 'Chương @')
  assert.equal(document.querySelector('#subject-topic-error').textContent, t('subject.topicInvalid'))
  await input('#f-ct1', 'Chương 1: Tổng quan')
  assert.equal(save().disabled, false); assert.equal(posted, 0, 'validation did not call API')

  const response = deferred(); let createdBody
  qbankApi.createSubject = body => { posted++; createdBody = body; return response.promise }
  save().click(); await vue.nextTick(); assert.equal(save().disabled, true)
  save().click(); assert.equal(posted, 1, 'no duplicate POST')
  const added = { subjectId: 8, subjectCode: 'NET0101', subjectName: 'Mạng máy tính (C++)', description: null, active: true, updatedAt: '2026-01-01T00:00:01.123456Z' }
  db = [original, added]; response.resolve(added); await tick(); await tick()
  assert.equal(isOpen.value, false); assert.equal(createdBody.firstTopic, 'Chương 1: Tổng quan')
  assert.equal(createdBody.subjectCode, 'NET0101'); assert.equal(COURSES[1].topicIds[0], 43)

  // Edit keeps its opening version and remains open on a server conflict.
  index.value = 1; isOpen.value = true; await tick()
  let updateBody
  qbankApi.updateSubject = async (_id, body) => { updateBody = body; throw { response: { status: 409, data: { code: 'SUBJECT_VERSION_CONFLICT' } } } }
  await input('#f-cn', 'Mạng máy tính mới')
  save().click(); await tick()
  assert.equal(isOpen.value, true); assert.equal(document.querySelector('#f-e').textContent, t('subject.versionConflict'))
  assert.equal(updateBody.expectedUpdatedAt, added.updatedAt)
  // A successful edit uses the same subject id and updates the list only after confirmation.
  qbankApi.updateSubject = async (id, body) => { assert.equal(id, 8); const edited = { ...added, subjectName: body.subjectName, updatedAt: '2026-01-01T00:00:02.123456Z' }; db = [edited, original]; return edited }
  await input('#f-cn', 'Mạng máy tính mới'); save().click(); await tick(); await tick()
  assert.equal(isOpen.value, false); assert.equal(COURSES[1].name, 'Mạng máy tính mới')

  // Archive sends the optimistic timestamp, keeps the question and renders inactive.
  let archived
  qbankApi.archiveSubject = async (id, expected) => { archived = { id, expected }; db = [db[0], { ...original, active: false, updatedAt: '2026-01-01T00:00:03.123456Z' }]; return db[1] }
  askDeleteCourse(0); assert.equal(S.dlg.kind, 'confirm'); await S.dlg.props.action()
  assert.deepEqual(archived, { id: 7, expected: original.updatedAt })
  assert.equal(COURSES.find(c => c.subjectId === 7).active, false); assert.equal(S.q[0].id, 100, 'archive never removes questions')

  // An archived subject can be reactivated without recreating it or deleting questions.
  index.value = 0; isOpen.value = true; await tick()
  const active = document.querySelector('#f-cact')
  assert.equal(active.checked, false)
  active.checked = true; active.dispatchEvent(new dom.window.Event('change', { bubbles: true })); await vue.nextTick()
  qbankApi.updateSubject = async (id, body) => {
    assert.equal(id, 7); assert.equal(body.active, true); assert.equal(body.expectedUpdatedAt, '2026-01-01T00:00:03.123456Z')
    db = [db[0], { ...db[1], active: true, updatedAt: '2026-01-01T00:00:04.123456Z' }]
    return db[1]
  }
  save().click(); await tick(); await tick()
  assert.equal(isOpen.value, false); assert.equal(COURSES[0].active, true); assert.equal(S.q[0].id, 100)

  // A lost archive response cannot be repeatedly submitted before a reload.
  let attempts = 0
  qbankApi.archiveSubject = async () => { attempts++; throw new Error('timeout') }
  askDeleteCourse(1); await S.dlg.props.action()
  askDeleteCourse(1); await S.dlg.props.action()
  assert.equal(attempts, 1)
  assert.equal(S.toast.msg, t('subject.saveUncertain'))
  await store.loadQuestions()

  // Unknown create response is not retried or reported as a success.
  index.value = -1; isOpen.value = true; await tick()
  await input('#f-cid', 'XXX0123'); await input('#f-cn', 'Môn thử')
  qbankApi.createSubject = async () => { throw new Error('timeout') }
  save().click(); await tick()
  assert.equal(isOpen.value, true); assert.equal(document.querySelector('#f-e').textContent, t('subject.saveUncertain')); assert.equal(save().disabled, true)
  // Legacy subjects remain readable, but their invalid codes must be corrected when edited.
  isOpen.value = false; await tick()
  COURSES.push({ id: 'legacy1', name: 'Môn cũ', dept: '', subjectId: 9, updatedAt: original.updatedAt, topics: [], topicIds: [], active: true })
  index.value = COURSES.length - 1; isOpen.value = true; await tick()
  assert.equal(document.querySelector('#subject-code-error').textContent, t('subject.codeInvalid'))
  assert.equal(save().disabled, true)
  await input('#f-cid', 'LEG1234')
  assert.equal(document.querySelector('#subject-code-error'), null)
  assert.equal(save().disabled, false)
  console.log('Subject CRUD: live regex, duplicates, payload, version conflict, archive and unknown response passed')
} finally { app?.unmount(); await server.close(); dom.window.close() }
