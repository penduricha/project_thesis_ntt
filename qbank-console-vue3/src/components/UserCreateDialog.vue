<script setup>
import { reactive, ref, computed, watch, nextTick } from 'vue'
import BaseModal from './BaseModal.vue'
import { COURSES, S, createUser } from '../store.js'

const emit = defineEmits(['close'])
const f = reactive({ id: '', n: '', cc: '', r: 'Giảng viên', subs: [] }), err = reactive({}), dirty = ref(false)
watch(f, () => { dirty.value = true }, { deep: true })
const active = computed(() => COURSES.filter(c => c.active !== false))
const star = '<span style="color:var(--danger-strong)">*</span>'
function cancel() { if (dirty.value && !confirm('Bạn đang nhập dở dữ liệu. Bạn có chắc muốn đóng và bỏ thay đổi?')) return; emit('close') }
async function save() {
  Object.keys(err).forEach(k => delete err[k])
  const id = f.id.trim(), n = f.n.trim().replace(/\s+/g, ' '), cc = f.cc.trim()
  if (!id) err.id = 'Mã đăng nhập đang trống.'
  else if (!/^\d{1,18}$/.test(id)) err.id = 'Mã đăng nhập chỉ gồm chữ số (tối đa 18 số).'
  else if (S.users.some(u => u.adminId === id)) err.id = 'Mã đăng nhập này đã tồn tại.'
  if (!n) err.n = 'Họ và tên đang trống.'
  if (!cc) err.cc = 'Số CCCD đang trống.'
  else if (!/^\d{12}$/.test(cc)) err.cc = 'Số CCCD phải gồm đúng 12 chữ số.'
  else if (S.users.some(u => u.idCard === cc)) err.cc = 'Số CCCD này đã có tài khoản.'
  if (Object.keys(err).length) { await nextTick(); const e = document.querySelector('#dlg .invalid'); e?.scrollIntoView({ block: 'center' }); e?.focus({ preventScroll: true }); return }
  dirty.value = false; emit('close'); createUser({ id, n, cc, r: f.r, subs: f.r === 'Giảng viên' ? f.subs : [] })
}
</script>
<template>
  <BaseModal title="Thêm tài khoản" :dirty="dirty" @close="emit('close')">
    <label>Mã đăng nhập (admin_id) <span style="color:var(--danger-strong)">*</span>
      <input id="u-id" inputmode="numeric" maxlength="18" autocomplete="off" placeholder="Ví dụ: 2500021780" v-model="f.id" :class="{ invalid: err.id }" :aria-invalid="err.id ? 'true' : null" @input="err.id = ''">
      <small v-if="err.id" class="fe" role="alert">{{ err.id }}</small>
      <small>Mã có sẵn do nhà trường cấp, dùng để đăng nhập.</small>
    </label>
    <label>Họ và tên <span style="color:var(--danger-strong)">*</span>
      <input id="u-n" autocomplete="off" v-model="f.n" :class="{ invalid: err.n }" :aria-invalid="err.n ? 'true' : null" @input="err.n = ''">
      <small v-if="err.n" class="fe" role="alert">{{ err.n }}</small>
    </label>
    <label>Số CCCD (12 chữ số) <span style="color:var(--danger-strong)">*</span>
      <input id="u-cccd" inputmode="numeric" maxlength="12" autocomplete="off" v-model="f.cc" :class="{ invalid: err.cc }" :aria-invalid="err.cc ? 'true' : null" @input="err.cc = ''">
      <small v-if="err.cc" class="fe" role="alert">{{ err.cc }}</small>
    </label>
    <label>Vai trò
      <select id="u-r" v-model="f.r"><option>Giảng viên</option><option>Quản trị viên</option></select>
    </label>
    <fieldset id="u-sub" class="subj-box" :hidden="f.r !== 'Giảng viên'">
      <legend>Môn học được phân công</legend>
      <div class="subj-list"><label v-for="c in active" :key="c.id" class="qall"><input type="checkbox" :value="c.id" v-model="f.subs"> <span>{{ c.name }}</span></label></div>
      <small class="mut">Giảng viên chỉ thấy và thao tác trong các môn được phân công. Quản trị viên xem được tất cả.</small>
    </fieldset>
    <template #footer><button class="btn" @click="cancel">Hủy</button><button class="btn pri" @click="save">Tạo tài khoản</button></template>
  </BaseModal>
</template>
