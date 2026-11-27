<script setup>
import { reactive, ref, computed } from 'vue'
import BaseModal from './BaseModal.vue'
import { t } from '../i18n/index.js'
import { COURSES, addCourse, updateCourse } from '../store.js'
import { validateSubject, normalizeSubjectName } from '../services/subjectValidation.js'

const props = defineProps({ index: { type: Number, default: -1 } })
const emit = defineEmits(['close'])
const edit = props.index >= 0, old = edit ? COURSES[props.index] : null
const snapshot = edit ? { subjectId: old?.subjectId, updatedAt: old?.updatedAt } : null
const f = reactive({ id: old?.id || '', name: old?.name || '', dept: old?.dept || '', t1: 'Chương 1: Tổng quan', active: old ? old.active !== false : true })
const touched = reactive({ id: edit, name: false, dept: false, t1: false })
const saving = ref(false), dirty = ref(false), uncertain = ref(false), apiError = ref('')
const excludeIndex = computed(() => !edit ? -1 : snapshot?.subjectId == null ? props.index : COURSES.findIndex(c => c.subjectId === snapshot.subjectId))
const errors = computed(() => validateSubject({ ...f, edit }, COURSES, excludeIndex.value))
const invalid = key => touched[key] && !!errors.value[key]
function onCodeInput(event) { f.id = event.target.value; changed('id') }
function changed(key) { touched[key] = true; dirty.value = true; apiError.value = '' }
function cancel() { if (saving.value) return; if (dirty.value && !confirm(t('validation.discard'))) return; emit('close') }
async function submit() {
  if (saving.value || uncertain.value || Object.keys(errors.value).length) return
  saving.value = true; apiError.value = ''
  try {
    const d = { id: f.id, name: normalizeSubjectName(f.name), dept: f.dept.trim(), active: f.active }
    const result = edit ? await updateCourse(props.index, d, snapshot) : await addCourse({ ...d, topic: normalizeSubjectName(f.t1) })
    if (result?.ok) { dirty.value = false; emit('close') }
    else if (!result?.abandoned) { apiError.value = result?.message || t('subject.saveFailed'); uncertain.value = !!result?.uncertain }
  } catch { apiError.value = t('subject.saveUncertain'); uncertain.value = true }
  finally { saving.value = false }
}
</script>

<template>
  <BaseModal :title="edit ? $t('course.editTitle') : $t('course.addTitle')" :dirty="dirty" :locked="saving" @close="emit('close')">
    <label>{{ $t('course.code') }} <span style="color:var(--danger-strong)">*</span>
      <input id="f-cid" maxlength="30" :placeholder="$t('course.exampleCode')" autocomplete="off" :value="f.id" :disabled="saving" :class="{ invalid: invalid('id') }" :aria-invalid="invalid('id') ? 'true' : null" aria-describedby="subject-code-hint subject-code-error" @input="onCodeInput" @blur="touched.id = true">
      <small id="subject-code-hint">{{ $t('subject.codeHint') }}</small>
      <small v-if="invalid('id')" id="subject-code-error" class="fe" role="alert">{{ $t(errors.id) }}</small>
    </label>
    <label>{{ $t('course.name') }} <span style="color:var(--danger-strong)">*</span>
      <input id="f-cn" maxlength="200" :placeholder="$t('course.exampleName')" autocomplete="off" v-model="f.name" :disabled="saving" :data-af="edit ? '' : undefined" :class="{ invalid: invalid('name') }" :aria-invalid="invalid('name') ? 'true' : null" aria-describedby="subject-name-hint subject-name-error" @input="changed('name')" @blur="touched.name = true">
      <small id="subject-name-hint">{{ $t('subject.nameHint') }}</small>
      <small v-if="invalid('name')" id="subject-name-error" class="fe" role="alert">{{ $t(errors.name) }}</small>
    </label>
    <label>{{ $t('course.description') }}
      <textarea id="f-cdept" rows="3" maxlength="500" :placeholder="$t('course.descPlaceholder')" v-model="f.dept" :disabled="saving" :class="{ invalid: invalid('dept') }" :aria-invalid="invalid('dept') ? 'true' : null" aria-describedby="subject-description-error" @input="changed('dept')" @blur="touched.dept = true"></textarea>
      <small id="f-dlen" style="text-align:right;margin-top:.25rem">{{ f.dept.length }}/500</small>
      <small v-if="invalid('dept')" id="subject-description-error" class="fe" role="alert">{{ $t(errors.dept) }}</small>
    </label>
    <label v-if="!edit">{{ $t('course.firstTopic') }} <span style="color:var(--danger-strong)">*</span>
      <input id="f-ct1" maxlength="150" autocomplete="off" v-model="f.t1" :disabled="saving" :class="{ invalid: invalid('t1') }" :aria-invalid="invalid('t1') ? 'true' : null" aria-describedby="subject-topic-error" @input="changed('t1')" @blur="touched.t1 = true">
      <small v-if="invalid('t1')" id="subject-topic-error" class="fe" role="alert">{{ $t(errors.t1) }}</small>
    </label>
    <label class="qall"><input type="checkbox" id="f-cact" v-model="f.active" :disabled="saving" @change="dirty = true"> {{ $t('tfa.active') }}</label>
    <p v-if="apiError" id="f-e" class="err" role="alert">{{ apiError }}</p>
    <template #footer>
      <button class="btn" :disabled="saving" @click="cancel">{{ $t('common.cancel') }}</button>
      <button class="btn pri" :disabled="saving || uncertain || Object.keys(errors).length > 0" @click="submit">{{ saving ? $t('common.saving') : edit ? $t('course.update') : $t('course.saveCourse') }}</button>
    </template>
  </BaseModal>
</template>
