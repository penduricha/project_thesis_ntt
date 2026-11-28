import assert from 'node:assert/strict'
import { createServer } from 'vite'

const server = await createServer({ server: { middlewareMode: true }, appType: 'custom', logLevel: 'error' })
const { EnrollmentRepository } = await server.ssrLoadModule('/src/auth/EnrollmentRepository.js')

let received
const done = new Promise(resolve => { received = resolve })
const callbacks = []
const service = {
  async readStatusStream(_token, {onOpen, onEvent}) {
    onOpen()
    onEvent('2fa-status', '{}')
    return {opened: true, status: 200}
  },
}
const repo = new EnrollmentRepository(service)
const stop = repo.watchTwoFaStatus(null, {
  onChange(status) {
    callbacks.push(status)
    if (callbacks.length === 2) received()
  },
})
await done
stop()
await server.close()
assert.deepEqual(callbacks, [null, null], 'empty invalidation must notify the status callback')
console.log('2FA empty SSE invalidation notifies the client callback')
