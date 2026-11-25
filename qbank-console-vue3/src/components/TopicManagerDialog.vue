<script setup>
import { ref, computed } from 'vue'
import BaseModal from './BaseModal.vue'
import { COURSES, S, log, toast } from '../store.js'

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
  if (!v) { err.value = 'Vui lòng nhập tên chương/chủ đề'; return }
  if (crs.value.topics.some(t => same(t, v))) { err.value = 'Tên chương này đã tồn tại trong môn học'; return }
  crs.value.topics.push(v); name.value = ''; err.value = ''
  log(`Thêm chương “${v}” vào môn ${crs.value.id}`, 'ok'); toast('Đã thêm chương mới', 'ok')
}
function rename(idx, e) {
  const v = clean(e.target.value), t = crs.value.topics
  if (!v) { e.target.value = t[idx]; return toast('Tên chương không được để trống', 'wa') }
  if (t.some((x, k) => k !== idx && same(x, v))) { e.target.value = t[idx]; return toast('Tên chương này đã tồn tại trong môn học', 'wa') }
  t[idx] = v; e.target.value = v
  log(`Đổi tên chương thành “${v}” (${crs.value.id})`, 'info'); toast('Đã cập nhật tên chương', 'ok')
}
function remove(idx) {
  const c = crs.value; if (c.topics.length <= 1) return
  const nm = c.topics[idx]
  S.q.forEach(q => { if (q.c === props.ci) { if (q.tp === idx) q.tp = 0; else if (q.tp > idx) q.tp-- } })
  // S.f.tp là CHUỖI (chip gán String(idx)), idx là SỐ -> so sánh thẳng sẽ luôn sai
  if (S.f.tp === String(idx)) S.f.tp = ''
  c.topics.splice(idx, 1)
  log(`Xóa chương “${nm}” (${c.id}), chuyển câu hỏi về chương đầu`, 'wa'); toast('Đã xóa chương và bảo toàn câu hỏi', 'wa')
}
</script>

<template>
  <BaseModal :title="`Chương / Chủ đề của môn ${crs.id} – ${crs.name}`" footer-style="grid-template-columns:1fr" @close="emit('close')">
    <div class="bar">
      <input id="new-tp-name" class="grow" maxlength="80" placeholder="Nhập tên chương / chủ đề mới (VD: Chương 4: Bảo mật)..." v-model="name"
             :class="{ invalid: err }" :aria-invalid="err ? 'true' : null" @input="err = ''" @keydown.enter.prevent.stop="add">
      <button type="button" class="btn pri" @click="add">Thêm</button>
    </div>
    <small v-if="err" class="fe" role="alert">{{ err }}</small>
    <div class="topic-list-mgr">
      <div v-for="(t, idx) in crs.topics" :key="idx" class="topic-item">
        <input :value="t" aria-label="Tên chương" @change="rename(idx, $event)">
        <span class="b n">{{ count(idx) }} câu</span>
        <button v-if="crs.topics.length > 1" type="button" class="btn sm del" @click="remove(idx)">Xóa</button>
      </div>
    </div>
    <small>Lưu ý: Khi xóa một chương, các câu hỏi thuộc chương đó sẽ tự động chuyển về chương đầu tiên của môn học.</small>
    <template #footer><button type="button" class="btn pri" @click="emit('close')">Hoàn tất</button></template>
  </BaseModal>
</template>
