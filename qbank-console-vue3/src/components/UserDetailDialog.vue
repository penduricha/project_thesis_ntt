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
  <BaseModal title="Chi tiết tài khoản" @close="emit('close')">
    <dl class="udet">
      <div><dt>Họ và tên</dt><dd>{{ u.name }}</dd></div>
      <div><dt>Mã đăng nhập</dt><dd class="mono">{{ u.adminId }} <button class="btn-rev" @click="copyId">Sao chép</button></dd></div>
      <div><dt>Trạng thái tài khoản</dt><dd><span class="ubd" :class="'ubd--' + (acc ? acc[0] : 'success')">{{ acc ? acc[1] : 'Hoạt động' }}</span></dd></div>
      <div><dt>Xác thực hai yếu tố</dt><dd><span class="ubd" :class="'ubd--' + v.bd">{{ v.text }}</span></dd></div>
      <div><dt>Đổi mật khẩu lần cuối</dt><dd>{{ u.passwordChangedAt || 'Chưa đổi mật khẩu tạm' }}</dd></div>
    </dl>
    <div v-if="me" class="own-role"><span>Vai trò hiện tại</span><b>{{ u.role }}</b></div>
    <template v-else>
      <label>Vai trò
        <select id="ud-r" v-model="role" :class="{ invalid: err }" @change="err = ''"><option>Quản trị viên</option><option>Giảng viên</option></select>
        <small v-if="err" class="fe" role="alert">{{ err }}</small>
      </label>
      <fieldset id="ud-sub" class="subj-box" :hidden="role !== 'Giảng viên'">
        <legend>Môn học được phân công</legend>
        <div class="subj-list"><label v-for="c in active" :key="c.id" class="qall"><input type="checkbox" :value="c.id" v-model="subs"> <span>{{ c.name }}</span></label></div>
      </fieldset>
    </template>
    <div class="directory-secondary">
      <button class="btn" @click="openDialog('user-confirm', { adminId: u.adminId, mode: 'pw' })">Cấp lại mật khẩu</button>
      <button v-if="!me" class="btn dan directory-danger" :disabled="used && u.status === 'disabled'" @click="del">{{ used ? 'Vô hiệu hóa' : 'Xóa tài khoản' }}</button>
    </div>
    <template #footer>
      <button class="btn" :class="me ? 'only-action pri' : ''" @click="emit('close')">Đóng</button>
      <button v-if="!me" class="btn pri" @click="save">Lưu vai trò</button>
    </template>
  </BaseModal>
</template>
