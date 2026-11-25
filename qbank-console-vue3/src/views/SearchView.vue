<script setup>
import { ref, computed } from 'vue'
import { t } from '../i18n/index.js'
import { useRouter } from 'vue-router'
import { LV } from '../data/mock.js'
import { COURSES, S, code, topicName, editQuestion } from '../store.js'
import { qbankApi, apiErrorMessage } from '../api/qbankApi.js'
import { detailToQuestion } from '../api/mappers.js'
import { resolveSampleQuery } from '../services/searchExamples.js'

const router = useRouter()
const busy = ref(false), res = ref(null)
// Keep the original Vietnamese queries for the existing rule-based parser and backend;
// only the example text shown in the input is translated.
const sampleQueries = [
  ['search.exampleDatabase', 'câu hỏi dễ môn cơ sở dữ liệu'],
  ['search.exampleAi', 'câu hỏi khó môn trí tuệ nhân tạo'],
  ['search.exampleNetwork', 'câu hỏi môn mạng chứa "HTTP"'],
]
const samples = computed(() => sampleQueries.map(([key, query]) => ({ key, query, label: t(key) })))
let selectedSample = null
const RULES = [[/java|spring|int3306/, 0], [/cơ sở dữ liệu|sql|mariadb|redis|int3202/, 1], [/mạng|http|rest|int3304/, 2], [/trí tuệ|llm|transformer|qwen|int3401/, 3], [/triết|bas1150/, 4]]

/* Dùng API thật khi đã đăng nhập thật và dữ liệu đến từ Spring Boot */
const useReal = () => S.dataMode === 'real'

function run(relax = false) {
  const displayed = (S.nl || '').trim(); if (!displayed) return
  const s = resolveSampleQuery(displayed, selectedSample)
  if (!S.colab) { res.value = { off: true }; return }
  busy.value = true
  setTimeout(async () => {
    analyzeRules(s, relax)          // phân tích câu lệnh -> bộ lọc (luôn chạy: cho thấy ý định)
    if (useReal()) await runReal(s, relax)   // nếu có backend thì lấy dữ liệu thật
    busy.value = false
  }, 450)
}

/**
 * Gọi Spring Boot để tìm thật. Backend dùng tên tham số `q` (xem QuestionBankController:
 * @RequestParam(name = "q")), KHÔNG phải `keyword` — gửi sai tên sẽ bị bỏ qua im lặng.
 * Lọc thêm theo môn/độ khó ở phía client vì API hiện chưa nhận `difficulty`.
 */
async function runReal(s, relax) {
  const intent = lastIntent
  try {
    // includeOptions=true: một lượt gọi là đủ 4 phương án, không cần GET /questions/{id} từng câu.
    const params = { q: intent.keyword || s, page: 0, size: 100, includeOptions: true }
    if (intent.course !== undefined) {
      const course = COURSES[intent.course]
      if (course?.subjectId !== null) params.subjectId = course.subjectId
    }
    const page = await qbankApi.listQuestions(params)
    const items = page?.content || []
    let rows = items.map((it) => {
      const detail = {
        questionId: it.questionId, subjectId: it.subjectId, topicId: it.topicId,
        title: it.title, stem: it.stem ?? it.stemPreview ?? '',
        optionA: it.options?.[0] ?? '', optionB: it.options?.[1] ?? '',
        optionC: it.options?.[2] ?? '', optionD: it.options?.[3] ?? '',
        correctLabel: it.correctLabel ?? 'A', contentFormat: it.contentFormat ?? 'plain',
        generalFeedback: it.generalFeedback ?? '', difficulty: it.difficulty,
        status: it.status, validationStatus: it.validationStatus, source: it.source,
        updatedAt: it.updatedAt, updatedBy: null, version: it.version, revisionNo: null,
      }
      const ci = COURSES.findIndex((c) => c.subjectId === detail.subjectId)
      const course = COURSES[ci]
      const tp = course ? Math.max(0, course.topicIds.indexOf(detail.topicId)) : 0
      return detailToQuestion(detail, ci >= 0 ? ci : 0, tp)
    })
    if (intent.level !== undefined) rows = rows.filter((q) => q.l === intent.level)
    res.value = { ...res.value, rows, real: true, total: page?.totalElements ?? rows.length }
    S.nlKw = intent.keyword || s
  } catch (e) {
    res.value = { ...res.value, real: false, apiError: apiErrorMessage(e, t('search.serverFallback')) }
  }
}

let lastIntent = {}

function analyzeRules(s, relax) {
  const n = s.toLowerCase(), F = {}
  RULES.forEach(([r, i]) => { if (F.course === undefined && r.test(n)) F.course = i })
  if (!relax) F.level = /khó/.test(n) ? 2 : /dễ/.test(n) ? 0 : /trung bình/.test(n) ? 1 : undefined
  const m = s.match(/chứa\s+[“"]?([^”"]+)[”"]?/i); if (m) F.keyword = m[1].trim()
  lastIntent = F
  const rows = S.q.filter(q => (F.course === undefined || q.c === F.course) && (F.level === undefined || q.l === F.level) && (!F.keyword || (q.text + q.o.join(' ')).toLowerCase().includes(F.keyword.toLowerCase())))
  S.nlKw = F.keyword || s
  const c = [F.course !== undefined && 'course_id = ?', F.level !== undefined && 'level = ?', F.keyword && 'content LIKE ?'].filter(Boolean)
  const js = { course: F.course === undefined ? null : `${COURSES[F.course].id} – ${COURSES[F.course].name}`, level: F.level === undefined ? null : LV[F.level], keyword: F.keyword || null }
  res.value = { rows, kw: F.keyword || '', hasLevel: F.level !== undefined, json: JSON.stringify(js, null, 2),
    sql: `SELECT id, content, course_id, topic_id, level\nFROM question\nWHERE ${c.join('\n  AND ') || '1 = 1'}` }
}
/* Tô sáng từ khóa bằng mảng đoạn, không cần v-html */
function hl(t, kw) {
  if (!kw) return [{ t, m: false }]
  const re = new RegExp('(' + kw.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + ')', 'gi')
  return t.split(re).map((x, i) => ({ t: x, m: i % 2 === 1 })).filter(x => x.t)
}
const sample = x => { selectedSample = x; S.nl = x.label; run() }
const byKeyword = () => { S.f.q = S.nlKw; S.f.page = 1; router.push('/bank') }
</script>

<template>
  <div class="card">
    <h3>{{ $t('search.askNatural') }}</h3>
    <p class="mut">{{ $t('search.flow') }}</p>
    <div class="bar">
      <input id="nl" class="grow" :aria-label="$t('search.questionLabel')" :placeholder="$t('search.examplePlaceholder')" v-model="S.nl" @keydown.enter="run()">
      <button id="nl-btn" class="btn pri" :disabled="busy" @click="run()"><template v-if="busy"><i class="spin"></i>{{ $t('ai.analyzing') }}</template><template v-else>{{ $t('search.analyzeAndSearch') }}</template></button>
    </div>
    <div class="chips"><button v-for="x in samples" :key="x.key" type="button" class="chip" @click="sample(x)">{{ x.label }}</button></div>
  </div>

  <div id="nlr">
    <div v-if="!res" class="empty-box"><p class="mut">{{ $t('search.placeholder') }} <b>{{ $t('search.step3') }}</b>.</p></div>
    <div v-else-if="res.off" class="card"><p class="err">{{ $t('search.llmDown') }}</p></div>
    <template v-else>
      <div class="cols">
        <div class="card"><h3>{{ $t('search.step1') }}</h3><pre class="codeblock">{{ res.json }}</pre><small>{{ $t('search.allowedFields') }}</small></div>
        <div class="card"><h3>{{ $t('search.step2') }}</h3><pre class="codeblock">{{ res.sql }}</pre><small>{{ $t('search.preparedStatement') }}</small></div>
      </div>
      <div class="card">
        <h3>{{ $t('search.step3Header', { n: res.rows.length }) }}</h3>
        <p v-if="res.real" class="mut" style="margin:.15rem 0 .6rem;font-size:var(--fs-sm)">
          {{ $t('search.fromSpring') }} <code>GET /questions?q=…</code>.
        </p>
        <p v-else-if="res.apiError" class="err" style="margin:.15rem 0 .6rem;font-size:var(--fs-sm)">
          {{ res.apiError }}
        </p>
        <div v-if="res.rows.length" class="tw">
          <table>
            <thead><tr><th>{{ $t('common.code') }}</th><th>{{ $t('bank.content') }}</th><th>{{ $t('course.topics') }}</th><th>{{ $t('bank.level') }}</th></tr></thead>
            <tbody>
              <tr v-for="q in res.rows" :key="q.id" class="clickable" tabindex="0" @click="editQuestion(q.id)" @keydown.enter="editQuestion(q.id)">
                <td><b>{{ code(q) }}</b></td>
                <td><template v-for="(p, i) in hl(q.text, res.kw)" :key="i"><mark v-if="p.m">{{ p.t }}</mark><template v-else>{{ p.t }}</template></template></td>
                <td><b>{{ COURSES[q.c]?.id }}</b> · {{ COURSES[q.c]?.name }}<small>{{ topicName(q.c, q.tp) }}</small></td>
                <td><span class="b" :class="['ok', '', 'er'][q.l]">{{ $t('difficulty.' + q.l) }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="empty-box">
          <p class="mut">{{ $t('search.noMatch') }}</p>
          <div class="bar">
            <button v-if="res.hasLevel" class="btn sm pri" @click="run(true)">{{ $t('ai.difficultyRelax') }}</button>
            <button class="btn sm" @click="byKeyword">{{ $t('search.keywordSearch') }}</button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
