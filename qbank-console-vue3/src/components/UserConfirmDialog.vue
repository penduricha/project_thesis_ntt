<script setup>
import BaseModal from './BaseModal.vue'
import { userBy, userUsed, resetPassword, removeUser } from '../store.js'
const props = defineProps({ adminId: String, mode: String })
const emit = defineEmits(['close'])
const u = userBy(props.adminId), used = props.mode === 'del' && userUsed(u)
function run() { emit('close'); props.mode === 'pw' ? resetPassword(props.adminId) : removeUser(props.adminId) }
</script>
<template>
  <BaseModal :title="mode === 'pw' ? 'Cấp lại mật khẩu?' : (used ? 'Vô hiệu hóa' : 'Xóa') + ' tài khoản?'" @close="emit('close')">
    <p v-if="mode === 'pw'" class="mut" style="margin:0">Mật khẩu hiện tại của <b>{{ u.name }}</b> sẽ ngừng hoạt động, các phiên đăng nhập đang mở bị thu hồi và hệ thống tạo một mật khẩu tạm mới.</p>
    <p v-else-if="used" class="mut" style="margin:0">Tài khoản <b>{{ u.name }}</b> đã tạo dữ liệu trong ngân hàng câu hỏi nên không thể xóa hẳn (ràng buộc khóa ngoại RESTRICT). Hệ thống sẽ chuyển trạng thái sang <b>Vô hiệu hóa</b> và giữ nguyên dữ liệu.</p>
    <p v-else class="mut" style="margin:0">Tài khoản <b>{{ u.name }}</b> sẽ bị xóa vĩnh viễn cùng thiết bị, khóa TOTP và mã dự phòng liên quan.</p>
    <template #footer>
      <button class="btn" @click="emit('close')">Hủy</button>
      <button class="btn" :class="mode === 'pw' ? 'pri' : 'dan'" @click="run">{{ mode === 'pw' ? 'Cấp lại mật khẩu' : used ? 'Vô hiệu hóa' : 'Xóa tài khoản' }}</button>
    </template>
  </BaseModal>
</template>
