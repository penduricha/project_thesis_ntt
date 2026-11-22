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
    <p class="mut" style="margin:0">Thông tin đăng nhập tạm của <b>{{ u?.name }}</b>. Mật khẩu chỉ hiển thị một lần, hãy gửi cho người dùng qua kênh an toàn. Người dùng phải đổi mật khẩu ở lần đăng nhập đầu tiên.</p>
    <div class="tfa-key"><span>Mã đăng nhập: {{ adminId }}</span></div>
    <div class="tfa-key"><span id="pw-t">{{ pw }}</span><button type="button" class="btn sm" data-af @click="copy">Sao chép</button></div>
    <template #footer><button class="btn pri" style="grid-column:1/-1" @click="emit('close')">Đã lưu lại</button></template>
  </BaseModal>
</template>
