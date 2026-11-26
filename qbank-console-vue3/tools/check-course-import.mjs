import assert from 'node:assert/strict'
import { JSDOM } from 'jsdom'
import { zipSync, strToU8 } from 'fflate'
import readXlsx from 'read-excel-file/node'
import { parseQuestionFile, submitCourseQuestions } from '../src/services/courseQuestionImport.js'

const dom = new JSDOM('')
globalThis.DOMParser = dom.window.DOMParser
const makeFile = (name, text) => new File([text], name)
const row = { stem: 'A, B?', optionA: 'Một', optionB: 'Hai', optionC: 'Ba', optionD: 'Bốn', correctLabel: 'B', title: 'Câu 1', generalFeedback: 'Giải thích' }
const json = await parseQuestionFile(makeFile('questions.json', JSON.stringify([row])))
assert.deepEqual(json.errors, [])
assert.equal(json.items[0].stem, row.stem)
const csv = 'stem,optionA,optionB,optionC,optionD,correctLabel,title,generalFeedback\r\n"A, B?",Một,Hai,Ba,Bốn,B,Câu 1,Giải thích\r\n'
assert.deepEqual((await parseQuestionFile(makeFile('questions.csv', csv))).items, json.items)
const xml = `<quiz><question type="category"><category><text>Course</text></category></question><question type="multichoice"><name><text>Câu 1</text></name><questiontext format="html"><text><![CDATA[<p>A, B?</p>]]></text></questiontext><single>true</single><generalfeedback><text>Giải thích</text></generalfeedback><answer fraction="0"><text>Một</text></answer><answer fraction="100"><text>Hai</text></answer><answer fraction="0"><text>Ba</text></answer><answer fraction="0"><text>Bốn</text></answer></question></quiz>`
assert.deepEqual((await parseQuestionFile(makeFile('questions.xml', xml))).items, json.items)
const invalidXml = await parseQuestionFile(makeFile('x.xml', xml.replace('fraction="100"', 'fraction="50"')))
assert.equal(invalidXml.errors[0].key, 'courseImport.xmlQuestion')
const mediaXml = await parseQuestionFile(makeFile('x.xml', xml.replace('<p>A, B?</p>', '<img src="https://example.test/x">')))
assert.equal(mediaXml.errors[0].key, 'courseImport.xmlMedia')
const feedbackXml = await parseQuestionFile(makeFile('x.xml', xml.replace('<answer fraction="0"><text>Một</text>', '<answer fraction="0"><text>Một</text><feedback><text>Specific feedback</text></feedback>')))
assert.equal(feedbackXml.errors[0].key, 'courseImport.xmlAnswerFeedback')
await assert.rejects(parseQuestionFile(makeFile('x.xml', '<!DOCTYPE quiz><quiz/>')), e => e.key === 'courseImport.invalidXml')
const invalid = await parseQuestionFile(makeFile('x.json', JSON.stringify([{ ...row, correctLabel: 'E', optionD: '' }])))
assert.ok(invalid.errors.some(e => e.key === 'courseImport.answerInvalid'))
assert.ok(invalid.errors.some(e => e.key === 'courseImport.required'))
assert.ok((await parseQuestionFile(makeFile('x.json', JSON.stringify([row, row])))).errors.some(e => e.key === 'courseImport.duplicateTitle'))
await assert.rejects(parseQuestionFile(makeFile('x.csv', 'stem,optionA\na,b')), e => e.key === 'courseImport.headers')
await assert.rejects(parseQuestionFile(makeFile('x.json', JSON.stringify(Array(201).fill(row)))), e => e.key === 'courseImport.tooMany')
await assert.rejects(parseQuestionFile(makeFile('x.doc', 'hello')), e => e.key === 'courseImport.fileType')

// A minimal real XLSX archive; parse through the same row normalization using
// the package's Node reader (the browser reader uses Web Workers).
const header = Object.keys(row)
const cells = (values, r) => values.map((v, i) => `<c r="${String.fromCharCode(65 + i)}${r}" t="inlineStr"><is><t>${String(v).replace(/&/g, '&amp;').replace(/</g, '&lt;')}</t></is></c>`).join('')
const files = {
  'xl/workbook.xml': '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Questions" sheetId="1" r:id="rId1"/></sheets></workbook>',
  'xl/_rels/workbook.xml.rels': '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>',
  'xl/worksheets/sheet1.xml': `<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1">${cells(header, 1)}</row><row r="2">${cells(header.map(k => row[k]), 2)}</row></sheetData></worksheet>`,
}
const bytes = zipSync(Object.fromEntries(Object.entries(files).map(([path, text]) => [path, strToU8(text)])))
const xlsx = await parseQuestionFile(new File([bytes], 'questions.xlsx'), { readXlsx: async f => readXlsx(Buffer.from(await f.arrayBuffer())) })
assert.deepEqual(xlsx.items, json.items)
const formulaFiles = { ...files, 'xl/worksheets/sheet1.xml': files['xl/worksheets/sheet1.xml'].replace('<sheetData>', '<sheetData><row><c><f>1+1</f><v>2</v></c></row>') }
const formulaBytes = zipSync(Object.fromEntries(Object.entries(formulaFiles).map(([path, text]) => [path, strToU8(text)])))
await assert.rejects(parseQuestionFile(new File([formulaBytes], 'formula.xlsx'), { readXlsx: async () => { throw new Error('must not parse formulas') } }), e => e.key === 'courseImport.xlsxFormula')
const expandedZip = zipSync({ 'xl/worksheets/sheet1.xml': new Uint8Array(17 * 1024 * 1024) })
await assert.rejects(parseQuestionFile(new File([expandedZip], 'large.xlsx')), e => e.key === 'courseImport.xlsxTooLarge')

let requests = [], saved = []
const importBatch = async body => { requests.push(body); return { saved: 1, failedAt: null, uncertain: false, questions: [{ questionId: 1, subjectId: 7, topicId: 42 }] } }
let outcome = await submitCourseQuestions(json.items, { importBatch, onSaved: q => saved.push(q), subjectId: 7, topicId: 42, difficulty: 2 })
assert.equal(outcome.saved, 1)
assert.deepEqual(requests[0], { subjectId: 7, topicId: 42, difficulty: 2, questions: [row] })
assert.equal(saved.length, 1)
requests = []; saved = []
outcome = await submitCourseQuestions([row, row, row], { subjectId: 7, topicId: 42, difficulty: 2, onSaved: q => saved.push(q), importBatch: async body => { requests.push(body); return { saved: 1, failedAt: 2, uncertain: false, status: 409, errorCode: 'TITLE_DUPLICATED', questions: [{ questionId: 1, subjectId: 7, topicId: 42 }] } } })
assert.equal(outcome.saved, 1)
assert.equal(outcome.failedAt, 2)
assert.equal(saved.length, 1)
assert.equal(requests.length, 1, 'must submit one batch and never retry automatically')
outcome = await submitCourseQuestions([row], { subjectId: 7, topicId: 42, difficulty: 2, onSaved: () => {}, importBatch: async () => { throw new Error('timeout') } })
assert.equal(outcome.batchUncertain, true)
assert.equal(outcome.saved, null)
outcome = await submitCourseQuestions([row], { subjectId: 7, topicId: 42, difficulty: 2, onSaved: () => {}, importBatch: async () => { throw { response: { status: 400, data: { code: 'VALIDATION_FAILED' } } } } })
assert.equal(outcome.batchRejected, true)
assert.equal(outcome.saved, 0)
outcome = await submitCourseQuestions([row], { subjectId: 7, topicId: 42, difficulty: 2, onSaved: () => { throw new Error('UI update') }, importBatch: async () => ({ saved: 1, failedAt: null, questions: [{ questionId: 1, subjectId: 7, topicId: 42 }] }) })
assert.deepEqual(outcome, { saved: 1, localUpdateFailure: true })
dom.window.close()
console.log('Course import: CSV, JSON, Moodle XML, XLSX, validation, payload and partial-failure checks passed')
