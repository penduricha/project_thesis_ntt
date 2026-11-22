import { reactive } from 'vue'
import { COURSES as RAW_COURSES } from './data/mock.js'
import { llm } from './services/llmSimulator.js'
import { questionRepository as repo } from './repositories/questionRepository.js'

/* Danh sách môn học dạng reactive để thêm/sửa/xóa là giao diện tự cập nhật */
export const COURSES = reactive(RAW_COURSES)

export const blankF = (hideAns = false) => ({ q: '', c: '', tp: '', l: '', s: '', sort: 'id', page: 1, hideAns })
export const S = reactive({
  ci: 0, n: 12, cq: '', rf: 'all', tf: 'all', rsel: new Set(), dlg: { kind: null, props: {}, n: 0 }, role: 'admin', name: 'Từ Quang Nhật', colab: true, tn: 1, status: 'idle',
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

/* ---------- Hộp thoại ---------- */
export const nowStr = () => { const d = new Date(); return String(d.getDate()).padStart(2, '0') + '/' + String(d.getMonth() + 1).padStart(2, '0') + ' ' + d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }) }
export const openDialog = (kind, props = {}) => { S.dlg.n++; S.dlg.kind = kind; S.dlg.props = props }
export const closeDialog = () => { S.dlg.kind = null; S.dlg.props = {} }

/* ---------- Câu hỏi ---------- */
export function editQuestion(id, ctx = {}) {
  if (!COURSES.length) return toast('Hãy tạo ít nhất một môn học trước khi thêm câu hỏi', 'wa')
  if (id && busy({ id })) return toast('Câu hỏi đang được kiểm định, vui lòng đợi hoàn tất', 'wa')
  openDialog('question', { id, ctx })
}
export async function saveQuestion(d, eid) {
  if (eid) {
    const cur = S.q.find(x => x.id == eid)
    const next = { ...cur, ...d, st: cur.st == 'draft' ? 'draft' : 'stale', ver: (cur.ver || 1) + 1 }
    await repo.upsert(next); Object.assign(cur, next)
    S.sug = S.sug.filter(s => s.qid != eid); log('Cập nhật nội dung câu hỏi ' + code({ id: eid }), 'info')
  } else {
    const q = { id: S.n++, st: 'draft', src: 'manual', ver: 1, ...d }
    await repo.upsert(q); S.q.push(q); log('Thêm câu hỏi mới ' + code(q), 'ok')
  }
  toast('Đã lưu câu hỏi vào cơ sở dữ liệu', 'ok')
}
export function askDeleteQuestion(id) {
  const q = S.q.find(x => x.id == id); if (!q) return
  if (busy(q)) return toast('Không thể xóa khi câu hỏi đang kiểm định', 'er')
  openDialog('confirm', { title: `Xóa câu hỏi ${code(q)}?`, text: 'Câu hỏi và đề xuất AI liên quan sẽ bị xóa khỏi ngân hàng.', cancel: 'Giữ lại', ok: 'Xóa câu hỏi', danger: true, action: () => deleteQuestion(id) })
}
async function deleteQuestion(id) {
  const q = S.q.find(x => x.id == id), sug = S.sug.find(s => s.qid == id)
  S.q = S.q.filter(x => x.id != id); S.sug = S.sug.filter(s => s.qid != id); S.sel.delete(id)
  await repo.removeMany([id]); log('Xóa câu hỏi ' + code({ id }), 'wa')
  toast('Đã xóa ' + code({ id }), 'wa', 'Hoàn tác', async () => {
    if (q) { await repo.restoreMany([q]); S.q.push(q) }
    if (sug) S.sug.push(sug)
    toast('Đã khôi phục ' + code({ id }), 'ok')
  })
}

/* ---------- Môn học ---------- */
export function addCourse({ id, name, dept, topic, active }) {
  COURSES.push({ id, name, dept, topics: [topic || 'Chương 1: Tổng quan'], subjectId: null, topicIds: [null], active }); S.cq = ''
  log(`Thêm môn học “${id} – ${name}”`, 'ok'); toast('Đã lưu môn học mới', 'ok')
}
export function updateCourse(i, { id, name, dept, active }) {
  const old = { ...COURSES[i] }; Object.assign(COURSES[i], { id, name, dept, active })
  log(`Cập nhật môn học “${old.id} – ${old.name}” → “${id} – ${name}”`, 'info'); toast('Đã cập nhật thông tin môn học', 'ok')
}
export function deleteCourse(i) {
  const ids = S.q.filter(q => q.c == i).map(q => q.id), nm = `${COURSES[i].id} – ${COURSES[i].name}`
  S.q = S.q.filter(q => q.c != i); S.q.forEach(q => { if (q.c > i) q.c-- })
  S.sug = S.sug.filter(s => !ids.includes(s.qid)); ids.forEach(id => S.sel.delete(id))
  COURSES.splice(i, 1); S.ci = 0
  log(`Xóa môn học “${nm}” và ${ids.length} câu hỏi`, 'wa'); toast(`Đã xóa môn học và ${ids.length} câu hỏi`, 'wa')
}
export function askDeleteCourse(i, after) {
  const c = COURSES[i]; if (!c) return
  openDialog('confirm', { title: `Xóa môn học “${c.id} – ${c.name}”?`, text: 'Toàn bộ chương và câu hỏi thuộc môn học này sẽ bị xóa khỏi ngân hàng.', cancel: 'Giữ lại', ok: 'Xóa môn học', danger: true, action: () => { deleteCourse(i); after?.() } })
}

/* ---------- Xem xét đề xuất AI (có hoàn tác) ---------- */
export function decide(id, ok) {
  const s = S.sug.find(x => x.qid == id), q = S.q.find(x => x.id == id); if (!s || !q) return
  const prev = { text: q.text, st: q.st, updatedAt: q.updatedAt, by: q.by }, bak = { ...s }
  if (ok) { q.text = s.tx; q.updatedAt = nowStr(); q.by = S.name + ' (Duyệt AI)'; log(`Chấp nhận đề xuất AI cho ${code(q)} (có lưu vết)`, 'ok') }
  else log(`Từ chối đề xuất AI cho ${code(q)}, giữ nội dung gốc`, 'info')
  q.st = 'ok'; S.sug = S.sug.filter(x => x.qid != id); S.rsel.delete(id)
  repo.upsert({ ...q, o: [...q.o] })
  toast(ok ? `Đã cập nhật ${code(q)} theo đề xuất AI` : `Đã giữ nguyên bản gốc ${code(q)}`, ok ? 'ok' : '', 'Hoàn tác', () => {
    Object.assign(q, prev); S.sug.push(bak); repo.upsert({ ...q, o: [...q.o] }); toast(`Đã hoàn tác quyết định cho ${code(q)}`, 'info')
  })
}
export function bulkDecide(ok) {
  const ids = [...S.rsel].filter(id => S.sug.some(s => s.qid == id))
  if (!ids.length) return toast('Hãy chọn ít nhất một đề xuất để xử lý', 'wa')
  ids.forEach(id => {
    const s = S.sug.find(x => x.qid == id), q = S.q.find(x => x.id == id)
    if (ok && s && q) { q.text = s.tx; q.updatedAt = nowStr(); q.by = S.name + ' (Duyệt AI)' }
    if (q) { q.st = 'ok'; repo.upsert({ ...q, o: [...q.o] }) }
  })
  S.sug = S.sug.filter(s => !ids.includes(s.qid)); S.rsel.clear()
  log(`${ok ? 'Chấp nhận' : 'Từ chối'} hàng loạt ${ids.length} đề xuất AI`, ok ? 'ok' : 'info')
  toast(`Đã ${ok ? 'chấp nhận' : 'từ chối'} ${ids.length} đề xuất`, ok ? 'ok' : '')
}
