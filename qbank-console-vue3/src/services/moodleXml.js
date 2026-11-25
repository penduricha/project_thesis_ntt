const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]))
const cd = t => `<text><![CDATA[${String(t).replace(/\]\]>/g, ']]]]><![CDATA[>')}]]></text>`

/* Xuất: mỗi câu kèm cây danh mục $course$/top/Môn học/Chương */
export function buildXml(list, { COURSES, code, topicName }) {
  return '<?xml version="1.0" encoding="UTF-8"?>\n<quiz>\n' + list.map(q => {
    const crs = COURSES[q.c]
    const catPath = `$course$/top/${crs ? crs.id + ' - ' + crs.name : 'Chung'}/${topicName(q.c, q.tp)}`
    return `  <question type="category">\n    <category><text>${esc(catPath)}</text></category>\n  </question>\n  <question type="multichoice">\n    <name>${cd(q.title || code(q))}</name>\n    <questiontext format="html">${cd(q.text)}</questiontext>\n    <generalfeedback format="html">${cd(q.fb || '')}</generalfeedback>\n    <defaultgrade>1.0000000</defaultgrade>\n    <single>true</single>\n    <shuffleanswers>true</shuffleanswers>\n    <answernumbering>ABCD</answernumbering>\n`
      + q.o.map((o, i) => `    <answer fraction="${i === q.a ? 100 : 0}" format="html">${cd(o)}</answer>`).join('\n') + '\n  </question>'
  }).join('\n') + '\n</quiz>'
}
export const isValidXml = s => !new DOMParser().parseFromString(s, 'application/xml').querySelector('parsererror')

/* Nhập: null nếu XML sai cấu trúc; ngược lại {items, reasons} (chỉ nhận multichoice, đúng 4 phương án, đúng 1 đáp án) */
export function parseMoodleXml(v) {
  const d = new DOMParser().parseFromString(v, 'application/xml')
  if (!v.trim() || d.querySelector('parsererror')) return null
  const items = [], reasons = []
  const tx = (n, s) => ((n.querySelector(s) || {}).textContent || '').replace(/<[^>]+>/g, '').trim(), full = n => parseFloat(n.getAttribute('fraction')) === 100
  d.querySelectorAll('question[type="multichoice"]').forEach((x, idx) => {
    const a = [...x.querySelectorAll(':scope > answer')], t = tx(x, 'questiontext > text'), label = tx(x, 'name > text') || `Câu #${idx + 1}`
    if (!t) return reasons.push(`${label}: thiếu nội dung câu dẫn`)
    if (a.length !== 4) return reasons.push(`${label}: có ${a.length} phương án (yêu cầu đúng 4)`)
    if (a.some(n => !tx(n, 'text'))) return reasons.push(`${label}: có phương án để trống`)
    if (a.filter(full).length !== 1) return reasons.push(`${label}: có ${a.filter(full).length} đáp án đúng (yêu cầu đúng 1)`)
    items.push({ text: t, o: a.map(n => tx(n, 'text')), a: a.findIndex(full) })
  })
  return { items, reasons }
}
