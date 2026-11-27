// Shared, synchronous UI validation. The server enforces the same syntax and lengths.
// The end assertion also rejects a trailing newline (unlike JavaScript $).
const CODE = /^[A-Z]{3}[0-9]{4,6}(?![\s\S])/
const NAME = /^[\p{L}\p{M}\p{N}][\p{L}\p{M}\p{N} .,'’()&+/#:;–—-]*$/u
const CONTROL = /[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F-\u009F]/u
export const normalizeSubjectName = value => String(value ?? '').trim().replace(/ +/g, ' ')
export const normalizeSubjectCode = value => String(value ?? '').trim().toUpperCase()

export function validateSubject(values, courses = [], excludeIndex = -1) {
  const code = String(values.id ?? '')
  const name = normalizeSubjectName(values.name)
  const description = String(values.dept ?? '')
  const topic = normalizeSubjectName(values.t1)
  const errors = {}
  if (!code) errors.id = 'validation.courseIdEmpty'
  else if (!CODE.test(code)) errors.id = 'subject.codeInvalid'
  else if (courses.some((c, i) => i !== excludeIndex && normalizeSubjectCode(c.id) === normalizeSubjectCode(code))) errors.id = 'validation.courseIdTaken'
  if (!name) errors.name = 'validation.courseNameEmpty'
  else if (name.length > 200 || !NAME.test(name)) errors.name = 'subject.nameInvalid'
  if (description.length > 500 || CONTROL.test(description)) errors.dept = 'subject.descriptionInvalid'
  if (!values.edit) {
    if (!topic) errors.t1 = 'subject.topicRequired'
    else if (topic.length > 150 || !NAME.test(topic)) errors.t1 = 'subject.topicInvalid'
  }
  return errors
}
