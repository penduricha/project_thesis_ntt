<script setup>
import { computed, onBeforeUnmount, onMounted } from 'vue'
import BaseModal from './BaseModal.vue'
import { S, toast, tfaStart, tfaDiscardKey } from '../store.js'
const emit = defineEmits(['close'])
const spaced = computed(() => S.tfaSec.replace(/(.{4})/g, '$1 ').trim())
const left = computed(() => {
  const t = Math.max(0, Math.ceil((S.tfaKeyExpiresAt - S.now) / 1000))
  return String(Math.floor(t / 60)).padStart(2, '0') + ':' + String(t % 60).padStart(2, '0')
})
const copy = () => { navigator.clipboard && navigator.clipboard.writeText(S.tfaSec); toast('Đã sao chép khóa bí mật', 'ok') }
async function go() {
  const done = await tfaStart()
  if (done !== false) emit('close')
}
// Đóng modal khi còn khóa chờ ở Redis -> dọn khóa để không để rác 5 phút (giống ModalActivate2FA).
function cancel() { emit('close'); tfaDiscardKey() }
// Đóng tab / chuyển trang: bắn yêu cầu hủy rồi không chờ (keepalive), y như fe_web_admin.
function onPageHide() { tfaDiscardKey() }
onMounted(() => window.addEventListener('pagehide', onPageHide))
onBeforeUnmount(() => window.removeEventListener('pagehide', onPageHide))
</script>
<template>
  <BaseModal title="Kích hoạt xác thực hai yếu tố" locked @close="cancel">
    <p class="mut" style="margin:0">Mở ứng dụng Authenticator trên điện thoại, chọn <b>Thêm tài khoản</b> rồi nhập khóa bí mật dưới đây (chu kỳ đổi mã {{ S.tfaPeriod || 30 }} giây).</p>
    <div class="tfa-key"><span id="tfa-k">{{ spaced }}</span><button type="button" class="btn sm" data-af @click="copy">Sao chép</button></div>
    <p class="mut" style="margin:0;font-size:var(--fs-sm)">Khóa chỉ hiển thị một lần và hết hạn sau <b>{{ left }}</b>. Sau khi nhập vào ứng dụng, bấm “Tiếp tục” rồi xác nhận trong ứng dụng để hoàn tất.</p>
    <template #footer>
      <button class="btn" :disabled="S.tfaBusy" @click="cancel">Hủy</button>
      <button class="btn pri" :disabled="S.tfaBusy" @click="go">{{ S.tfaBusy ? 'Đang gửi…' : 'Tiếp tục' }}</button>
    </template>
  </BaseModal>
</template>
