<script setup>
import BaseModal from './BaseModal.vue'
import { S, toast, tfaStart } from '../store.js'
const emit = defineEmits(['close'])
const spaced = S.tfaSec.replace(/(.{4})/g, '$1 ').trim()
const copy = () => { navigator.clipboard && navigator.clipboard.writeText(S.tfaSec); toast('Đã sao chép khóa bí mật', 'ok') }
const go = () => { emit('close'); tfaStart() }
</script>
<template>
  <BaseModal title="Kích hoạt xác thực hai yếu tố" locked @close="emit('close')">
    <p class="mut" style="margin:0">Mở ứng dụng Authenticator trên điện thoại, chọn <b>Thêm tài khoản</b> rồi nhập khóa bí mật dưới đây.</p>
    <div class="tfa-key"><span id="tfa-k">{{ spaced }}</span><button type="button" class="btn sm" data-af @click="copy">Sao chép</button></div>
    <p class="mut" style="margin:0;font-size:var(--fs-sm)">Khóa chỉ hiển thị một lần. Sau khi nhập vào ứng dụng, bấm “Tiếp tục” và xác nhận trong ứng dụng trong vòng 5 phút.</p>
    <template #footer><button class="btn" @click="emit('close')">Hủy</button><button class="btn pri" @click="go">Tiếp tục</button></template>
  </BaseModal>
</template>
