// Ánh xạ DTO của Spring Boot (CSDL v4) <-> mô hình UI của QBank Console.
// Bảng ánh xạ (đã đối chiếu be-web-admin enums + Question entity):
//   validation_status: not_checked -> draft | clean -> ok | has_issues -> flag | stale -> stale | failed -> flag
//   difficulty: DB 1..5 (NULL = chưa đánh giá) <-> UI 3 mức LV = ['Dễ','Trung bình','Khó'] (index 0..2)
//     UI -> DB: LV_DB = [1,2,3] (giữ nguyên mock.js). DB -> UI: 1->0, 2->1, >=3->2, null->1 (Trung bình).
//   correct_label: 'A'..'D' <-> index 0..3
//   status: active/archived (UI chỉ hiển thị active; archived = đã xóa mềm)

export const VALIDATION_TO_UI = {
  not_checked: 'draft',
  clean: 'ok',
  has_issues: 'flag',
  stale: 'stale',
  failed: 'flag',
}
export const UI_TO_VALIDATION = { draft: 'not_checked', ok: 'clean', flag: 'has_issues', stale: 'stale' }

export const LV_DB = [1, 2, 3]
export const difficultyToUi = (d) => (d == null ? 1 : d <= 1 ? 0 : d === 2 ? 1 : 2)
export const difficultyToDb = (uiIndex) => LV_DB[+uiIndex] ?? 2
export const labelToIndex = (label) => Math.max(0, 'ABCD'.indexOf(label || 'A'))
export const indexToLabel = (i) => 'ABCD'[+i] || 'A'

export function formatInstant(iso) {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '—'
  const dd = String(d.getDate()).padStart(2, '0')
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const hh = d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })
  return `${dd}/${mm} ${hh}`
}

/** SubjectView -> mục COURSES của UI. topics/topicIds được điền sau khi tải topics. */
export function subjectToCourse(s) {
  return {
    id: s.subjectCode,
    name: s.subjectName,
    dept: s.description || '',
    topics: [],
    subjectId: s.subjectId,
    topicIds: [],
    active: s.active !== false,
  }
}

/** QuestionDetail -> q của UI (đủ 4 phương án + đáp án đúng để hiển thị/sửa). */
export function detailToQuestion(d, courseIndex, topicIndex) {
  return {
    id: d.questionId,
    text: d.stem,
    o: [d.optionA, d.optionB, d.optionC, d.optionD],
    a: labelToIndex(d.correctLabel),
    c: courseIndex,
    tp: topicIndex,
    l: difficultyToUi(d.difficulty),
    st: VALIDATION_TO_UI[d.validationStatus] || 'draft',
    title: d.title || '',
    fb: d.generalFeedback || '',
    updatedAt: formatInstant(d.updatedAt),
    by: d.updatedBy ? String(d.updatedBy) : 'Hệ thống',
    ver: d.version ?? 1,
    subjectId: d.subjectId,
    topicId: d.topicId,
    validationStatus: d.validationStatus,
    source: d.source,
    revisionNo: d.revisionNo,
  }
}

/** q của UI -> CreateQuestionRequest của backend. */
export function questionToCreate(q, course) {
  return {
    subjectId: course.subjectId,
    topicId: course.topicIds?.[q.tp] ?? null,
    title: q.title?.trim() || null,
    stem: q.text.trim(),
    optionA: q.o[0].trim(),
    optionB: q.o[1].trim(),
    optionC: q.o[2].trim(),
    optionD: q.o[3].trim(),
    correctLabel: indexToLabel(q.a),
    contentFormat: 'plain',
    generalFeedback: q.fb?.trim() || null,
    difficulty: difficultyToDb(q.l),
  }
}

/** q của UI -> UpdateQuestionRequest (bắt buộc expectedVersion để khóa lạc quan, lệch -> 409). */
export function questionToUpdate(q, course) {
  return {
    expectedVersion: q.ver ?? 1,
    topicId: course.topicIds?.[q.tp] ?? null,
    title: q.title?.trim() || null,
    stem: q.text.trim(),
    optionA: q.o[0].trim(),
    optionB: q.o[1].trim(),
    optionC: q.o[2].trim(),
    optionD: q.o[3].trim(),
    correctLabel: indexToLabel(q.a),
    contentFormat: 'plain',
    generalFeedback: q.fb?.trim() || null,
    difficulty: difficultyToDb(q.l),
    changeNote: 'Sửa từ QBank Console',
  }
}

/**
 * AiSuggestionView[] (mỗi dòng = 1 vấn đề) -> 1 mục S.sug của UI (gom theo câu hỏi).
 * UI ReviewView cần: { qid, tx (văn bản đề xuất cho stem), is: [{k,d}], diff, sev, tid, raw: [...] }
 * - tx: lấy proposedText của đề xuất nhắm vào stem/question; không có thì giữ nguyên stem gốc.
 * - raw: giữ nguyên danh sách để duyệt từng đề xuất qua POST /ai/suggestions/{id}/decision.
 */
export function suggestionsToUi(list, question, taskId) {
  const stemSug = list.find((s) => ['stem', 'question'].includes(String(s.targetField || '').toLowerCase()))
  const tx = stemSug?.proposedText || question.text
  const diff = list.find((s) => s.suggestedDifficulty != null)?.suggestedDifficulty
  const sev = list.some((s) => s.severity === 'high') ? 'Cao' : list.some((s) => s.severity === 'medium') ? 'Trung bình' : 'Thấp'
  return {
    qid: question.id,
    tx,
    is: list.map((s) => ({ k: s.issueLabel || s.issueType, d: s.explanation || s.proposedText || '' })),
    diff: diff != null ? ['Dễ', 'Trung bình', 'Khó'][difficultyToUi(diff)] : 'Trung bình',
    sev,
    tid: taskId || list[0]?.taskId || '',
    raw: list,
  }
}
