<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { COURSES, subjOk } from '../store.js'
const props = defineProps({ modelValue: { type: String, default: '' }, uid: { type: String, default: 'flt' }, all: { type: Boolean, default: true }, placeholder: { type: String, default: '' }, invalid: Boolean })
const emit = defineEmits(['update:modelValue'])
const norm = t => (t || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/gi, 'd').toLowerCase()
const cur = computed(() => (props.modelValue === '' ? -1 : +props.modelValue))
const label = () => COURSES[cur.value]?.name || ''
const text = ref(label()), open = ref(false), typing = ref(false), act = ref(-1), inp = ref(null)
const above = ref(false), menuHeight = ref(256)
watch([cur, label], () => { text.value = label(); typing.value = false })
function show() {
  const bounds = inp.value?.getBoundingClientRect()
  if (bounds) {
    const container = inp.value.closest('.modal-body')?.getBoundingClientRect()
    const top = Math.max(0, container?.top ?? 0)
    const bottom = Math.min(window.innerHeight, container?.bottom ?? window.innerHeight)
    const below = bottom - bounds.bottom - 6, aboveSpace = bounds.top - top - 6
    above.value = below < 180 && aboveSpace > below
    const available = above.value ? aboveSpace : below
    menuHeight.value = available > 0 ? Math.max(40, Math.min(256, available)) : 256
  }
  open.value = true
}
const items = computed(() => {
  const q = typing.value ? norm(text.value.trim()) : ''
  return COURSES.map((c, i) => [c, i]).filter(([c, i]) => subjOk(i) && (props.all || c.active !== false || i === cur.value) && (!q || norm(c.name).includes(q) || norm(c.id).includes(q)))
})
function pick(i) { open.value = false; typing.value = false; act.value = -1; text.value = COURSES[i]?.name || ''; if (i !== cur.value) emit('update:modelValue', i < 0 ? '' : String(i)) }
function blur() {
  open.value = false
  const t = norm(text.value.trim()); if (!t) { if (props.all) pick(-1); else { text.value = label(); typing.value = false } return }
  const all = COURSES.map((_, i) => i).filter(i => subjOk(i) && (props.all || COURSES[i].active !== false || i === cur.value))
  const ex = all.filter(i => norm(COURSES[i].name) === t || norm(COURSES[i].id) === t)
  const u = ex.length ? ex : all.filter(i => norm(COURSES[i].name).includes(t))
  if (u.length === 1) pick(u[0]); else { text.value = label(); typing.value = false }
}
function key(e) {
  if (e.isComposing || e.keyCode === 229) return
  const n = items.value.length
  if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
    e.preventDefault(); show()
    if (!n) return
    act.value = act.value < 0 ? (e.key === 'ArrowDown' ? 0 : n - 1)
      : Math.max(0, Math.min(n - 1, act.value + (e.key === 'ArrowDown' ? 1 : -1)))
    nextTick(() => document.getElementById(`${props.uid}-option-${act.value}`)?.scrollIntoView?.({ block: 'nearest' }))
  }
  else if (e.key === 'Enter' && open.value) { e.preventDefault(); e.stopPropagation(); const t = items.value[act.value] || items.value[0]; if (t) pick(t[1]) }
  else if (e.key === 'Escape' && open.value) { e.preventDefault(); e.stopPropagation(); open.value = false; text.value = label(); typing.value = false }
}
function toggle() { if (open.value) open.value = false; else { inp.value.focus(); show() } }
</script>

<template>
  <div class="cb" :class="{ 'flt-course': uid === 'flt' }" data-enter="none">
    <input ref="inp" :id="uid + '-c'" class="cb-in" :class="{ 'flt-sel': uid === 'flt', invalid }" role="combobox" :aria-expanded="String(open)" :aria-controls="uid + '-lb'"
           aria-autocomplete="list" :aria-activedescendant="open && act >= 0 ? uid + '-option-' + act : undefined" :aria-invalid="invalid ? 'true' : undefined" autocomplete="off" :placeholder="placeholder || $t('bank.allCourses')" v-model="text"
           @focus="$event.target.select(); show(); typing = false" @click="show()"
           @input="typing = true; act = -1; show()" @keydown="key" @blur="blur">
    <button type="button" class="cb-btn" tabindex="-1" :aria-label="$t('dialog.openCourses')" @mousedown.prevent @click="toggle"></button>
    <ul :id="uid + '-lb'" class="cb-list course-list" :class="{ above }" :style="{ maxHeight: menuHeight + 'px' }" role="listbox" :hidden="!open">
      <li v-if="all && !(typing && text.trim())" role="option" :aria-selected="cur < 0" :class="{ on: cur < 0 }" @mousedown.prevent @click.stop="pick(-1)">{{ $t('bank.allCourses') }}</li>
      <li v-for="([c, i], k) in items" :id="uid + '-option-' + k" :key="c.subjectId ?? c.id" role="option" :aria-selected="i === cur" :class="{ on: i === cur, act: k === act }" @mousedown.prevent @click.stop="pick(i)">{{ c.name }}</li>
      <li v-if="!items.length" class="none">{{ $t('course.notFound') }}</li>
    </ul>
  </div>
</template>

<style scoped>
.course-list.above { top: auto; bottom: calc(100% + 4px); }
.course-list li { white-space: normal; overflow-wrap: anywhere; }
</style>
