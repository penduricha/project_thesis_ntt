<script setup>
import { reactive, ref, computed, watch, nextTick } from 'vue'
import BaseModal from './BaseModal.vue'
import CourseCombobox from './CourseCombobox.vue'
import { COURSES, S, code, nowStr, saveQuestion, toast, subjOk } from '../store.js'
import { LV, LV_DB, ENUM_DB } from '../data/mock.js'
import { norm } from '../utils.js'

const props = defineProps({ id: { type: Number, default: 0 }, ctx: { type: Object, default: () => ({}) } })
const emit = defineEmits(['close'])
const cur = props.id ? S.q.find(x => x.id === props.id) : null
const base = cur || { text: '', o: ['', '', '', ''], a: 0, c: props.ctx.c ?? 0, tp: props.ctx.tp ?? 0, l: 1 }
const f = reactive({
  title: base.title || '', text: base.text, o: [...base.o], a: base.a, c: base.c, l: base.l, fb: base.fb || '',
  tp: Math.min(base.tp || 0, Math.max(0, (COURSES[base.c]?.topics.length || 1) - 1)),
})
const err = reactive({}), dirty = ref(false), saving = ref(false), apiErr = ref('')
watch(f, () => { dirty.value = true }, { deep: true })

const dupOpt = computed(() => { const v = f.o.map(x => x.trim().toLowerCase()).filter(Boolean); return new Set(v).size < v.length })

/* ===== Kiểm tra trùng lặp CHỈ áp cho form THÊM MỚI =====
   Yêu cầu nghiệp vụ chỉ đặt ra cho câu hỏi thêm mới: "câu hỏi thêm mới phải khác tiêu đề câu
   khác". Form Sửa KHÔNG kiểm tra và KHÔNG cảnh báo trùng lặp với câu khác — sửa nội dung một
   câu có sẵn không tạo ra bản trùng mới, mà lại đang sửa chính câu đó.

   Vì sao trước đây phải nới: những câu đã trùng tiêu đề/câu dẫn từ TRƯỚC khi có quy tắc
   (CH010 & CH030 cùng tiêu đề "Interface") rơi vào thế khoá vĩnh viễn nếu form Sửa chặn —
   mở câu nào cũng bị câu kia chặn. Nhưng hiện cảnh báo mềm ở form Sửa cũng là sai: người dùng
   đang sửa CH003 lại thấy "trùng với CH033" trong khi không hỏi gì về chuyện đó.

   Backend tương ứng: QuestionService chỉ gọi assertTitleAvailable() trong create(). */
const isNew = computed(() => !props.id)
const nstem = t => norm(t).replace(/\s+/g, ' ').trim()
const ntitle = t => String(t || '').trim().replace(/\s+/g, ' ').toLowerCase()

/* Trùng tiêu đề với câu khác trong cùng môn — CHẶN lưu (chỉ form thêm mới). */
const dupTitle = computed(() => {
  if (!isNew.value) return null
  const t = ntitle(f.title)
  if (!t) return null                       // tiêu đề không bắt buộc: để trống thì bỏ qua
  return S.q.find(x => x.c === f.c && ntitle(x.title) === t) || null
})

/* Trùng câu dẫn — chỉ CẢNH BÁO, vẫn lưu được (chỉ form thêm mới). */
const dupStem = computed(() => {
  if (!isNew.value) return null
  const t = nstem(f.text)
  if (!t) return null
  return S.q.find(x => x.c === f.c && nstem(x.text) === t) || null
})
const dto = computed(() => {
  const c = COURSES[f.c] || {}, o = f.o.map(x => x.trim())
  return { subjectId: c.subjectId ?? null, topicId: c.topicIds?.[f.tp] ?? null, topicName: c.topics?.[f.tp] ?? null, title: f.title.trim() || null, stem: f.text.trim(),
    optionA: o[0], optionB: o[1], optionC: o[2], optionD: o[3], correctLabel: 'ABCD'[f.a], contentFormat: 'plain', generalFeedback: f.fb.trim() || null,
    difficulty: LV_DB[+f.l], status: 'active', validationStatus: ENUM_DB.st[cur && cur.st !== 'draft' ? 'stale' : 'draft'], source: cur?.src || 'manual', version: cur?.ver || 1 }
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
  // Chặn trùng tiêu đề — chỉ ở form THÊM MỚI (dupTitle tự trả null ở form sửa).
  if (dupTitle.value) err.ti = `Tiêu đề này đã dùng cho ${code(dupTitle.value)} trong cùng môn. Hãy đặt tiêu đề khác.`
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
  <BaseModal :title="id ? $t('bank.editPrefix') + code({ id }) : $t('bank.addNew')" :dirty="dirty" @close="emit('close')">
    <label>{{ $t('bank.titleOptional') }}
      <input id="f-ti" maxlength="255" v-model="f.title" :class="{ invalid: err.ti }" :aria-invalid="err.ti ? 'true' : null"
             :placeholder="id ? code({ id }) : $t('bank.codeAuto')" autocomplete="off"
             @input="err.ti = ''">
      <small v-if="err.ti" class="fe" role="alert">{{ err.ti }}</small>
    </label>
    <label>{{ $t('bank.lead') }} <span style="color:var(--danger-strong)">*</span>
      <textarea id="f-t" rows="3" maxlength="500" v-model="f.text" :class="{ invalid: err.t }" :aria-invalid="err.t ? 'true' : null" @input="err.t = ''"></textarea>
      <small v-if="err.t" class="fe" role="alert">{{ err.t }}</small>
      <small id="f-len" style="text-align:right;margin-top:.25rem">{{ f.text.length }}/500</small>
      <p v-if="dupStem" id="f-sdup" class="warn-inline">{{ $t('bank.dupStemWarn', { code: code(dupStem) }) }}</p>
    </label>
    <div class="opt-list">
      <h4>{{ $t('bank.options4') }} <span style="color:var(--danger-strong)">*</span></h4>
      <template v-for="(o, i) in f.o" :key="i">
        <div class="opt">
          <input type="radio" name="ans" :value="i" v-model="f.a" :aria-label="$t('bank.answerPrefix') + 'ABCD'[i] + $t('bank.answerCorrect')"><b>{{ 'ABCD'[i] }}</b>
          <input :id="'f-o' + i" v-model="f.o[i]" :class="{ invalid: err['o' + i] }" :aria-invalid="err['o' + i] ? 'true' : null" @input="err['o' + i] = ''">
        </div>
        <small v-if="err['o' + i]" class="fe" role="alert">{{ err['o' + i] }}</small>
      </template>
      <p id="f-dup" class="warn-inline">{{ dupOpt ? $t('bank.dupOptionWarn') : '' }}</p>
    </div>
    <label>{{ $t('course.titleCourse') }} <span style="color:var(--danger-strong)">*</span>
      <CourseCombobox uid="cf" :all="false" :placeholder="$t('course.searchPlaceholder')" :invalid="!!err.c" :model-value="String(f.c)" @update:model-value="pickCourse" />
      <small v-if="err.c" class="fe" role="alert">{{ err.c }}</small>
      <small style="margin-top:.25rem">{{ $t('bank.courseCountHint', { n: COURSES.length }) }}</small>
    </label>
    <div class="cc-f">{{ $t('course.chapters') }} <span style="color:var(--danger-strong)">*</span>
      <div id="cf-chips" class="cc-chips" :class="{ invalid: err.chips }">
        <template v-if="COURSES[f.c]?.topics.length">
          <small style="flex:1 1 100%">{{ $t('course.chapterPickHint', { n: COURSES[f.c].topics.length }) }}</small>
          <button v-for="(x, i) in COURSES[f.c].topics" :key="i" type="button" class="btn sm" :class="{ pri: i === f.tp }" @click="f.tp = i; err.chips = ''">{{ x }}</button>
        </template>
        <small v-else>{{ $t('course.noTopic') }}</small>
      </div>
      <small v-if="err.chips" class="fe" role="alert">{{ err.chips }}</small>
    </div>
    <label>{{ $t('bank.level') }}
      <select id="f-l" v-model="f.l"><option v-for="(x, i) in LV" :key="i" :value="i">{{ x }}</option></select>
    </label>
    <label>{{ $t('llm.responseOptional') }}
      <textarea id="f-fb" rows="2" maxlength="500" v-model="f.fb"></textarea>
    </label>
    <details class="api-prev"><summary>{{ $t('ai.payloadPreview') }}</summary><pre id="f-api" class="codeblock" style="margin-top:.5rem">{{ JSON.stringify(dto, null, 2) }}</pre></details>
    <p id="f-e" class="err">{{ apiErr }}</p>
    <template #footer>
      <button class="btn" @click="cancel">{{ $t('common.cancel') }}</button>
      <button class="btn pri" :disabled="saving" @click="save">{{ saving ? $t('common.saving') : $t('bank.saveQuestion') }}</button>
    </template>
  </BaseModal>
</template>
