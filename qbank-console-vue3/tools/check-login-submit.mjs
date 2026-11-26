import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { JSDOM } from 'jsdom'

const dom = new JSDOM('<!doctype html><div id="app"></div>', { url: 'http://localhost:5173/' })
for (const key of ['window', 'document', 'navigator', 'Element', 'HTMLElement', 'HTMLInputElement', 'SVGElement', 'Document', 'ShadowRoot', 'Node', 'localStorage', 'sessionStorage']) {
  Object.defineProperty(globalThis, key, { configurable: true, value: dom.window[key] })
}
globalThis.BroadcastChannel = undefined
globalThis.requestAnimationFrame = callback => setTimeout(callback, 0)

const { createServer } = await import('vite')
const vue = await import('vue')
const { parse, compileScript, compileTemplate } = await import('@vue/compiler-sfc')
const { routerKey } = await import('vue-router')
const axios = (await import('axios')).default
const server = await createServer({ server: { middlewareMode: true }, appType: 'custom', logLevel: 'error' })
let app
try {
  const { DEMO_MODE } = await server.ssrLoadModule('/src/config.js')
  assert.equal(DEMO_MODE, false, 'Test requires real-mode configuration (no demo mode)')
  const { default: LoginView } = await server.ssrLoadModule('/src/views/LoginView.vue')
  const source = readFileSync('src/views/LoginView.vue', 'utf8')
  const { descriptor } = parse(source)
  const script = compileScript(descriptor, { id: 'login-test' })
  const template = compileTemplate({ source: descriptor.template.content, filename: 'LoginView.vue', id: 'login-test', compilerOptions: { mode: 'function', bindingMetadata: script.bindings } })
  assert.deepEqual(template.errors, [])
  LoginView.render = new Function('Vue', template.code)(vue)
  let calls = []
  let resolveRequest
  axios.defaults.adapter = config => {
    calls.push({ url: config.url, data: JSON.parse(config.data) })
    return new Promise(resolve => { resolveRequest = () => resolve({ data: { success: false, message: 'invalid' }, status: 200, statusText: 'OK', headers: {}, config }) })
  }
  app = vue.createApp(LoginView)
  app.config.globalProperties.$t = key => key
  app.provide(routerKey, { push: async () => {} })
  app.provide(vue.ssrContextKey, { modules: new Set() })
  app.mount('#app')
  await vue.nextTick()
  const form = document.querySelector('form')
  const id = document.querySelector('#login-admin-id')
  const password = document.querySelector('#login-password')
  const otp = [...document.querySelectorAll('.otp__box')]
  const setValue = (el, value) => { el.value = value; el.dispatchEvent(new dom.window.Event('input', { bubbles: true })) }
  const enter = (el, options = {}) => el.dispatchEvent(new dom.window.KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true, ...options }))
  enter(id)
  await vue.nextTick()
  assert.ok(document.querySelector('#errorAdminId'), 'Enter uses the same validation as the button')
  setValue(id, '123')
  setValue(password, 'password')
  await vue.nextTick()
  enter(password, { isComposing: true })
  enter(password, { repeat: true })
  await vue.nextTick()
  assert.equal(calls.length, 0, 'IME composition and key repeat do not submit')
  assert.equal(enter(document.querySelector('#rememberMe')), true, 'Checkbox keeps native keyboard behavior')
  assert.equal(enter(document.querySelector('.control__btn')), true, 'Other buttons keep native keyboard behavior')
  enter(password)
  await vue.nextTick()
  await new Promise(resolve => setTimeout(resolve, 0))
  assert.equal(calls.length, 1)
  assert.equal(calls[0].url, '/admin/auth/login')
  enter(password)
  form.requestSubmit()
  await vue.nextTick()
  assert.equal(calls.length, 1, 'Pending login cannot be submitted twice')
  resolveRequest()
  await new Promise(resolve => setTimeout(resolve, 0))
  await vue.nextTick()
  setValue(password, 'password')
  otp.forEach((input, i) => setValue(input, String(i + 1)))
  await vue.nextTick()
  enter(otp[5])
  await new Promise(resolve => setTimeout(resolve, 0))
  assert.equal(calls.length, 2, 'Enter from OTP submits the same login flow')
  assert.equal(calls[1].url, '/admin/auth/login/totp')
  assert.equal(calls[1].data.totpCode, '123456')
  resolveRequest()
  await new Promise(resolve => setTimeout(resolve, 0))
  await vue.nextTick()
  setValue(password, 'password')
  otp.forEach((input, i) => setValue(input, String(i + 1)))
  await vue.nextTick()
  document.querySelector('.lsubmit').click()
  await new Promise(resolve => setTimeout(resolve, 0))
  assert.equal(calls.length, 3, 'Click uses the same login flow')
  assert.equal(calls[2].url, calls[1].url)
  assert.deepEqual(calls[2].data, calls[1].data)
  resolveRequest()
  console.log('Login Enter, click, OTP, validation and duplicate-submit checks passed')
} finally {
  app?.unmount()
  await server.close()
  dom.window.close()
}
