// Only non-secret generation identifiers are persisted; never store the TOTP key here.
// A separate key per generation prevents a late tab from overwriting a newer marker.
const PREFIX = 'qbank.tfa.enrollmentGeneration.'
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
function prefix(adminId) {
  const id = String(adminId ?? '')
  return /^[1-9][0-9]{0,18}$/.test(id) ? PREFIX + id + '.' : null
}
export function rememberEnrollment(adminId, generation, storage) {
  const name = prefix(adminId)
  if (!name || !UUID.test(generation ?? '')) return false
  try {
    const target = storage ?? globalThis.localStorage
    target.setItem(name + generation, '1')
    return target.getItem(name + generation) === '1'
  } catch { return false }
}
export function readEnrollments(adminId, storage) {
  const name = prefix(adminId)
  if (!name) return []
  try {
    const target = storage ?? globalThis.localStorage
    const found = []
    for (let i = 0; i < target.length; i++) {
      const entry = target.key(i)
      if (!entry?.startsWith(name)) continue
      const generation = entry.slice(name.length)
      if (UUID.test(generation) && target.getItem(entry) === '1') found.push(generation)
    }
    return found.sort()
  } catch { return [] }
}
export function forgetEnrollment(adminId, generation, storage) {
  const name = prefix(adminId)
  if (!name || !UUID.test(generation ?? '')) return false
  try {
    const target = storage ?? globalThis.localStorage
    target.removeItem(name + generation)
    return target.getItem(name + generation) === null
  } catch { return false }
}
