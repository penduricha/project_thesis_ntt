/* Repository mock: sau này đổi thân hàm sang axios/fetch gọi Spring Boot, giữ nguyên chữ ký. */
import { QUESTIONS } from '../data/mock.js'
const delay = (ms = 150) => new Promise(r => setTimeout(r, ms))
const clone = q => ({ ...q, o: [...q.o] })
const db = QUESTIONS.map(clone)
export const questionRepository = {
  async findAll() { await delay(); return db.map(clone) },
  async upsert(q) { await delay(120); const i = db.findIndex(x => x.id === q.id); if (i >= 0) db[i] = clone(q); else db.push(clone(q)); return clone(q) },
  async removeMany(ids) { await delay(100); for (const id of ids) { const i = db.findIndex(q => q.id === id); if (i >= 0) db.splice(i, 1) } },
  async restoreMany(items) { await delay(100); db.push(...items.map(clone)) },
}
