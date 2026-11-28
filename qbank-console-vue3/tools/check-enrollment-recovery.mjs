import assert from 'node:assert/strict'
import { rememberEnrollment, readEnrollments, forgetEnrollment } from '../src/auth/enrollmentRecovery.js'

class MemoryStorage {
  values = new Map()
  get length() { return this.values.size }
  key(index) { return Array.from(this.values.keys())[index] ?? null }
  getItem(key) { return this.values.get(key) ?? null }
  setItem(key, value) { this.values.set(key, String(value)) }
  removeItem(key) { this.values.delete(key) }
}
const storage = new MemoryStorage()
const a = 'a9c62262-bdd5-43a9-8a3b-f89bc0c3a870'
const b = '6f5cb2cc-a391-4a11-992e-6a64652dc2af'
assert.equal(rememberEnrollment('7', a, storage), true)
assert.equal(rememberEnrollment('7', b, storage), true)
assert.equal(rememberEnrollment('8', a, storage), true)
assert.deepEqual(readEnrollments('7', storage), [b, a])
assert.deepEqual(readEnrollments('8', storage), [a])
assert.equal(forgetEnrollment('7', a, storage), true)
assert.deepEqual(readEnrollments('7', storage), [b], 'an old response cannot remove the new marker')
assert.equal(rememberEnrollment('7', 'not-an-id', storage), false)
assert.equal(rememberEnrollment(null, a, storage), false)
const blocked = {setItem() { throw Error('blocked') }, getItem() { throw Error('blocked') }}
assert.equal(rememberEnrollment('7', a, blocked), false, 'must not activate if storage fails')
assert.deepEqual(readEnrollments('7', blocked), [])
assert.equal(forgetEnrollment('7', b, blocked), false)
assert.ok([...storage.values.values()].every(v => v === '1'), 'no TOTP secret is persisted')
console.log('Enrollment recovery markers: account isolation, old responses, storage failures passed')
