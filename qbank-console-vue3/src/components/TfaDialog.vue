<script setup>
import { computed, onBeforeUnmount, onMounted } from 'vue'
import BaseModal from './BaseModal.vue'
import { t } from '../i18n/index.js'
import { S, toast, tfaStart, tfaDiscardKey } from '../store.js'
const emit = defineEmits(['close'])
const spaced = computed(() => S.tfaSec.replace(/(.{4})/g, '$1 ').trim())
const left = computed(() => {
  const t = Math.max(0, Math.ceil((S.tfaKeyExpiresAt - S.now) / 1000))
  return String(Math.floor(t / 60)).padStart(2, '0') + ':' + String(t % 60).padStart(2, '0')
})
const copy = async () => { try { if (!navigator.clipboard) throw new Error('Clipboard unavailable'); await navigator.clipboard.writeText(S.tfaSec); toast(t('clipboard.secretCopied'), 'ok') } catch { toast(t('clipboard.blocked'), 'wa') } }
async function go() {
  const done = await tfaStart()
  if (done !== false) emit('close')
}
// Đóng modal khi còn khóa chờ ở Redis -> dọn khóa để không để rác 5 phút (giống ModalActivate2FA).
function cancel() { emit('close'); tfaDiscardKey() }
// Đóng tab / chuyển trang: bắn yêu cầu hủy rồi không chờ (keepalive), y như fe_web_admin.
function onPageHide() { tfaDiscardKey(true) }
onMounted(() => window.addEventListener('pagehide', onPageHide))
onBeforeUnmount(() => window.removeEventListener('pagehide', onPageHide))
</script>
<template>
  <BaseModal :title="$t('tfa.activateTitle')" locked @close="cancel">
    <p class="mut" style="margin:0">{{ $t('tfa.activateSteps') }} <b>{{ $t('tfa.addAccountStep') }}</b> {{ $t('tfa.periodHint', { n: S.tfaPeriod || 30 }) }}</p>
    <div class="tfa-key"><span id="tfa-k">{{ spaced }}</span><button type="button" class="btn sm" data-af @click="copy">{{ $t('common.copy') }}</button></div>
    <p class="mut" style="margin:0;font-size:var(--fs-sm)">{{ $t('llm.keyOnce') }} <b>{{ left }}</b>{{ $t('tfa.activateSteps2') }}</p>
    <template #footer>
      <button class="btn" :disabled="S.tfaBusy" @click="cancel">{{ $t('common.cancel') }}</button>
      <button class="btn pri" :disabled="S.tfaBusy" @click="go">{{ S.tfaBusy ? $t('common.sending') : $t('auth.otpContinue') }}</button>
    </template>
  </BaseModal>
</template>
