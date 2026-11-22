import { reactive } from 'vue'
import { COURSES } from './data/mock.js'
import { llm } from './services/llmSimulator.js'
import { questionRepository as repo } from './repositories/questionRepository.js'

export const blankF = (hideAns = false) => ({ q: '', c: '', tp: '', l: '', s: '', sort: 'id', page: 1, hideAns })
export const S = reactive({
  ci: 0, role: 'admin', name: 'Demo Admin', colab: true, tn: 1, status: 'idle',
  q: [], sug: [], tasks: [], sel: new Set(), f: blankF(),
  log: [['09:45:12', 'Khởi tạo phiên làm việc và đồng bộ ngân hàng câu hỏi từ MariaDB', 'ok']],
  toast: { on: false, msg: '', tone: '', label: '', cb: null },
})

export const code = q => 'CH' + String(q.id).padStart(3, '0')
export const topicName = (ci, tpi) => COURSES[ci]?.topics[tpi] || 'Chương mặc định'
const live = /pending|running/
export const act = () => S.tasks.filter(t => live.test(t.st)).length
export const busy = q => S.tasks.some(t => t.qid == q.id && live.test(t.st))
export const resetFilters = () => { S.f = blankF(S.f.hideAns) }
export const clearFilters = () => Object.assign(S.f, { q: '', c: '', tp: '', l: '', s: '', page: 1 })
export function log(m, tone = 'info') { S.log.unshift([new Date().toLocaleTimeString('vi-VN'), m, tone]); if (S.log.length > 50) S.log.length = 50 }

let th
export function toast(msg, tone = '', label = '', cb = null) {
  Object.assign(S.toast, { on: true, msg, tone, label, cb })
  clearTimeout(th); th = setTimeout(() => { S.toast.on = false }, cb ? 5000 : 3500)
}
export const notYet = what => toast(`${what} chưa được chuyển sang Vue trong bản demo này`, 'wa')

const tsk = s => { const m = (s || '').match(/(\d+)\/(\d+) (\d+):(\d+)/); return m ? +m[2] * 1e6 + +m[1] * 1e4 + +m[3] * 100 + +m[4] : 0 }
export function list(isCourse) {
  const f = S.f, kw = f.q.toLowerCase()
  let arr = S.q.filter(q =>
    (!kw || (q.text + q.o.join(' ') + code(q) + (COURSES[q.c]?.id || '') + (COURSES[q.c]?.name || '')).toLowerCase().includes(kw)) &&
    (isCourse ? q.c == S.ci : (f.c === '' || q.c == f.c)) &&
    (f.tp === '' || q.tp == f.tp) && (f.l === '' || q.l == f.l) && (f.s === '' || q.st == f.s))
  if (f.sort == 'level_asc') arr = [...arr].sort((a, b) => a.l - b.l)
  else if (f.sort == 'level') arr = [...arr].sort((a, b) => b.l - a.l)
  else if (f.sort == 'recent') arr = [...arr].sort((a, b) => tsk(b.updatedAt) - tsk(a.updatedAt))
  else arr = [...arr].sort((a, b) => a.id - b.id)
  return arr
}

/* Vòng đời API: idle -> loading -> ready | error */
export async function loadQuestions() {
  S.status = 'loading'
  try { S.q = await repo.findAll(); S.status = 'ready' }
  catch { S.status = 'error'; toast('Không tải được ngân hàng câu hỏi', 'er') }
}

export function runAI(ids) {
  let n = 0
  ids.forEach(id => {
    const q = S.q.find(x => x.id == id)
    if (!q || S.tasks.some(t => t.qid == id && live.test(t.st))) return
    n++
    const t = reactive({ id: 'T-' + String(S.tn++).padStart(4, '0'), qid: id, st: 'pending', ms: 0, t0: Date.now(), started: new Date().toLocaleTimeString('vi-VN'), msg: '' })
    S.tasks.unshift(t); if (S.tasks.length > 50) S.tasks.length = 50
    setTimeout(() => { t.st = 'running' }, 600)
    setTimeout(() => {
      t.ms = Date.now() - t.t0
      if (!S.colab) {
        t.st = 'failed'; t.msg = 'Dịch vụ LLM không khả dụng (hết thời gian chờ)'
        log(`Tác vụ ${t.id} (${code(q)}) thất bại do mất kết nối LLM`, 'er')
        toast(t.id + ' thất bại có kiểm soát – quản lý câu hỏi vẫn hoạt động', 'er')
        return
      }
      const r = llm(q); t.st = 'succeeded'
      t.msg = r.is.length ? `Phát hiện ${r.is.length} vấn đề · Độ khó gợi ý: ${r.diff}` : 'Đạt chuẩn, không phát hiện lỗi'
      if (r.is.length) {
        S.sug = S.sug.filter(s => s.qid != id)
        S.sug.push({ qid: id, tx: r.tx, is: r.is, diff: r.diff, sev: r.sev, tid: t.id })
        q.st = 'flag'; log(`Hoàn tất ${t.id} (${code(q)}): có ${r.is.length} đề xuất AI`, 'wa')
      } else { q.st = 'ok'; log(`Hoàn tất ${t.id} (${code(q)}): câu hỏi đạt chuẩn`, 'ok') }
    }, 2200 + Math.random() * 1000)
  })
  if (!n) return toast('Các câu hỏi được chọn đang có tác vụ chạy, không tạo trùng', 'wa')
  log(`Tiếp nhận ${n} tác vụ kiểm định AI`, 'info')
  toast(`Đã tiếp nhận ${n} tác vụ – theo dõi ở mục “Tác vụ AI”`, 'ok')
}

export async function removeQuestions(ids) {
  const bq = S.q.filter(q => ids.includes(q.id)), bs = S.sug.filter(s => ids.includes(s.qid))
  S.q = S.q.filter(q => !ids.includes(q.id)); S.sug = S.sug.filter(s => !ids.includes(s.qid)); ids.forEach(id => S.sel.delete(id))
  log(`Xóa hàng loạt ${ids.length} câu hỏi`, 'wa')
  try { await repo.removeMany(ids) } catch { S.q.push(...bq); return toast('Xóa thất bại, đã khôi phục', 'er') }
  toast(`Đã xóa ${ids.length} câu hỏi`, 'wa', 'Hoàn tác', async () => {
    await repo.restoreMany(bq); S.q.push(...bq); S.sug.push(...bs); toast('Đã khôi phục các câu hỏi vừa xóa', 'ok')
  })
}
