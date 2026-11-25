<script setup>
import BaseModal from './BaseModal.vue'
import { t } from '../i18n/index.js'
import { userBy, toast } from '../store.js'
const props = defineProps({ adminId: String, pw: String, title: String })
const emit = defineEmits(['close'])
const u = userBy(props.adminId)
const copy = async () => { try { if (!navigator.clipboard) throw new Error('Clipboard unavailable'); await navigator.clipboard.writeText(props.pw); toast(t('clipboard.passwordCopied'), 'ok') } catch { toast(t('clipboard.blocked'), 'wa') } }
</script>
<template>
  <BaseModal :title="title" locked @close="emit('close')">
    <p class="mut" style="margin:0">{{ $t('admin.tempPassword') }} <b>{{ u?.name }}</b>{{ $t('admin.tempPasswordWarn') }}</p>
    <div class="tfa-key"><span>{{ $t('tempPw.loginId', { id: adminId }) }}</span></div>
    <div class="tfa-key"><span id="pw-t">{{ pw }}</span><button type="button" class="btn sm" data-af @click="copy">{{ $t('common.copy') }}</button></div>
    <template #footer><button class="btn pri" style="grid-column:1/-1" @click="emit('close')">{{ $t('dialog.saved') }}</button></template>
  </BaseModal>
</template>
