<script setup>
import { reactive, ref, computed, watch, nextTick } from 'vue'
import BaseModal from './BaseModal.vue'
import { t } from '../i18n/index.js'
import CourseCombobox from './CourseCombobox.vue'
import CourseQuestionImport from './CourseQuestionImport.vue'
import { COURSES, S, code, nowStr, saveQuestion, toast, subjOk } from '../store.js'
import { LV } from '../data/mock.js'
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
const mode = ref('manual'), importBusy = ref(false), importDirty = ref(false), importPanel = ref(null)
function setMode(value) {
  if (value === mode.value || saving.value || importBusy.value) return
  if ((mode.value === 'manual' ? dirty.value : importDirty.value) && !confirm(t('validation.discard'))) return
  mode.value = value
  if (value === 'manual') importDirty.value = false
}
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

function pickCourse(v) { f.c = +v; f.tp = 0; err.c = ''; err.chips = '' }
function cancel() { if (dirty.value && !confirm(t('validation.discard'))) return; emit('close') }
function focusFirstInvalid() {
  const e = document.querySelector('#dlg .invalid'); if (!e) return
  const t = e.matches('input,textarea,select') ? e : (e.querySelector('button,input') || e)
  t.scrollIntoView({ block: 'center' }); t.focus({ preventScroll: true })
}
async function save() {
  Object.keys(err).forEach(k => delete err[k]); apiErr.value = ''
  const text = f.text.trim(), o = f.o.map(x => x.trim()), c = COURSES[f.c]
  if (!text) err.t = t('validation.stemEmpty')
  o.forEach((x, i) => { if (!x) err['o' + i] = t('validation.optionEmpty', { option: 'ABCD'[i] }) })
  if (!c) err.c = t('validation.selectCourse')
  else if (!subjOk(f.c)) err.c = t('validation.courseScope')
  else if (!c.topics[f.tp]) err.chips = t('validation.noChapter')
  // Chặn trùng tiêu đề — chỉ ở form THÊM MỚI (dupTitle tự trả null ở form sửa).
  if (dupTitle.value) err.ti = t('validation.titleTaken', { code: code(dupTitle.value) })
  if (Object.keys(err).length) { await nextTick(); return focusFirstInvalid() }
  if (cur && cur.text === text && cur.o.every((x, i) => x === o[i]) && cur.a === f.a && cur.c === f.c && (cur.tp || 0) === f.tp && cur.l === +f.l && (cur.title || '') === f.title.trim() && (cur.fb || '') === f.fb.trim()) {
    dirty.value = false; emit('close'); return toast(t('validation.noChanges'))
  }
  saving.value = true
  try {
    await saveQuestion({ text, o, a: f.a, c: f.c, tp: f.tp, l: +f.l, title: f.title.trim(), fb: f.fb.trim(), updatedAt: nowStr(), by: S.name }, props.id)
    dirty.value = false; emit('close')
  } catch { apiErr.value = t('validation.saveQuestionFailed') }
  finally { saving.value = false }
}
</script>

<template>
  <BaseModal :title="id ? $t('bank.editPrefix') + code({ id }) : $t('bank.addNew')" :dirty="mode === 'manual' ? dirty : importDirty" :locked="importBusy || saving" @close="emit('close')">
    <div v-if="!id && ctx.courseImport" class="import-mode" role="group" :aria-label="$t('courseImport.mode')">
      <button type="button" class="btn sm" :class="{ pri: mode === 'manual' }" :aria-pressed="mode === 'manual'" :disabled="importBusy || saving" @click="setMode('manual')">{{ $t('courseImport.manual') }}</button>
      <button type="button" class="btn sm" :class="{ pri: mode === 'import' }" :aria-pressed="mode === 'import'" :disabled="importBusy || saving" @click="setMode('import')">{{ $t('courseImport.mode') }}</button>
    </div>
    <CourseQuestionImport v-if="mode === 'import'" ref="importPanel" :course-index="ctx.c" :initial-topic="ctx.tp ?? 0" @busy="importBusy = $event" @dirty="importDirty = $event" />
    <template v-else>
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
    <div class="cc-f">{{ $t('course.chapters') }}
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
      <select id="f-l" v-model="f.l"><option v-for="(x, i) in LV" :key="i" :value="i">{{ $t('difficulty.' + i) }}</option></select>
    </label>
    <label>{{ $t('llm.responseOptional') }}
      <textarea id="f-fb" rows="2" maxlength="500" v-model="f.fb"></textarea>
    </label>
    <p id="f-e" class="err">{{ apiErr }}</p>
    </template>
    <template #footer>
      <button v-if="mode === 'import'" type="button" class="btn pri only-action"
              :disabled="importBusy || !importPanel?.canSave" @click="importPanel?.save()">
        {{ importBusy ? $t('courseImport.saving') : $t('courseImport.submit') }}
      </button>
      <template v-else>
      <button class="btn" @click="cancel">{{ $t('common.cancel') }}</button>
      <button class="btn pri" :disabled="saving" @click="save">{{ saving ? $t('common.saving') : $t('bank.saveQuestion') }}</button>
      </template>
    </template>
  </BaseModal>
</template>

<style scoped>
.import-mode { display: flex; gap: .5rem; flex-wrap: wrap; margin-bottom: 1rem; }
</style>
