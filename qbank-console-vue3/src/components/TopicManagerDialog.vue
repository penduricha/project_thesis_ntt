<script setup>
import { ref, computed } from 'vue'
import BaseModal from './BaseModal.vue'
import { COURSES, S, log, toast } from '../store.js'
import { t } from '../i18n/index.js'

const props = defineProps({ ci: Number })
const emit = defineEmits(['close'])
const crs = computed(() => COURSES[props.ci])
const name = ref(''), err = ref('')
const clean = v => v.trim().replace(/\s+/g, ' ')
/* So trùng phải chuẩn hoá cả hai phía: tên chương đã lưu có thể còn khoảng trắng thừa. */
const key = v => clean(String(v || '')).toLowerCase()
const same = (a, b) => key(a) === key(b)
const count = idx => S.q.filter(q => q.c === props.ci && q.tp === idx).length

function add() {
  const v = clean(name.value)
  if (!v) { err.value = t('topics.nameRequired'); return }
  if (crs.value.topics.some(t => same(t, v))) { err.value = t('topics.duplicate'); return }
  crs.value.topics.push(v); name.value = ''; err.value = ''
  log(t('topics.logAdd', { name: v, course: crs.value.id }), 'ok'); toast(t('topics.added'), 'ok')
}
function rename(idx, e) {
  const v = clean(e.target.value), t = crs.value.topics
  if (!v) { e.target.value = t[idx]; return toast(t('topics.nameRequired'), 'wa') }
  if (t.some((x, k) => k !== idx && same(x, v))) { e.target.value = t[idx]; return toast(t('topics.duplicate'), 'wa') }
  t[idx] = v; e.target.value = v
  log(t('topics.logRename', { name: v, course: crs.value.id }), 'info'); toast(t('topics.renamed'), 'ok')
}
function remove(idx) {
  const c = crs.value; if (c.topics.length <= 1) return
  const nm = c.topics[idx]
  S.q.forEach(q => { if (q.c === props.ci) { if (q.tp === idx) q.tp = 0; else if (q.tp > idx) q.tp-- } })
  // S.f.tp là CHUỖI (chip gán String(idx)), idx là SỐ -> so sánh thẳng sẽ luôn sai
  if (S.f.tp === String(idx)) S.f.tp = ''
  c.topics.splice(idx, 1)
  log(t('topics.logRemove', { name: nm, course: c.id }), 'wa'); toast(t('topics.removed'), 'wa')
}
</script>

<template>
  <BaseModal :title="$t('topics.title', { id: crs.id, name: crs.name })" footer-style="grid-template-columns:1fr" @close="emit('close')">
    <div class="bar">
      <input id="new-tp-name" class="grow" maxlength="80" :placeholder="$t('course.newTopicPlaceholder')" v-model="name"
             :class="{ invalid: err }" :aria-invalid="err ? 'true' : null" @input="err = ''" @keydown.enter.prevent.stop="add">
      <button type="button" class="btn pri" @click="add">{{ $t('common.add') }}</button>
    </div>
    <small v-if="err" class="fe" role="alert">{{ err }}</small>
    <div class="topic-list-mgr">
      <div v-for="(t, idx) in crs.topics" :key="idx" class="topic-item">
        <input :value="t" :aria-label="$t('course.topicName')" @change="rename(idx, $event)">
        <span class="b n">{{ $t('bank.questionCount', { n: count(idx) }) }}</span>
        <button v-if="crs.topics.length > 1" type="button" class="btn sm del" @click="remove(idx)">{{ $t('common.delete') }}</button>
      </div>
    </div>
    <small>{{ $t('course.deleteTopicWarn') }}</small>
    <template #footer><button type="button" class="btn pri" @click="emit('close')">{{ $t('common.done') }}</button></template>
  </BaseModal>
</template>
