<script setup>
import { reactive, ref, computed, watch, nextTick } from 'vue'
import BaseModal from './BaseModal.vue'
import CourseCombobox from './CourseCombobox.vue'
import { COURSES, S, code, nowStr, saveQuestion, toast, subjOk } from '../store.js'
import { LV, LV_DB, ENUM_DB } from '../data/mock.js'
import { norm } from '../utils.js'

const props = defineProps({ id: { type: Number, default: 0 }, ctx: { type: Object, default: () => ({}) } })
const emit = defineEmits(['close'])
const cur = props.id ? S.q.find(x => x.id == props.id) : null
const base = cur || { text: '', o: ['', '', '', ''], a: 0, c: props.ctx.c ?? 0, tp: props.ctx.tp ?? 0, l: 1 }
const f = reactive({
  title: base.title || '', text: base.text, o: [...base.o], a: base.a, c: base.c, l: base.l, fb: base.fb || '',
  tp: Math.min(base.tp || 0, Math.max(0, (COURSES[base.c]?.topics.length || 1) - 1)),
})
const err = reactive({}), dirty = ref(false), saving = ref(false), apiErr = ref('')
watch(f, () => { dirty.value = true }, { deep: true })

const dupOpt = computed(() => { const v = f.o.map(x => x.trim().toLowerCase()).filter(Boolean); return new Set(v).size < v.length })
const nstem = t => norm(t).replace(/\s+/g, ' ').trim()
const dupStem = computed(() => {
  const t = nstem(f.text), d = t ? S.q.find(x => x.id != props.id && x.c == f.c && nstem(x.text) == t) : null
  return d ? `Câu dẫn trùng với ${code(d)} trong cùng môn học. Đây chỉ là cảnh báo, bạn vẫn có thể lưu.` : ''
})
/* Quy tắc trùng tiêu đề CHỈ áp cho THÊM MỚI (props.id == 0) — khớp đúng yêu cầu nghiệp vụ
   và đúng phần backend (QuestionService chỉ gọi assertTitleAvailable trong create()).
   KHÔNG được chặn ở form sửa: nếu chặn, những câu đã trùng tiêu đề từ TRƯỚC khi có quy tắc
   (ví dụ CH010 và CH030 cùng tiêu đề "Interface") sẽ vĩnh viễn không sửa được, vì mở câu nào
   cũng bị chính câu kia báo trùng.
   Ở form sửa, trùng tiêu đề vẫn được CẢNH BÁO mềm qua dupTitleWarn — vẫn lưu được. */
const ntitle = t => String(t || '').trim().replace(/\s+/g, ' ').toLowerCase()
const dupTitleWarn = computed(() => {
  const t = ntitle(f.title)
  if (!t) return null                       // tiêu đề không bắt buộc: để trống thì bỏ qua
  return S.q.find(x => x.id != props.id && x.c == f.c && ntitle(x.title) === t) || null
})
const dupTitle = computed(() => (props.id ? null : dupTitleWarn.value))
const dto = computed(() => {
  const c = COURSES[f.c] || {}, o = f.o.map(x => x.trim())
  return { subjectId: c.subjectId ?? null, topicId: c.topicIds?.[f.tp] ?? null, topicName: c.topics?.[f.tp] ?? null, title: f.title.trim() || null, stem: f.text.trim(),
    optionA: o[0], optionB: o[1], optionC: o[2], optionD: o[3], correctLabel: 'ABCD'[f.a], contentFormat: 'plain', generalFeedback: f.fb.trim() || null,
    difficulty: LV_DB[+f.l], status: 'active', validationStatus: ENUM_DB.st[cur && cur.st != 'draft' ? 'stale' : 'draft'], source: cur?.src || 'manual', version: cur?.ver || 1 }
})

function pickCourse(v) { f.c = +v; f.tp = 0; err.c = ''; err.chips = '' }
function cancel() { if (dirty.value && !confirm('Bạn đang nhập dở dữ liệu. Bạn có chắc muốn đóng và bỏ thay đổi?')) return; emit('close') }
function focusFirstInvalid() {
  const e = document.querySelector('#dlg .invalid'); if (!e) return
  const t = e.matches('input,textarea,select') ? e : (e.querySelector('button,input') || e)
  t.scrollIntoView({ block: 'center' }); t.focus({ preventScroll: true })
}
async function save() {
  Object.keys(err).forEach(k => delete err[k]); apiErr.value = ''
  const text = f.text.trim(), o = f.o.map(x => x.trim()), c = COURSES[f.c]
  if (!text) err.t = 'Câu dẫn đang trống. Hãy nhập nội dung câu hỏi.'
  o.forEach((x, i) => { if (!x) err['o' + i] = 'Phương án ' + 'ABCD'[i] + ' đang trống.' })
  if (!c) err.c = 'Hãy chọn môn học trong danh sách.'
  else if (!subjOk(f.c)) err.c = 'Bạn chưa được phân công môn học này.'
  else if (!c.topics[f.tp]) err.chips = 'Môn học này chưa có chương. Hãy thêm chương cho môn học trước khi lưu câu hỏi.'
  // Chỉ chặn khi THÊM MỚI (dupTitle đã trả null ở form sửa nên điều kiện dưới là đủ rõ nghĩa).
  if (!props.id && dupTitle.value) err.ti = `Tiêu đề này đã dùng cho ${code(dupTitle.value)} trong cùng môn. Hãy đặt tiêu đề khác.`
  if (Object.keys(err).length) { await nextTick(); return focusFirstInvalid() }
  if (cur && cur.text === text && cur.o.every((x, i) => x === o[i]) && cur.a === f.a && cur.c === f.c && (cur.tp || 0) === f.tp && cur.l === +f.l && (cur.title || '') === f.title.trim() && (cur.fb || '') === f.fb.trim()) {
    dirty.value = false; emit('close'); return toast('Không có thay đổi nào để lưu')
  }
  saving.value = true
  try {
    await saveQuestion({ text, o, a: f.a, c: f.c, tp: f.tp, l: +f.l, title: f.title.trim(), fb: f.fb.trim(), updatedAt: nowStr(), by: S.name }, props.id)
    dirty.value = false; emit('close')
  } catch { apiErr.value = 'Không lưu được câu hỏi, vui lòng thử lại.' }
  finally { saving.value = false }
}
</script>

<template>
  <BaseModal :title="id ? 'Sửa ' + code({ id }) : 'Thêm câu hỏi mới'" :dirty="dirty" @close="emit('close')">
    <label>Tiêu đề (không bắt buộc, dùng làm tên câu hỏi khi xuất Moodle)
      <input id="f-ti" maxlength="255" v-model="f.title" :class="{ invalid: err.ti }" :aria-invalid="err.ti ? 'true' : null"
             :placeholder="id ? code({ id }) : 'Để trống sẽ dùng mã CHxxx'" autocomplete="off"
             @input="err.ti = ''">
      <small v-if="err.ti" class="fe" role="alert">{{ err.ti }}</small>
      <small v-else-if="dupTitleWarn" class="warn-inline">{{ id ? 'Cảnh báo' : 'Tiêu đề trùng với' }} {{ code(dupTitleWarn) }} trong cùng môn học.{{ id ? ' — vẫn lưu được, nhưng nên đặt tiêu đề khác.' : '' }}</small>
    </label>
    <label>Câu dẫn <span style="color:var(--danger-strong)">*</span>
      <textarea id="f-t" rows="3" maxlength="500" v-model="f.text" :class="{ invalid: err.t }" :aria-invalid="err.t ? 'true' : null" @input="err.t = ''"></textarea>
      <small v-if="err.t" class="fe" role="alert">{{ err.t }}</small>
      <small id="f-len" style="text-align:right;margin-top:.25rem">{{ f.text.length }}/500</small>
      <p id="f-sdup" class="warn-inline">{{ dupStem }}</p>
    </label>
    <div class="opt-list">
      <h4>Bốn phương án – chọn vòng tròn của đáp án đúng <span style="color:var(--danger-strong)">*</span></h4>
      <template v-for="(o, i) in f.o" :key="i">
        <div class="opt">
          <input type="radio" name="ans" :value="i" v-model="f.a" :aria-label="'Đáp án ' + 'ABCD'[i] + ' đúng'"><b>{{ 'ABCD'[i] }}</b>
          <input :id="'f-o' + i" v-model="f.o[i]" :class="{ invalid: err['o' + i] }" :aria-invalid="err['o' + i] ? 'true' : null" @input="err['o' + i] = ''">
        </div>
        <small v-if="err['o' + i]" class="fe" role="alert">{{ err['o' + i] }}</small>
      </template>
      <p id="f-dup" class="warn-inline">{{ dupOpt ? 'Cảnh báo: Có 2 phương án giống nhau – hệ thống AI sẽ gắn cờ lỗi Phương án nhiễu.' : '' }}</p>
    </div>
    <label>Môn học (Course) <span style="color:var(--danger-strong)">*</span>
      <CourseCombobox uid="cf" :all="false" placeholder="Chọn hoặc gõ để tìm môn học…" :invalid="!!err.c" :model-value="String(f.c)" @update:model-value="pickCourse" />
      <small v-if="err.c" class="fe" role="alert">{{ err.c }}</small>
      <small style="margin-top:.25rem">{{ COURSES.length }} môn học. Bấm để xem tất cả hoặc gõ để lọc (không cần dấu).</small>
    </label>
    <div class="cc-f">Chương / Chủ đề con <span style="color:var(--danger-strong)">*</span>
      <div id="cf-chips" class="cc-chips" :class="{ invalid: err.chips }">
        <template v-if="COURSES[f.c]?.topics.length">
          <small style="flex:1 1 100%">Chương của môn đã chọn ({{ COURSES[f.c].topics.length }}) – bấm để chọn nhanh:</small>
          <button v-for="(x, i) in COURSES[f.c].topics" :key="i" type="button" class="btn sm" :class="{ pri: i == f.tp }" @click="f.tp = i; err.chips = ''">{{ x }}</button>
        </template>
        <small v-else>Môn học này chưa có chương nào.</small>
      </div>
      <small v-if="err.chips" class="fe" role="alert">{{ err.chips }}</small>
    </div>
    <label>Mức độ
      <select id="f-l" v-model="f.l"><option v-for="(x, i) in LV" :key="i" :value="i">{{ x }}</option></select>
    </label>
    <label>Phản hồi chung (không bắt buộc)
      <textarea id="f-fb" rows="2" maxlength="500" v-model="f.fb"></textarea>
    </label>
    <details class="api-prev"><summary>Xem dữ liệu gửi Server 1 (POST /api/questions)</summary><pre id="f-api" class="codeblock" style="margin-top:.5rem">{{ JSON.stringify(dto, null, 2) }}</pre></details>
    <p id="f-e" class="err">{{ apiErr }}</p>
    <template #footer>
      <button class="btn" @click="cancel">Hủy</button>
      <button class="btn pri" :disabled="saving" @click="save">{{ saving ? 'Đang lưu…' : 'Lưu câu hỏi' }}</button>
    </template>
  </BaseModal>
</template>
