<script setup>
import { computed, ref } from 'vue'
import { COURSES, S, subjOk, useRealApi, loadDashboard } from '../store.js'
import { qbankApi } from '../api/qbankApi.js'
import { detailToQuestion, LV_DB } from '../api/mappers.js'
import { t } from '../i18n/index.js'
import { parseQuestionFile, submitCourseQuestions } from '../services/courseQuestionImport.js'

const props = defineProps({ courseIndex: { type: Number, required: true }, initialTopic: { type: Number, default: 0 } })
const emit = defineEmits(['busy', 'dirty'])
const course = computed(() => COURSES[props.courseIndex])
const topic = ref(props.initialTopic), level = ref(1), fileName = ref(''), parsed = ref(null)
const error = ref(null), busy = ref(false), parsing = ref(false), result = ref(null)
let fileSequence = 0
const canSave = computed(() => !busy.value && !parsing.value && !result.value && parsed.value?.items.length && !parsed.value.errors.length && course.value?.topicIds?.[topic.value] != null && useRealApi() && S.dataMode === 'real' && subjOk(props.courseIndex))
const jsonExample = '[\n  {"stem":"Question?","optionA":"A","optionB":"B","optionC":"C","optionD":"D","correctLabel":"A","title":"","generalFeedback":""}\n]'

async function selectFile(event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (result.value && !confirm(t('courseImport.replaceAfterResult'))) { event.target.value = ''; return }
  const sequence = ++fileSequence
  fileName.value = file.name; parsed.value = null; error.value = null; result.value = null
  parsing.value = true; emit('dirty', true)
  try { const value = await parseQuestionFile(file); if (sequence === fileSequence) parsed.value = value }
  catch (e) { if (sequence === fileSequence) error.value = e?.key ? e : { key: 'courseImport.invalidFile', params: {} } }
  finally { if (sequence === fileSequence) parsing.value = false; event.target.value = '' }
}
async function save() {
  if (!canSave.value) return
  const c = course.value, ci = props.courseIndex, tp = topic.value
  const subjectId = c.subjectId, topicId = c.topicIds?.[tp]
  if (subjectId == null || topicId == null) { error.value = { key: 'courseImport.noTopic', params: {} }; return }
  const rows = parsed.value.items
  const existingTitles = new Set(S.q.filter(q => q.c === ci && q.title).map(q => q.title.trim().replace(/\s+/g, ' ').toLowerCase()))
  const duplicate = rows.findIndex(row => row.title && existingTitles.has(row.title.replace(/\s+/g, ' ').toLowerCase()))
  if (duplicate >= 0) { error.value = { key: 'courseImport.existingTitle', params: { n: duplicate + 1 } }; return }
  busy.value = true; emit('busy', true); error.value = null
  try {
    result.value = await submitCourseQuestions(rows, {
      subjectId, topicId, difficulty: LV_DB[level.value], importBatch: body => qbankApi.importQuestions(body),
      onSaved: response => { S.q.push(detailToQuestion(response, ci, tp)) },
    })
    if (result.value.saved) await loadDashboard()
    if (!result.value.failedAt && !result.value.localUpdateFailure && !result.value.batchUncertain && !result.value.batchRejected) emit('dirty', false)
  } finally { busy.value = false; emit('busy', false) }
}
defineExpose({ canSave, save })
</script>

<template>
  <section class="course-import" data-enter="none">
    <p>{{ $t('courseImport.destination', { course: course ? `${course.id} – ${course.name}` : '' }) }}</p>
    <p class="mut">{{ $t('courseImport.scopeHint') }}</p>
    <label>{{ $t('course.chapters') }}
      <select v-model.number="topic" :disabled="busy || !!result">
        <option v-for="(name, index) in course?.topics || []" :key="index" :value="index">{{ name }}</option>
      </select>
    </label>
    <p v-if="!course?.topics?.length || course?.topicIds?.[topic] == null" class="err">{{ $t('courseImport.noTopic') }}</p>
    <label>{{ $t('bank.defaultLevel') }}
      <select v-model.number="level" :disabled="busy || !!result">
        <option v-for="i in [0, 1, 2]" :key="i" :value="i">{{ $t('difficulty.' + i) }}</option>
      </select>
    </label>
    <label>{{ $t('courseImport.file') }}
      <input type="file" accept=".xml,.csv,.xlsx,.json" :disabled="busy || parsing" @change="selectFile">
    </label>
    <small>{{ $t('courseImport.limits') }}</small>
    <details class="import-help">
      <summary>{{ $t('courseImport.formatTitle') }}</summary>
      <p>{{ $t('courseImport.formatText') }}</p>
      <pre class="codeblock">stem,optionA,optionB,optionC,optionD,correctLabel,title,generalFeedback</pre>
      <p>{{ $t('courseImport.jsonFormat') }}</p>
      <pre class="codeblock">{{ jsonExample }}</pre>
      <p>{{ $t('courseImport.xmlFormat') }}</p>
      <p>{{ $t('courseImport.formatRestrictions') }}</p>
    </details>
    <p v-if="parsing" role="status">{{ $t('courseImport.reading') }}</p>
    <p v-if="error" class="err" role="alert">{{ $t(error.key, error.params) }}</p>
    <template v-if="parsed">
      <p><strong>{{ fileName }}</strong> · {{ $t('courseImport.count', { n: parsed.items.length }) }}</p>
      <div v-if="parsed.errors.length" class="import-errors" role="alert">
        <p>{{ $t('courseImport.fixErrors', { n: parsed.errors.length }) }}</p>
        <ul><li v-for="(item, i) in parsed.errors" :key="i">{{ $t(item.key, item.params) }}</li></ul>
      </div>
      <template v-else>
        <p>{{ $t('courseImport.previewHint') }}</p>
        <ol class="import-preview">
          <li v-for="(item, i) in parsed.items.slice(0, 5)" :key="i">
            <strong>{{ item.stem }}</strong>
            <span>{{ $t('courseImport.correctPreview', { label: item.correctLabel, text: item['option' + item.correctLabel] }) }}</span>
          </li>
        </ol>
      </template>
    </template>
    <p v-if="!useRealApi() || S.dataMode !== 'real'" class="err">{{ $t('courseImport.realRequired') }}</p>
    <div v-if="result" role="status" class="import-result">
      <p v-if="result.saved != null">{{ $t('courseImport.saved', { n: result.saved, total: parsed.items.length }) }}</p>
      <p v-if="result.batchUncertain" class="err">{{ $t('courseImport.batchUncertain') }}</p>
      <p v-if="result.batchRejected" class="err">{{ $t('courseImport.batchRejected', { status: result.status }) }}</p>
      <p v-if="result.errorCode">{{ $t('courseImport.serverCode', { code: result.errorCode }) }}</p>
      <p v-if="result.failedAt" class="err">{{ $t(result.uncertain ? 'courseImport.uncertain' : 'courseImport.rejected', { n: result.failedAt, status: result.status || '' }) }}</p>
      <p v-if="result.failedAt">{{ $t('courseImport.stopped') }}</p>
      <p v-if="result.localUpdateFailure" class="err">{{ $t('courseImport.refreshNeeded') }}</p>
    </div>
  </section>
</template>

<style scoped>
.course-import { display: grid; gap: .8rem; }
.course-import p { margin: 0; }
.course-import label { display: grid; gap: .3rem; }
.import-help { padding: .75rem; border: 1px solid var(--border); border-radius: .5rem; }
.import-help summary { cursor: pointer; font-weight: 600; }
.import-help p { margin: .6rem 0; }
.import-help pre { overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; }
.import-errors, .import-result { padding: .75rem; border: 1px solid var(--border); border-radius: .5rem; }
.import-errors ul { max-height: 12rem; overflow: auto; margin-bottom: 0; }
.import-preview { display: grid; gap: .5rem; margin: 0; padding-left: 1.5rem; }
.import-preview li { overflow-wrap: anywhere; }
.import-preview span { display: block; color: var(--text-muted); font-size: .9em; }
</style>
