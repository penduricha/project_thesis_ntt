<script setup>
import { ref, computed, watch } from 'vue'
import { COURSES, subjOk } from '../store.js'
const props = defineProps({ modelValue: { type: String, default: '' }, uid: { type: String, default: 'flt' }, all: { type: Boolean, default: true }, placeholder: { type: String, default: 'Tất cả môn học' }, invalid: Boolean })
const emit = defineEmits(['update:modelValue'])
const norm = t => (t || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/gi, 'd').toLowerCase()
const cur = computed(() => (props.modelValue === '' ? -1 : +props.modelValue))
const label = () => COURSES[cur.value]?.name || ''
const text = ref(label()), open = ref(false), typing = ref(false), act = ref(-1), inp = ref(null)
watch(cur, () => { text.value = label(); typing.value = false })
const items = computed(() => {
  const q = typing.value ? norm(text.value.trim()) : ''
  return COURSES.map((c, i) => [c, i]).filter(([c, i]) => subjOk(i) && (props.all || c.active !== false || i === cur.value) && (!q || norm(c.name).includes(q) || norm(c.id).includes(q)))
})
function pick(i) { open.value = false; typing.value = false; text.value = COURSES[i]?.name || ''; emit('update:modelValue', i < 0 ? '' : String(i)) }
function blur() {
  open.value = false
  const t = norm(text.value.trim()); if (!t) { if (props.all) pick(-1); else { text.value = label(); typing.value = false } return }
  const all = COURSES.map((_, i) => i)
  const ex = all.filter(i => norm(COURSES[i].name) == t || norm(COURSES[i].id) == t)
  const u = ex.length ? ex : all.filter(i => norm(COURSES[i].name).includes(t))
  if (u.length == 1) pick(u[0]); else { text.value = label(); typing.value = false }
}
function key(e) {
  const n = items.value.length
  if (e.key == 'ArrowDown') { e.preventDefault(); open.value = true; act.value = Math.min(n - 1, act.value + 1) }
  else if (e.key == 'ArrowUp') { e.preventDefault(); act.value = Math.max(0, act.value - 1) }
  else if (e.key == 'Enter' && open.value) { e.preventDefault(); const t = items.value[act.value] || items.value[0]; if (t) pick(t[1]) }
  else if (e.key == 'Escape' && open.value) { e.preventDefault(); e.stopPropagation(); open.value = false }
}
function toggle() { if (open.value) open.value = false; else { inp.value.focus(); open.value = true } }
</script>

<template>
  <div class="cb" :class="{ 'flt-course': uid == 'flt' }">
    <input ref="inp" :id="uid + '-c'" class="cb-in" :class="{ 'flt-sel': uid == 'flt', invalid }" role="combobox" :aria-expanded="String(open)" :aria-controls="uid + '-lb'"
           aria-autocomplete="list" autocomplete="off" :placeholder="placeholder" v-model="text"
           @focus="$event.target.select(); open = true; typing = false" @click="open = true"
           @input="open = true; typing = true; act = -1" @keydown="key" @blur="blur">
    <button type="button" class="cb-btn" tabindex="-1" aria-label="Mở danh sách môn học" @mousedown.prevent @click="toggle"></button>
    <ul :id="uid + '-lb'" class="cb-list" role="listbox" :hidden="!open" @mousedown.prevent>
      <li v-if="all && !(typing && text.trim())" role="option" :class="{ on: cur < 0 }" @click="pick(-1)">Tất cả môn học</li>
      <li v-for="([c, i], k) in items" :key="c.id" role="option" :class="{ on: i === cur, act: k === act }" @click="pick(i)">{{ c.name }}</li>
      <li v-if="!items.length" class="none">Không tìm thấy môn học phù hợp</li>
    </ul>
  </div>
</template>
