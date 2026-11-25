<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { LV, LV_DB, ENUM_DB, IC } from '../data/mock.js'
import { COURSES, S, code, topicName, decide, bulkDecide, editQuestion } from '../store.js'
import { diffWords } from '../utils.js'

const router = useRouter()
const types = ['all', 'Chính tả', 'Ngữ pháp', 'Logic', 'Phương án nhiễu']
const items = computed(() => S.sug.filter(s => S.q.some(x => x.id === s.qid)))
const cards = computed(() => items.value
  .filter(s => S.rf === 'all' || s.is.some(i => i.k === S.rf))
  .map(s => {
    const q = S.q.find(x => x.id === s.qid), same = q.text === s.tx
    const json = JSON.stringify({ taskId: s.tid, questionId: code(q), valid: true, issues: s.is.map(i => ({ type: i.k, description: i.d })),
      suggestedText: same ? null : s.tx, suggestedDifficulty: LV_DB[LV.indexOf(s.diff)] || null, severity: ENUM_DB.sev[s.sev] || 'low' }, null, 2)
    return { s, q, same, df: diffWords(q.text, s.tx), json }
  }))
const toggle = (id, on) => (on ? S.rsel.add(id) : S.rsel.delete(id))
</script>

<template>
  <div v-if="!items.length" class="empty-box">
    <p class="mut">{{ $t('ai.noSuggestions') }}</p>
    <button class="btn pri" @click="router.push('/bank')">{{ $t('search.goToBank') }}</button>
  </div>
  <template v-else>
    <div class="card">
      <div class="bar" style="justify-content:space-between">
        <div class="chips">
          <button v-for="t in types" :key="t" type="button" class="chip" :class="{ on: S.rf === t }" @click="S.rf = t">{{ t === 'all' ? $t('bank.allCount') + items.length + ')' : t }}</button>
        </div>
        <div class="bar">
          <span class="mut" style="font-size:var(--fs-xs)">{{ $t('bank.selected') }} {{ S.rsel.size }}</span>
          <button class="btn sm" :disabled="!S.rsel.size" @click="bulkDecide(false)">{{ $t('ai.rejectSelected') }}</button>
          <button class="btn sm pri" :disabled="!S.rsel.size" @click="bulkDecide(true)">{{ $t('ai.acceptSelected') }}</button>
        </div>
      </div>
    </div>

    <div v-for="c in cards" :key="c.s.qid" class="card">
      <div class="bar">
        <input type="checkbox" :aria-label="$t('bank.selectedItemsPrefix', { code: code(c.q) })" :checked="S.rsel.has(c.s.qid)" @change="toggle(c.s.qid, $event.target.checked)">
        <b>{{ code(c.q) }}</b>
        <span class="mut">· {{ COURSES[c.q.c]?.id || '' }} ({{ topicName(c.q.c, c.q.tp) }}){{ $t('ai.taskPrefix') }}<RouterLink to="/tasks" style="color:var(--primary-ink);font-weight:600">{{ c.s.tid }}</RouterLink></span>
        <span class="grow"></span>
        <span class="b" :class="c.s.sev === 'Cao' ? 'er' : 'wa'">{{ $t('ai.severityErrors', { sev: c.s.sev, n: c.s.is.length }) }}</span>
        <span class="b n">{{ $t('ai.suggestedDiff', { d: c.s.diff }) }}</span>
      </div>
      <div class="cmp" :hidden="c.same">
        <div><h4>{{ $t('ai.original') }}</h4><p><template v-for="(w, i) in c.df.oldTok" :key="i"><del v-if="w.k === 'del'" class="diff-del">{{ w.t }}</del><template v-else>{{ w.t }}</template>{{ ' ' }}</template></p></div>
        <div class="new"><h4>{{ $t('ai.suggestion') }}</h4><p><template v-for="(w, i) in c.df.newTok" :key="i"><ins v-if="w.k === 'ins'" class="diff-ins">{{ w.t }}</ins><template v-else>{{ w.t }}</template>{{ ' ' }}</template></p></div>
      </div>
      <div class="qrow__opts"><span v-for="(o, i) in c.q.o" :key="i" class="o" :class="{ on: i === c.q.a }"><i>{{ 'ABCD'[i] }}</i>{{ o }}</span></div>
      <p v-if="c.same" class="warn-inline" style="font-size:var(--fs-sm)">{{ $t('ai.noteNoRewrite') }}</p>
      <ul class="iss"><li v-for="(i, k) in c.s.is" :key="k"><span class="b" :class="IC[i.k]">{{ i.k }}</span> {{ i.d }}</li></ul>
      <details><summary class="mut" style="cursor:pointer;font-size:var(--fs-sm)">{{ $t('search.responseValid') }}</summary><pre class="codeblock" style="margin-top:.5rem">{{ c.json }}</pre></details>
      <small>{{ $t('ai.noteAnswerSafe') }}</small>
      <div class="end">
        <button class="btn" @click="decide(c.s.qid, false)">{{ c.same ? $t('ai.keepOriginalReviewed') : $t('ai.rejectKeepOriginal') }}</button>
        <button v-if="c.same" class="btn pri" @click="editQuestion(c.s.qid)">{{ $t('ai.openEditor') }}</button>
        <button v-else class="btn pri" @click="decide(c.s.qid, true)">{{ $t('ai.accept') }}</button>
      </div>
    </div>
  </template>
</template>
