<script setup>
import { computed, watch, watchEffect, nextTick, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import CourseCombobox from '../components/CourseCombobox.vue'
import SkeletonList from '../components/SkeletonList.vue'
import { LV, stB } from '../data/mock.js'
import { t, locale } from '../i18n/index.js'
import { COURSES, S, list, code, topicName, busy, runAI, clearFilters, loadQuestions, editQuestion, askDeleteQuestion, openDialog } from '../store.js'

const route = useRoute()
const isCourse = computed(() => route.name === 'course')
const crs = computed(() => COURSES[S.ci])
const L = computed(() => list(isCourse.value))
const perPage = 20
const totalPages = computed(() => Math.max(1, Math.ceil(L.value.length / perPage)))
watchEffect(() => { if (S.f.page > totalPages.value) S.f.page = totalPages.value })
const slice = computed(() => L.value.slice((S.f.page - 1) * perPage, S.f.page * perPage))
const inView = computed(() => L.value.filter(q => S.sel.has(q.id)).length)
const allSel = computed(() => L.value.length > 0 && inView.value === L.value.length)
const partial = computed(() => inView.value > 0 && inView.value < L.value.length)
const courseQ = computed(() => (isCourse.value ? S.q.filter(q => q.c === S.ci) : []))

const chips = computed(() => {
  const f = S.f, a = []
  if (f.q) a.push([t('search.keywordLabel') + ': ' + f.q, () => { f.q = '' }])
  if (!isCourse.value && f.c !== '') a.push([t('nav.courses') + ': ' + COURSES[f.c].id + ' – ' + COURSES[f.c].name, () => { f.c = '' }])
  if (isCourse.value && f.tp !== '') a.push([t('course.chapters') + ': ' + topicName(S.ci, f.tp), () => { f.tp = '' }])
  if (f.l !== '') a.push([t('bank.level') + ': ' + t('difficulty.' + f.l), () => { f.l = '' }])
  if (f.s !== '') a.push([t('common.status') + ': ' + t('questionStatus.' + f.s), () => { f.s = '' }])
  return a
})
const loc = q => {
  const c = COURSES[q.c]
  return isCourse.value ? `· ${topicName(q.c, q.tp)}` : `· ${c ? c.id + ' – ' + c.name : ''} › ${topicName(q.c, q.tp)}`
}
const selAll = on => L.value.forEach(q => (on ? S.sel.add(q.id) : S.sel.delete(q.id)))
const setPage = () => { S.f.page = 1 }
const addQ = () => editQuestion(0, isCourse.value ? { c: S.ci, tp: S.f.tp !== '' ? +S.f.tp : 0, courseImport: true } : {})

/* Đo độ rộng placeholder để ô tìm kiếm không bị cắt chữ (port fitSearch) */
const srch = ref(null)
function fitSearch() {
  const el = srch.value; if (!el) return
  const cs = getComputedStyle(el), c = (fitSearch.c ??= document.createElement('canvas').getContext('2d'))
  c.font = `${cs.fontStyle} ${cs.fontWeight} ${cs.fontSize} ${cs.fontFamily}`
  ;(el.closest('.tb') || el.parentElement).style.setProperty('--srch-w', Math.ceil(c.measureText(el.placeholder).width + parseFloat(cs.paddingLeft) * 2 + 4) + 'px')
}
onMounted(() => { fitSearch(); document.fonts?.ready.then(fitSearch) })
watch(() => locale.code, () => nextTick(fitSearch))
</script>

<template>
  <template v-if="isCourse && crs && S.status === 'ready' && !courseQ.length">
    <div class="subtopic-bar">
      <div class="chips"><span class="mut" style="font-size:var(--fs-sm)">{{ $t('course.subtitle') }} <b>{{ crs.topics.length }}</b> {{ $t('moodle.category') }}</span><span v-for="(t, i) in crs.topics" :key="i" class="b n">{{ t }}</span></div>
      <button type="button" class="btn sm" @click="openDialog('topics', { ci: S.ci })">{{ $t('course.manageTopics') }}</button>
    </div>
    <div class="empty-box"><p class="mut">{{ $t('nav.courses') }} <b>{{ crs.id }} – {{ crs.name }}</b> {{ $t('ai.emptyQuestion') }}</p><button class="btn pri" @click="addQ">{{ $t('bank.addFirst') }}</button></div>
  </template>
  <template v-else>
  <div v-if="isCourse && crs" class="subtopic-bar">
    <div class="chips" style="flex:1">
      <button type="button" class="chip" :class="{ on: S.f.tp === '' }" @click="S.f.tp = ''; setPage()">{{ $t('bank.allTopicsCount') + courseQ.length + ($locale.code === 'zh' || $locale.code === 'ja' ? '）' : ')') }}</button>
      <button v-for="(t, idx) in crs.topics" :key="idx" type="button" class="chip" :class="{ on: S.f.tp === String(idx) }"
              @click="S.f.tp = String(idx); setPage()">{{ t }} ({{ courseQ.filter(q => q.tp === idx).length }})</button>
    </div>
    <button type="button" class="btn sm" @click="openDialog('topics', { ci: S.ci })">{{ $t('bank.manageTopicsCount') + crs.topics.length + ($locale.code === 'zh' || $locale.code === 'ja' ? '）' : ')') }}</button>
  </div>

  <div class="card tools">
    <div class="bar tb" :class="{ 'tb-nc': isCourse }">
      <div class="srch-box">
        <input id="q-srch" ref="srch" :placeholder="$t('bank.searchPlaceholder')" :aria-label="$t('common.search')" :value="S.f.q"
               @input="S.f.q = $event.target.value; setPage()">
        <button type="button" id="q-clr" class="srch-clear" :aria-label="$t('bank.clearKeyword')" v-show="S.f.q" @click="S.f.q = ''; setPage()">×</button>
      </div>
      <CourseCombobox v-if="!isCourse" :model-value="S.f.c" @update:model-value="v => { S.f.c = v; setPage() }" />
      <select class="flt-sel" :aria-label="$t('bank.level')" v-model="S.f.l" @change="setPage">
        <option value="">{{ $t('bank.level') }}</option>
        <option v-for="(x, i) in LV" :key="i" :value="String(i)">{{ $t('difficulty.' + i) }}</option>
      </select>
      <select class="flt-sel" :aria-label="$t('common.status')" v-model="S.f.s" @change="setPage">
        <option value="">{{ $t('common.status') }}</option>
        <option v-for="(v, k) in stB" :key="k" :value="k">{{ $t('questionStatus.' + k) }}</option>
      </select>
      <select class="flt-sel" :aria-label="$t('common.sort')" v-model="S.f.sort">
        <option value="id">{{ $t('common.sort') }}</option>
        <option value="level_asc">{{ $t('ai.fromEasyToHard') }}</option>
        <option value="level">{{ $t('ai.fromHardToEasy') }}</option>
        <option value="recent">{{ $t('common.newest') }}</option>
      </select>
      <button class="btn pri" @click="addQ">{{ $t('bank.addQuestion') }}</button>
    </div>
    <div v-if="chips.length" class="bar">
      <span class="mut" style="font-size:var(--fs-xs)">{{ $t('search.activeFilter') }}</span>
      <span v-for="([lbl, rm], idx) in chips" :key="idx" class="chip-rm">{{ lbl }} <button type="button" :aria-label="$t('search.clearFilter')" @click="rm(); setPage()">×</button></span>
      <button class="btn sm ghost" @click="clearFilters">{{ $t('bank.clearFilters') }}</button>
    </div>
    <div class="bar" style="justify-content:space-between">
      <label class="qall"><input type="checkbox" id="qall-cb" :checked="allSel" :indeterminate.prop="partial" @change="selAll($event.target.checked)"> <span id="qall-lbl">{{ $t('bank.selectAllFiltered') + L.length }}{{ $t('bank.filteredSuffix') }}</span></label>
      <button type="button" class="btn sm" @click="S.f.hideAns = !S.f.hideAns">{{ S.f.hideAns ? $t('bank.showAnswer') : $t('bank.hideAnswer') }}</button>
    </div>
  </div>

  <div class="qwrap" :class="{ 'hide-ans': S.f.hideAns }">
    <div class="qlist" id="tb">
      <!-- Skeleton thay cho spinner: khung đã đúng bố cục hàng thật nên dữ liệu về không bị nhảy. -->
        <SkeletonList v-if="S.status === 'loading' || S.status === 'idle'" :rows="6" :label="$t('loading.questions')" />
      <div v-else-if="S.status === 'error'" class="empty-box"><p class="mut">{{ $t('error.loadFailed') }}</p><button class="btn sm pri" @click="loadQuestions">{{ $t('common.retry') }}</button></div>
      <div v-else-if="!slice.length" class="empty-box">
        <svg viewBox="0 0 24 24"><circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3"/></svg>
        <p class="mut">{{ $t('bank.noMatch') }}</p>
        <button class="btn sm pri" @click="clearFilters">{{ $t('bank.clearFilter') }}</button>
      </div>
      <template v-else>
        <div v-for="q in slice" :key="q.id" class="qrow">
          <div class="qrow__sel"><input type="checkbox" :aria-label="$t('bank.selectPrefix') + code(q)" :checked="S.sel.has(q.id)" @change="$event.target.checked ? S.sel.add(q.id) : S.sel.delete(q.id)"></div>
          <div class="qrow__main">
            <div class="qrow__head">
              <b>{{ code(q) }}</b>
              <span class="b" :class="['ok', '', 'er'][q.l]">{{ $t('difficulty.' + q.l) }}</span>
              <span v-if="busy(q)" class="b wa"><i class="spin"></i>{{ $t('bank.verifying') }}</span>
              <span v-else class="b" :class="stB[q.st][1]">{{ $t('questionStatus.' + q.st) }}</span>
              <span class="mut" style="font-size:var(--fs-xs)">{{ loc(q) }}</span>
              <span class="qrow__meta">{{ $t('bank.updatedMeta', { t: q.updatedAt || '—', who: q.by || $t('bank.systemActor') }) }}</span>
            </div>
            <p class="qrow__text">{{ q.text }}</p>
            <div class="qrow__opts"><span v-for="(o, i) in q.o" :key="i" class="o" :class="{ on: i === q.a }"><i>{{ 'ABCD'[i] }}</i>{{ o }}</span></div>
          </div>
          <div class="acts">
            <button class="btn sm pri" :disabled="busy(q)" @click="runAI([q.id])">{{ $t('ai.validate') }}</button>
            <button class="btn sm" :disabled="busy(q)" @click="editQuestion(q.id)">{{ $t('common.edit') }}</button>
            <button class="btn sm dan" :disabled="busy(q)" @click="askDeleteQuestion(q.id)">{{ $t('common.delete') }}</button>
          </div>
        </div>
        <div v-if="totalPages > 1" class="bar" style="justify-content:center;margin-top:.5rem">
          <button class="btn sm" :disabled="S.f.page <= 1" @click="S.f.page--">{{ $t('common.prev') }}</button>
          <span class="mut" style="font-size:var(--fs-sm)">{{ $t('common.pageNumber', { page: S.f.page, total: totalPages }) }}</span>
          <button class="btn sm" :disabled="S.f.page >= totalPages" @click="S.f.page++">{{ $t('common.next') }}</button>
        </div>
      </template>
    </div>
  </div>
  </template>
</template>
