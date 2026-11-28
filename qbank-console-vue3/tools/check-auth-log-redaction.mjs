import assert from 'node:assert/strict'
import { createServer } from 'vite'

globalThis.BroadcastChannel = undefined
const map = new Map([['accessToken', 'fake-access'], ['refreshToken', 'fake-refresh']])
globalThis.localStorage = {getItem: k => map.get(k) ?? null, setItem: (k, v) => map.set(k, String(v)), removeItem: k => map.delete(k)}
Object.defineProperty(globalThis, 'navigator', {value: {}, configurable: true})
const server = await createServer({server: {middlewareMode: true}, appType: 'custom', logLevel: 'error'})
const saved = {error: console.error, warn: console.warn, log: console.log}
const messages = []
try {
  const { AdminAccount } = await server.ssrLoadModule('/src/auth/AdminAccount.js')
  const { TokenLogin } = await server.ssrLoadModule('/src/auth/TokenLogin.js')
  const { AdminService } = await server.ssrLoadModule('/src/auth/AdminService.js')
  const { EnrollmentService } = await server.ssrLoadModule('/src/auth/EnrollmentService.js')
  const { default: AdminRepository, REQUEST_STATUS } = await server.ssrLoadModule('/src/auth/AdminRepository.js')
  const { silentRefresher } = await server.ssrLoadModule('/src/auth/SilentTokenRefresher.js')
  for (const level of Object.keys(saved)) console[level] = (...args) => messages.push(args)
  const secret = 'TEST-SECRET-NEVER-LOG'
  const error = Object.assign(new Error(secret), {config: {headers: {Authorization: secret}, data: secret}, response: {status: 503, data: {secret}}})
  const reject = async () => { throw error }
  const client = {get: reject, post: reject, delete: reject}
  const config = {getAuthorizedAPIClient: () => client, getAPIClientWithHeadersToken: () => client}
  const token = new TokenLogin('fake-access', 'fake-refresh')
  const enrollment = new EnrollmentService(config)
  assert.equal((await enrollment.postDataToStartEnrollmentRequest(token, 30)).status, 503)
  assert.equal((await enrollment.deleteDataToCancelEnrollmentRequest(token, 'generation')).status, 503)
  assert.equal((await enrollment.postDataToActivateEnrollmentRequest(token, 30, 'generation')).status, 503)
  assert.equal((await enrollment.postReconcileEnrollmentRequest(token, 'generation')).status, 503)
  assert.equal((await enrollment.getDataTwoFaStatusRequest(token)).status, 503)
  assert.equal((await enrollment.deleteDataToCancelPendingRequest(token, 42)).status, 503)
  const admin = new AdminService(config, {})
  assert.equal(await admin.getDataToAutoLoginAccessToken(token), false)
  const networkError = Object.assign(new Error(secret), {config: error.config})
  const repository = new AdminRepository({postDataToLoginRequest: async () => { throw networkError }}, {}, {})
  assert.equal((await repository.getLoginResult(42, secret)).getStatus(), REQUEST_STATUS.EXCEPTION)
  silentRefresher.init({tokenLogin: token, adminService: {getDataToRenewTokens: reject}})
  await assert.rejects(silentRefresher.refreshNow(null, {throwOnUnavailable: true}))
  assert.equal(String(new AdminAccount(42, secret)), 'AdminAccount[redacted]')
  assert.ok(messages.length > 0, 'the test exercises failure logging')
  assert.ok(!JSON.stringify(messages).includes(secret), 'credentials or request error were logged')
} finally {
  Object.assign(console, saved)
  await server.close()
}
console.log('Authentication failure logs do not include the test credential or request object')
