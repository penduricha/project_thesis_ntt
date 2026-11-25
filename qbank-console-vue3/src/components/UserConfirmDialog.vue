<script setup>
import BaseModal from './BaseModal.vue'
import { userBy, userUsed, resetPassword, removeUser } from '../store.js'
const props = defineProps({ adminId: String, mode: String })
const emit = defineEmits(['close'])
const u = userBy(props.adminId), used = props.mode === 'del' && userUsed(u)
function run() { emit('close'); props.mode === 'pw' ? resetPassword(props.adminId) : removeUser(props.adminId) }
</script>
<template>
  <BaseModal :title="mode === 'pw' ? $t('admin.resetPwQ') : (used ? $t('admin.disableAccount') : $t('common.delete')) + $t('admin.accountSuffixQ')" @close="emit('close')">
    <p v-if="mode === 'pw'" class="mut" style="margin:0">{{ $t('account.currentPassword') }} <b>{{ u.name }}</b> {{ $t('account.disableWarn') }}</p>
    <p v-else-if="used" class="mut" style="margin:0">{{ $t('nav.accountMenu') }} <b>{{ u.name }}</b> {{ $t('account.hasDataWarn') }} <b>{{ $t('admin.disableAccount') }}</b> {{ $t('account.disableKeepData') }}</p>
    <p v-else class="mut" style="margin:0">{{ $t('nav.accountMenu') }} <b>{{ u.name }}</b> {{ $t('account.deleteWarn') }}</p>
    <template #footer>
      <button class="btn" @click="emit('close')">{{ $t('common.cancel') }}</button>
      <button class="btn" :class="mode === 'pw' ? 'pri' : 'dan'" @click="run">{{ mode === 'pw' ? $t('admin.resetPwShort') : used ? $t('admin.disableAccount') : $t('admin.deleteAccount') }}</button>
    </template>
  </BaseModal>
</template>
