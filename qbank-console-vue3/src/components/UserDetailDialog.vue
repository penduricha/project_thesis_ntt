<script setup>
import { ref, computed } from 'vue'
import BaseModal from './BaseModal.vue'
import { ACC_ST, TFA_VIEW, ME } from '../data/account.js'
import { COURSES, S, userBy, userUsed, adminCount, saveRole, openDialog, toast } from '../store.js'

const props = defineProps({ adminId: String })
const emit = defineEmits(['close'])
const u = userBy(props.adminId), me = u.adminId === ME.adminId
const v = TFA_VIEW[me ? S.tfa.st : u.totp], acc = ACC_ST[u.status]
const role = ref(u.role), subs = ref([...(u.subjects || [])]), err = ref(''), used = userUsed(u)
const active = computed(() => COURSES.filter(c => c.active !== false))
const copyId = async () => { try { await navigator.clipboard.writeText(String(u.adminId)); toast('Đã sao chép mã đăng nhập', 'ok') } catch { toast('Không thể sao chép tự động. Hãy chọn ID và sao chép thủ công.', 'wa') } }
function save() { err.value = saveRole(u.adminId, role.value, subs.value); if (!err.value) emit('close') }
function del() {
  if (u.role === 'Quản trị viên' && adminCount() < 2) return toast('Không thể xóa quản trị viên cuối cùng', 'wa')
  openDialog('user-confirm', { adminId: u.adminId, mode: 'del' })
}
</script>
<template>
  <BaseModal :title="$t('account.detail')" @close="emit('close')">
    <dl class="udet">
      <div><dt>{{ $t('account.fullName') }}</dt><dd>{{ u.name }}</dd></div>
      <div><dt>{{ $t('auth.loginId') }}</dt><dd class="mono">{{ u.adminId }} <button class="btn-rev" @click="copyId">{{ $t('common.copy') }}</button></dd></div>
      <div><dt>{{ $t('admin.accountStatus') }}</dt><dd><span class="ubd" :class="'ubd--' + (acc ? acc[0] : 'success')">{{ acc ? acc[1] : $t('admin.activeStatus') }}</span></dd></div>
      <div><dt>{{ $t('auth.otpTitle') }}</dt><dd><span class="ubd" :class="'ubd--' + v.bd">{{ v.text }}</span></dd></div>
      <div><dt>{{ $t('account.lastChange') }}</dt><dd>{{ u.passwordChangedAt || $t('common.noPasswordChange') }}</dd></div>
    </dl>
    <div v-if="me" class="own-role"><span>{{ $t('admin.currentRole') }}</span><b>{{ u.role }}</b></div>
    <template v-else>
      <label>{{ $t('common.role') }}
        <select id="ud-r" v-model="role" :class="{ invalid: err }" @change="err = ''"><option>{{ $t('admin.roleAdmin') }}</option><option>{{ $t('admin.roleLecturer') }}</option></select>
        <small v-if="err" class="fe" role="alert">{{ err }}</small>
      </label>
      <fieldset id="ud-sub" class="subj-box" :hidden="role !== $t('admin.roleLecturer')">
        <legend>{{ $t('course.assigned') }}</legend>
        <div class="subj-list"><label v-for="c in active" :key="c.id" class="qall"><input type="checkbox" :value="c.id" v-model="subs"> <span>{{ c.name }}</span></label></div>
      </fieldset>
    </template>
    <div class="directory-secondary">
      <button class="btn" @click="openDialog('user-confirm', { adminId: u.adminId, mode: 'pw' })">{{ $t('admin.resetPassword') }}</button>
      <button v-if="!me" class="btn dan directory-danger" :disabled="used && u.status === 'disabled'" @click="del">{{ used ? $t('admin.disableAccount') : $t('admin.deleteAccount') }}</button>
    </div>
    <template #footer>
      <button class="btn" :class="me ? 'only-action pri' : ''" @click="emit('close')">{{ $t('common.close') }}</button>
      <button v-if="!me" class="btn pri" @click="save">{{ $t('admin.saveRole') }}</button>
    </template>
  </BaseModal>
</template>
