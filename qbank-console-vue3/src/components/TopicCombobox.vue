<script setup>
import { computed, nextTick, ref, watch } from 'vue'

// The catalog (including topicIds) comes from the same API-backed store as the
// question editor. Values are indexes only for compatibility with that store.
const props = defineProps({
  course: { type: Object, default: null },
  modelValue: { type: [Number, String], default: 0 },
  uid: { type: String, required: true },
  all: Boolean,
  counts: { type: Array, default: null },
  totalCount: { type: Number, default: null },
  invalid: Boolean,
  disabled: Boolean,
})
const emit = defineEmits(['update:modelValue'])
const input = ref(null)
const open = ref(false), typing = ref(false), active = ref(-1), above = ref(false), menuHeight = ref(256)
const text = ref('')
const norm = value => String(value ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/gi, 'd').toLowerCase()
const selected = computed(() => props.modelValue === '' || props.modelValue == null ? -1 : Number(props.modelValue))
const topics = computed(() => props.course?.topics ?? [])
const selectedLabel = computed(() => topics.value[selected.value] ?? '')
watch([selected, selectedLabel, () => props.course?.subjectId], () => {
  text.value = selectedLabel.value
  typing.value = false
  open.value = false
}, { immediate: true })
const items = computed(() => {
  const query = typing.value ? norm(text.value.trim()) : ''
  const rows = topics.value.map((name, index) => ({ name, index }))
  return query ? rows.filter(item => norm(item.name).includes(query)) : rows
})
const allVisible = computed(() => props.all && !(typing.value && text.value.trim()))
const count = computed(() => items.value.length + (allVisible.value ? 1 : 0))
const activeId = computed(() => active.value < 0 ? undefined : `${props.uid}-option-${active.value}`)

function restore() { text.value = selectedLabel.value; typing.value = false; active.value = -1 }
function close() { open.value = false; restore() }
function position() {
  const field = input.value
  if (!field) return
  const bounds = field.getBoundingClientRect()
  const container = field.closest('.modal-body')?.getBoundingClientRect()
  const top = Math.max(0, container?.top ?? 0)
  const bottom = Math.min(window.innerHeight, container?.bottom ?? window.innerHeight)
  const belowSpace = bottom - bounds.bottom - 6, aboveSpace = bounds.top - top - 6
  above.value = belowSpace < 180 && aboveSpace > belowSpace
  const available = above.value ? aboveSpace : belowSpace
  menuHeight.value = available > 0 ? Math.max(40, Math.min(256, available)) : 256
}
function show() {
  if (props.disabled || (!props.all && !topics.value.length)) return
  position(); open.value = true
}
function toggle() {
  if (open.value) close()
  else { input.value?.focus(); show() }
}
function pick(index) {
  const value = index < 0 ? '' : index
  open.value = false; typing.value = false; active.value = -1
  text.value = index < 0 ? '' : topics.value[index] ?? ''
  emit('update:modelValue', value)
}
function scrollActive() {
  nextTick(() => document.getElementById(activeId.value)?.scrollIntoView?.({ block: 'nearest' }))
}
function key(event) {
  if (event.isComposing || event.keyCode === 229) return
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault(); show()
    if (!count.value) return
    const delta = event.key === 'ArrowDown' ? 1 : -1
    active.value = active.value < 0 ? (delta > 0 ? 0 : count.value - 1) : Math.max(0, Math.min(count.value - 1, active.value + delta))
    scrollActive()
  } else if (event.key === 'Enter' && open.value) {
    event.preventDefault(); event.stopPropagation()
    const i = active.value < 0 ? 0 : active.value
    if (allVisible.value && i === 0) pick(-1)
    else { const item = items.value[i - (allVisible.value ? 1 : 0)]; if (item) pick(item.index) }
  } else if (event.key === 'Escape' && open.value) {
    event.preventDefault(); event.stopPropagation(); close()
  }
}
</script>

<template>
  <div class="cb topic-combobox" data-enter="none">
    <input :id="uid + '-input'" ref="input" class="cb-in" :class="{ invalid }" :value="text"
           role="combobox" :aria-label="$t('course.chapters')" :aria-expanded="String(open)" :aria-controls="uid + '-list'"
           :aria-activedescendant="activeId" :aria-invalid="invalid ? 'true' : undefined" aria-autocomplete="list"
           :placeholder="all ? $t('topicPicker.all') : $t('topicPicker.placeholder')" autocomplete="off"
           :disabled="disabled || (!all && !topics.length)"
           @focus="show(); $event.target.select()" @click="show()"
           @input="text = $event.target.value; typing = true; active = -1; show()" @keydown="key" @blur="close">
    <button type="button" class="cb-btn" tabindex="-1" :aria-label="$t('topicPicker.open')"
            :disabled="disabled || (!all && !topics.length)" @mousedown.prevent @click="toggle"></button>
    <ul :id="uid + '-list'" class="cb-list topic-list" :class="{ above }" :style="{ maxHeight: menuHeight + 'px' }" role="listbox" :hidden="!open">
      <li v-if="allVisible" :id="uid + '-option-0'" role="option" :aria-selected="selected < 0" :class="{ on: selected < 0, act: active === 0 }"
          @mousedown.prevent @click="pick(-1)"><span>{{ $t('topicPicker.all') }}</span><span v-if="totalCount != null" class="topic-count">{{ totalCount }}</span></li>
      <li v-for="(item, i) in items" :id="uid + '-option-' + (i + (allVisible ? 1 : 0))" :key="course?.topicIds?.[item.index] ?? item.index"
          role="option" :aria-selected="item.index === selected" :class="{ on: item.index === selected, act: active === i + (allVisible ? 1 : 0) }"
          @mousedown.prevent @click="pick(item.index)"><span>{{ item.name }}</span><span v-if="counts?.[item.index] != null" class="topic-count">{{ counts[item.index] }}</span></li>
      <li v-if="!items.length && !allVisible" class="none" role="presentation">{{ topics.length ? $t('topicPicker.notFound') : $t('course.noTopic') }}</li>
    </ul>
  </div>
</template>

<style scoped>
.topic-list { overflow-y: auto; overscroll-behavior: contain; }
.topic-list.above { top: auto; bottom: calc(100% + 4px); }
.topic-list li { white-space: normal; overflow-wrap: anywhere; }
.topic-list li[role=option] { display: flex; justify-content: space-between; align-items: baseline; gap: .75rem; }
.topic-count { flex: none; font-size: .8em; opacity: .75; font-variant-numeric: tabular-nums; }
.cb-btn:disabled { opacity: .45; cursor: not-allowed; }
</style>
