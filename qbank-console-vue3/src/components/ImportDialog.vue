<script setup>
import { ref, computed } from 'vue'
import BaseModal from './BaseModal.vue'
import CourseCombobox from './CourseCombobox.vue'
import { LV } from '../data/mock.js'
import { COURSES, S, topicName, importQuestions, log, toast } from '../store.js'
import { parseMoodleXml } from '../services/moodleXml.js'

const emit = defineEmits(['close'])
const xml = ref(''), err = ref(''), chipErr = ref(''), res = ref(null), dirty = ref(false), busy = ref(false)
const crs = computed(() => COURSES[S.imp.c])
const ph = '<quiz>\n  <question type="multichoice">...</question>\n</quiz>'

function pickCourse(v) { S.imp.c = +v; S.imp.tp = 0; chipErr.value = '' }
function onFile(e) {
  const f = e.target.files[0]; if (!f) return
  dirty.value = true
  if (!/\.xml$/i.test(f.name)) { err.value = 'Chỉ nhận tệp có đuôi .xml.'; e.target.value = ''; return }
  err.value = ''
  const r = new FileReader(); r.onload = () => { xml.value = r.result }; r.readAsText(f)
}
function cancel() { if (dirty.value && !confirm('Bạn đang nhập dở dữ liệu. Bạn có chắc muốn đóng và bỏ thay đổi?')) return; emit('close') }
async function save() {
  err.value = chipErr.value = ''; res.value = null
  const p = parseMoodleXml(xml.value)
  if (!p) { err.value = 'XML trống hoặc sai cấu trúc nên chưa thể lưu. Hãy kiểm tra lại các thẻ đóng/mở.'; return }
  if (!crs.value?.topics[S.imp.tp]) { chipErr.value = 'Môn học này chưa có chương. Hãy thêm chương trước khi nhập.'; return }
  busy.value = true
  try {
    const ok = await importQuestions(p.items)
    dirty.value = false
    log(`Nhập Moodle XML: ${ok} thành công, ${p.reasons.length} bỏ qua`, ok ? 'ok' : 'wa')
    if (ok && !p.reasons.length) { emit('close'); return toast(`Đã lưu ${ok} câu vào ${crs.value.id} – ${crs.value.name}`, 'ok') }
    res.value = { ok, reasons: p.reasons }
  } catch { err.value = 'Không lưu được dữ liệu, vui lòng thử lại.' }
  finally { busy.value = false }
}
</script>

<template>
  <BaseModal title="Nhập Moodle XML" :dirty="dirty" @close="emit('close')">
    <label>Gán môn học mặc định <span style="color:var(--danger-strong)">*</span>
      <CourseCombobox uid="imp" :all="false" placeholder="Chọn hoặc gõ để tìm môn học…" :model-value="String(S.imp.c)" @update:model-value="pickCourse" />
    </label>
    <div class="cc-f">Chương / Chủ đề con
      <div id="imp-chips" class="cc-chips" :class="{ invalid: chipErr }">
        <template v-if="crs?.topics.length">
          <small style="flex:1 1 100%">Chương của môn đã chọn ({{ crs.topics.length }}) – bấm để chọn nhanh:</small>
          <button v-for="(x, i) in crs.topics" :key="i" type="button" class="btn sm" :class="{ pri: i == S.imp.tp }" @click="S.imp.tp = i; chipErr = ''">{{ x }}</button>
        </template>
        <small v-else>Môn học này chưa có chương nào.</small>
      </div>
      <small v-if="chipErr" class="fe" role="alert">{{ chipErr }}</small>
    </div>
    <label>Mức độ mặc định
      <select v-model.number="S.imp.l"><option v-for="(x, i) in LV" :key="i" :value="i">{{ x }}</option></select>
    </label>
    <label>Dán trực tiếp mã nguồn XML
      <textarea id="xi" rows="7" v-model="xml" :placeholder="ph" @input="dirty = true; err = ''"></textarea>
    </label>
    <label>Hoặc chọn tệp .xml từ máy tính
      <input type="file" accept=".xml,text/xml" aria-label="Chọn tệp XML" @change="onFile">
    </label>
    <div id="xr">
      <p v-if="err" class="err">{{ err }}</p>
      <template v-if="res">
        <p><span class="b ok">Đã lưu {{ res.ok }} câu</span> <span class="b" :class="res.reasons.length ? 'wa' : 'n'">Bỏ qua {{ res.reasons.length }}</span></p>
        <small>Các câu nhập vào được gán môn <b>{{ crs.id }} – {{ crs.name }}</b> ({{ topicName(S.imp.c, S.imp.tp) }}), mức độ <b>{{ LV[S.imp.l] }}</b>.</small>
        <ul v-if="res.reasons.length" class="iss" style="margin-top:.5rem"><li v-for="(r, i) in res.reasons" :key="i">{{ r }}</li></ul>
      </template>
    </div>
    <template #footer>
      <button type="button" class="btn" @click="cancel">Đóng</button>
      <button type="button" class="btn pri" :disabled="busy" @click="save">{{ busy ? 'Đang lưu…' : 'Kiểm tra cấu trúc và lưu' }}</button>
    </template>
  </BaseModal>
</template>
