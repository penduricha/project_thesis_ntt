import Papa from 'papaparse'
import { unzipSync, strFromU8 } from 'fflate'

export const MAX_IMPORT_BYTES = 2 * 1024 * 1024
export const MAX_IMPORT_QUESTIONS = 200
const required = ['stem', 'optionA', 'optionB', 'optionC', 'optionD', 'correctLabel']
const optional = ['title', 'generalFeedback']
const fields = [...required, ...optional]

// Errors contain only translation keys and parameters, not translated strings,
// so an open preview reacts immediately to a change of language.
const problem = (key, params = {}) => ({ key: `courseImport.${key}`, params })
const fail = (key, params) => { throw problem(key, params) }

function normalizeRows(rows) {
  if (!Array.isArray(rows) || !rows.length) fail('empty')
  if (rows.length > MAX_IMPORT_QUESTIONS) fail('tooMany', { max: MAX_IMPORT_QUESTIONS })
  const errors = [], items = [], titles = new Set()
  rows.forEach((row, index) => {
    const n = index + 1
    if (!row || typeof row !== 'object' || Array.isArray(row)) { errors.push(problem('invalidRow', { n })); return }
    const unknown = Object.keys(row).filter(key => !fields.includes(key))
    if (unknown.length) { errors.push(problem('unknownFields', { n, fields: unknown.join(', ') })); return }
    const clean = {}
    for (const key of fields) {
      const value = row[key]
      if (value == null && optional.includes(key)) { clean[key] = ''; continue }
      if (typeof value !== 'string') { errors.push(problem('textRequired', { n, field: key })); continue }
      clean[key] = value.trim()
      if (required.includes(key) && !clean[key]) errors.push(problem('required', { n, field: key }))
      const limit = key === 'title' ? 255 : key === 'generalFeedback' ? 4000 : 20000
      if (clean[key].length > limit) errors.push(problem('tooLong', { n, field: key, max: limit }))
    }
    const label = clean.correctLabel?.toUpperCase()
    if (label && !/^[ABCD]$/.test(label)) errors.push(problem('answerInvalid', { n }))
    if (clean.title) {
      const title = clean.title.toLowerCase().replace(/\s+/g, ' ')
      if (titles.has(title)) errors.push(problem('duplicateTitle', { n }))
      titles.add(title)
    }
    items.push({ ...clean, correctLabel: label })
  })
  return { items, errors }
}

function rowsFromTable(table) {
  if (!table.length) fail('empty')
  const headers = table[0].map((v, i) => typeof v === 'string' ? (i === 0 ? v.replace(/^\uFEFF/, '') : v).trim() : '')
  if (new Set(headers).size !== headers.length || headers.some(h => !fields.includes(h)) || required.some(h => !headers.includes(h))) fail('headers')
  const rows = table.slice(1).filter(row => row.some(v => v != null && String(v).trim() !== ''))
  return rows.map(row => {
    if (row.length > headers.length && row.slice(headers.length).some(v => v != null && String(v).trim() !== '')) fail('headers')
    return Object.fromEntries(headers.map((h, i) => [h, row[i] ?? '']))
  })
}

function parseXml(text) {
  if (/<!DOCTYPE|<!ENTITY/i.test(text)) fail('invalidXml')
  const doc = new DOMParser().parseFromString(text, 'application/xml')
  if (doc.querySelector('parsererror') || doc.documentElement?.tagName !== 'quiz') fail('invalidXml')
  const questions = [...doc.documentElement.children].filter(el => el.tagName === 'question' && el.getAttribute('type') !== 'category')
  if (!questions.length) fail('empty')
  if (questions.length > MAX_IMPORT_QUESTIONS) fail('tooMany', { max: MAX_IMPORT_QUESTIONS })
  const errors = [], rows = []
  function content(el, n) {
    if (!el) return ''
    if (el.querySelector('file')) { errors.push(problem('xmlMedia', { n })); return '' }
    const textNode = [...el.children].find(x => x.tagName === 'text')
    const raw = textNode?.textContent || ''
    const format = (el.getAttribute('format') || '').toLowerCase()
    if (format && !['html', 'plain', 'plain_text'].includes(format)) { errors.push(problem('xmlFormatUnsupported', { n })); return '' }
    if (format !== 'html') return raw.trim()
    // Strip all attributes before parsing so untrusted markup cannot load resources.
    // Refuse unsupported tags rather than silently losing embedded content.
    let unsupported = false
    const safe = raw.replace(/<[^>]*>/g, tag => {
      const match = tag.match(/^<\s*(\/?)\s*([a-zA-Z0-9]+)(?:\s[^>]*)?\s*\/?>$/)
      if (!match || !['p', 'div', 'br', 'b', 'strong', 'i', 'em', 'u', 'span', 'ul', 'ol', 'li', 'sub', 'sup', 'code', 'pre'].includes(match[2].toLowerCase())) {
        unsupported = true
        return ''
      }
      return `<${match[1]}${match[2].toLowerCase()}>`
    })
    if (unsupported) { errors.push(problem('xmlMedia', { n })); return '' }
    const html = new DOMParser().parseFromString(safe, 'text/html')
    html.querySelectorAll('br').forEach(node => node.replaceWith('\n'))
    html.querySelectorAll('p,div,li').forEach(node => node.append('\n'))
    return html.body.textContent.trim()
  }
  questions.forEach((q, index) => {
    const n = index + 1
    const answers = [...q.children].filter(el => el.tagName === 'answer')
    const fractions = answers.map(a => Number(a.getAttribute('fraction')))
    const single = q.querySelector(':scope > single')?.textContent.trim().toLowerCase()
    if (q.querySelector('file')) errors.push(problem('xmlMedia', { n }))
    if (answers.some(answer => answer.querySelector('feedback > text')?.textContent.trim())) errors.push(problem('xmlAnswerFeedback', { n }))
    if (q.getAttribute('type') !== 'multichoice' || (single && single !== 'true' && single !== '1') || answers.length !== 4 || fractions.filter(f => f === 100).length !== 1 || answers.some(a => !a.hasAttribute('fraction') || !a.getAttribute('fraction').trim()) || fractions.some(f => f !== 0 && f !== 100)) {
      errors.push(problem('xmlQuestion', { n })); return
    }
    const row = { title: content(q.querySelector(':scope > name'), n), stem: content(q.querySelector(':scope > questiontext'), n), generalFeedback: content(q.querySelector(':scope > generalfeedback'), n), correctLabel: 'ABCD'[fractions.indexOf(100)] }
    answers.forEach((answer, i) => { row[`option${'ABCD'[i]}`] = content(answer, n) })
    rows.push(row)
  })
  if (errors.length) return { items: [], errors }
  return normalizeRows(rows)
}

export async function parseQuestionFile(file, { readXlsx } = {}) {
  if (!file || file.size === 0 || file.size > MAX_IMPORT_BYTES) fail('fileSize', { max: 2 })
  const extension = file.name.toLowerCase().split('.').pop()
  if (!['xml', 'csv', 'xlsx', 'json'].includes(extension)) fail('fileType')
  try {
    if (extension === 'xlsx') {
      // Inspect archive sizes before handing the workbook to the XLSX reader.
      // This limits ZIP expansion and rejects formulas rather than importing a
      // stale cached formula result as if it were user-entered text.
      let expanded = 0, count = 0
      const archive = unzipSync(new Uint8Array(await file.arrayBuffer()), { filter: entry => {
        count++
        expanded += entry.originalSize
        if (!Number.isFinite(entry.originalSize) || count > 1000 || expanded > 16 * 1024 * 1024) fail('xlsxTooLarge')
        return /^xl\/worksheets\/[^/]+\.xml$/i.test(entry.name)
      } })
      if (Object.values(archive).some(bytes => /<(?:\w+:)?f(?:\s|>|\/)/i.test(strFromU8(bytes)))) fail('xlsxFormula')
      const read = readXlsx || (await import('read-excel-file/browser')).default
      const sheets = await read(file)
      if (sheets.length !== 1) fail('oneSheet')
      return normalizeRows(rowsFromTable(sheets[0].data))
    }
    const text = new TextDecoder('utf-8', { fatal: true }).decode(await file.arrayBuffer())
    if (extension === 'xml') return parseXml(text)
    if (extension === 'json') return normalizeRows(JSON.parse(text))
    const result = Papa.parse(text, { skipEmptyLines: 'greedy', delimiter: ',' })
    if (result.errors.length) fail('invalidCsv')
    return normalizeRows(rowsFromTable(result.data))
  } catch (error) {
    if (error?.key?.startsWith('courseImport.')) throw error
    fail(extension === 'json' ? 'invalidJson' : extension === 'xlsx' ? 'invalidXlsx' : 'invalidFile')
  }
}

// The file is parsed locally; Spring independently validates the normalized batch.
// A lost response may mean some or all rows committed. Never retry automatically.
export async function submitCourseQuestions(items, { importBatch, onSaved, subjectId, topicId, difficulty }) {
  let result
  try {
    result = await importBatch({ subjectId, topicId, difficulty, questions: items.map(item => ({
      title: item.title || null, stem: item.stem, optionA: item.optionA, optionB: item.optionB,
      optionC: item.optionC, optionD: item.optionD, correctLabel: item.correctLabel,
      generalFeedback: item.generalFeedback || null,
    })) })
  } catch (error) {
    const status = error?.response?.status
    if ([400, 401, 403, 404, 405, 413, 415, 422].includes(status)) {
      return { saved: 0, batchRejected: true, status, errorCode: error.response?.data?.code }
    }
    return { saved: null, batchUncertain: true }
  }
  if (!result || !Array.isArray(result.questions) || !Number.isInteger(result.saved) ||
      result.saved !== result.questions.length || result.saved > items.length ||
      result.questions.some(q => !q || q.questionId == null || q.subjectId !== subjectId || q.topicId !== topicId) ||
      (result.failedAt != null && (result.failedAt !== result.saved + 1 || result.failedAt > items.length)) ||
      (result.failedAt == null && (result.saved !== items.length || result.uncertain === true))) {
    return { saved: null, batchUncertain: true }
  }
  try { result.questions.forEach(onSaved) }
  catch (_) { return { saved: result.saved, localUpdateFailure: true } }
  return result
}
