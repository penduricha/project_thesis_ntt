import assert from 'node:assert/strict'
import { createServer } from 'vite'
globalThis.BroadcastChannel = undefined
const server = await createServer({ server: { middlewareMode: true }, appType: 'custom', logLevel: 'error' })
const { default: AdminRepository, REQUEST_STATUS } = await server.ssrLoadModule('/src/auth/AdminRepository.js')

function repositoryWithResponse(responseOrError, isError = false) {
  return new AdminRepository({
    postDataToLoginRequest: async () => {
      if (isError) throw responseOrError
      return { data: responseOrError }
    },
  }, {}, {})
}
const status = async (response, isError = false) =>
  (await repositoryWithResponse(response, isError).getLoginResult(42, 'password')).getStatus()
assert.equal(await status({ success: false }), REQUEST_STATUS.FAILED)
assert.equal(await status({ success: false, data: { code: 'LOCKED', retryAfterSeconds: 30 } }), REQUEST_STATUS.LOCKED)
assert.equal(await status({ response: { status: 503, data: { success: false, message: 'unavailable' } } }, true), REQUEST_STATUS.EXCEPTION)
assert.equal(await status({ response: { status: 429, data: { success: false, data: { code: 'LOCKED', retryAfterSeconds: 30 } } } }, true), REQUEST_STATUS.LOCKED)
await server.close()
console.log('Login distinguishes service errors from credential failures and lockouts')
