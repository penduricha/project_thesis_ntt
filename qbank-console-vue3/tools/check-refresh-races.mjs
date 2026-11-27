import assert from 'node:assert/strict'
import { createServer } from 'vite'

globalThis.BroadcastChannel = undefined
const data = new Map()
globalThis.localStorage = {
  getItem: k => data.get(k) ?? null,
  setItem: (k, v) => data.set(k, String(v)),
  removeItem: k => data.delete(k),
}
const server = await createServer({ server: { middlewareMode: true }, appType: 'custom', logLevel: 'error' })
const { AdminService } = await server.ssrLoadModule('/src/auth/AdminService.js')
const { TokenLogin } = await server.ssrLoadModule('/src/auth/TokenLogin.js')
function setup() {
  data.clear()
  data.set('accessToken', 'access-old')
  data.set('refreshToken', 'refresh-old')
  const token = new TokenLogin('access-old', 'refresh-old')
  let resolve, reject
  const request = new Promise((a, b) => { resolve = a; reject = b })
  const service = new AdminService({ getAPIClientWithHeadersToken: () => ({ get: () => request }) }, { removeAdminIdFromLocalStorage() {} })
  return { token, resolve, reject, run: () => service.getDataToRenewTokens(token) }
}
const success = { status: 200, data: { data: { accessToken: 'access-new', refreshToken: 'refresh-new' } } }
{
  const x = setup(); const pending = x.run(); data.clear(); x.resolve(success)
  await assert.rejects(pending, e => e.code === 'AUTH_SESSION_CHANGED')
  assert.equal(data.size, 0)
}
{
  const x = setup(); const pending = x.run(); data.set('refreshToken', 'other-tab'); x.reject({ response: { status: 401 } })
  await assert.rejects(pending, e => e.code === 'AUTH_SESSION_CHANGED')
  assert.equal(data.get('refreshToken'), 'other-tab')
}
{
  const x = setup(); const pending = x.run(); x.resolve({ status: 200, data: {} })
  await assert.rejects(pending, /Invalid refresh response/)
  assert.equal(data.get('refreshToken'), 'refresh-old')
}
{
  const x = setup(); const pending = x.run(); x.reject({ response: { status: 503 } })
  await assert.rejects(pending)
  assert.equal(data.get('refreshToken'), 'refresh-old')
}
{
  const x = setup(); const pending = x.run(); x.reject({ response: { status: 401 } })
  assert.equal(await pending, false)
  assert.equal(data.has('refreshToken'), false)
}
{
  const x = setup(); const pending = x.run(); x.resolve(success)
  assert.equal(await pending, true)
  assert.equal(data.get('refreshToken'), 'refresh-new')
}
const { silentRefresher } = await server.ssrLoadModule('/src/auth/SilentTokenRefresher.js')
Object.defineProperty(globalThis, "navigator", { value: {}, configurable: true })
{
  const x = setup()
  silentRefresher.init({ tokenLogin: x.token, adminService: { getDataToRenewTokens: () => Promise.reject(new Error('offline')) } })
  const a = silentRefresher.refreshNow(null, { throwOnUnavailable: true })
  const b = silentRefresher.refreshNow()
  await assert.rejects(a, /offline/)
  assert.equal(await b, false)
}
await server.close()
console.log('Refresh race, malformed response, 401 and transient failures passed')
