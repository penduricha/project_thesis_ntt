<script setup>
import BaseModal from './BaseModal.vue'
import { userBy, toast } from '../store.js'
const props = defineProps({ adminId: String, pw: String, title: String })
const emit = defineEmits(['close'])
const u = userBy(props.adminId)
const copy = () => { navigator.clipboard && navigator.clipboard.writeText(props.pw); toast('Đã sao chép mật khẩu tạm', 'ok') }
</script>
<template>
  <BaseModal :title="title" locked @close="emit('close')">
    <p class="mut" style="margin:0">{{ $t('admin.tempPassword') }} <b>{{ u?.name }}</b>{{ $t('admin.tempPasswordWarn') }}</p>
    <div class="tfa-key"><span>{{ $t('tempPw.loginId', { id: adminId }) }}</span></div>
    <div class="tfa-key"><span id="pw-t">{{ pw }}</span><button type="button" class="btn sm" data-af @click="copy">{{ $t('common.copy') }}</button></div>
    <template #footer><button class="btn pri" style="grid-column:1/-1" @click="emit('close')">{{ $t('dialog.saved') }}</button></template>
  </BaseModal>
</template>
