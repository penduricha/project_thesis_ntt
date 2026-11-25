<script setup>
import { reactive, ref, computed, watch, nextTick } from 'vue'
import BaseModal from './BaseModal.vue'
import { t } from '../i18n/index.js'
import { COURSES, S, createUser } from '../store.js'

const emit = defineEmits(['close'])
const f = reactive({ id: '', n: '', cc: '', r: 'Giảng viên', subs: [] }), err = reactive({}), dirty = ref(false)
watch(f, () => { dirty.value = true }, { deep: true })
const active = computed(() => COURSES.filter(c => c.active !== false))
const star = '<span style="color:var(--danger-strong)">*</span>'
function cancel() { if (dirty.value && !confirm(t('validation.discard'))) return; emit('close') }
async function save() {
  Object.keys(err).forEach(k => delete err[k])
  const id = f.id.trim(), n = f.n.trim().replace(/\s+/g, ' '), cc = f.cc.trim()
  if (!id) err.id = 'validation.idEmpty'
  else if (!/^\d{1,18}$/.test(id)) err.id = 'validation.idDigits'
  else if (S.users.some(u => u.adminId === id)) err.id = 'validation.idTaken'
  if (!n) err.n = 'validation.nameEmpty'
  if (!cc) err.cc = 'validation.idCardEmpty'
  else if (!/^\d{12}$/.test(cc)) err.cc = 'validation.idCardDigits'
  else if (S.users.some(u => u.idCard === cc)) err.cc = 'validation.idCardTaken'
  if (Object.keys(err).length) { await nextTick(); const e = document.querySelector('#dlg .invalid'); e?.scrollIntoView({ block: 'center' }); e?.focus({ preventScroll: true }); return }
  dirty.value = false; emit('close'); createUser({ id, n, cc, r: f.r, subs: f.r === 'Giảng viên' ? f.subs : [] })
}
</script>
<template>
  <BaseModal :title="$t('admin.addAccount')" :dirty="dirty" @close="emit('close')">
    <label>{{ $t('admin.adminIdLabel') }} <span style="color:var(--danger-strong)">*</span>
      <input id="u-id" inputmode="numeric" maxlength="18" autocomplete="off" :placeholder="$t('validation.exampleId')" v-model="f.id" :class="{ invalid: err.id }" :aria-invalid="err.id ? 'true' : null" @input="err.id = ''">
      <small v-if="err.id" class="fe" role="alert">{{ $t(err.id) }}</small>
      <small>{{ $t('auth.codeHint') }}</small>
    </label>
    <label>{{ $t('account.fullName') }} <span style="color:var(--danger-strong)">*</span>
      <input id="u-n" autocomplete="off" v-model="f.n" :class="{ invalid: err.n }" :aria-invalid="err.n ? 'true' : null" @input="err.n = ''">
      <small v-if="err.n" class="fe" role="alert">{{ $t(err.n) }}</small>
    </label>
    <label>{{ $t('account.idCardNumber') }} <span style="color:var(--danger-strong)">*</span>
      <input id="u-cccd" inputmode="numeric" maxlength="12" autocomplete="off" v-model="f.cc" :class="{ invalid: err.cc }" :aria-invalid="err.cc ? 'true' : null" @input="err.cc = ''">
      <small v-if="err.cc" class="fe" role="alert">{{ $t(err.cc) }}</small>
    </label>
    <label>{{ $t('common.role') }}
      <select id="u-r" v-model="f.r"><option value="Giảng viên">{{ $t('admin.roleLecturer') }}</option><option value="Quản trị viên">{{ $t('admin.roleAdmin') }}</option></select>
    </label>
    <fieldset id="u-sub" class="subj-box" :hidden="f.r !== 'Giảng viên'">
      <legend>{{ $t('course.assigned') }}</legend>
      <div class="subj-list"><label v-for="c in active" :key="c.id" class="qall"><input type="checkbox" :value="c.id" v-model="f.subs"> <span>{{ c.name }}</span></label></div>
      <small class="mut">{{ $t('course.lecturerScope') }}</small>
    </fieldset>
    <template #footer><button class="btn" @click="cancel">{{ $t('common.cancel') }}</button><button class="btn pri" @click="save">{{ $t('admin.createAccount') }}</button></template>
  </BaseModal>
</template>
