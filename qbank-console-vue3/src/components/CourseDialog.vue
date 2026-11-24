<script setup>
import { reactive, ref, watch, nextTick } from 'vue'
import BaseModal from './BaseModal.vue'
import { COURSES, addCourse, updateCourse } from '../store.js'

const props = defineProps({ index: { type: Number, default: -1 } })
const emit = defineEmits(['close'])
const edit = props.index >= 0, old = edit ? COURSES[props.index] : null
const f = reactive({ id: old?.id || '', name: old?.name || '', dept: old?.dept || '', t1: 'Chương 1: Tổng quan', active: old ? old.active !== false : true })
const err = reactive({}), dirty = ref(false)
watch(f, () => { dirty.value = true }, { deep: true })
function cancel() { if (dirty.value && !confirm('Bạn đang nhập dở dữ liệu. Bạn có chắc muốn đóng và bỏ thay đổi?')) return; emit('close') }
/* Chuẩn hoá để so trùng: bỏ khoảng trắng hai đầu và gộp khoảng trắng giữa, không phân biệt
   hoa/thường. Phải chuẩn hoá CẢ HAI phía (dữ liệu đã lưu lẫn dữ liệu vừa nhập) — nếu chỉ
   chuẩn hoá phía nhập thì một môn cũ lưu kèm khoảng trắng thừa sẽ lọt qua bước kiểm tra. */
const keyOf = t => String(t || '').trim().replace(/\s+/g, ' ').toLowerCase()
async function submit() {
  Object.keys(err).forEach(k => delete err[k])
  const id = f.id.trim().toUpperCase(), name = f.name.trim().replace(/\s+/g, ' '), others = COURSES.filter((_, i) => i !== props.index)
  if (!id) err.id = 'Mã học phần đang trống.'
  else if (others.some(c => keyOf(c.id) == keyOf(id))) err.id = 'Mã học phần này đã tồn tại.'
  if (!name) err.name = 'Tên môn học đang trống.'
  else if (others.some(c => keyOf(c.name) == keyOf(name))) err.name = 'Tên môn học này đã tồn tại.'
  if (Object.keys(err).length) {
    await nextTick(); const e = document.querySelector('#dlg .invalid'); e?.scrollIntoView({ block: 'center' }); e?.focus({ preventScroll: true }); return
  }
  const d = { id, name, dept: f.dept.trim(), active: f.active }
  edit ? updateCourse(props.index, d) : addCourse({ ...d, topic: f.t1.trim() || 'Chương 1: Tổng quan' })
  dirty.value = false; emit('close')
}
</script>

<template>
  <BaseModal :title="edit ? 'Sửa thông tin môn học' : 'Thêm môn học mới'" :dirty="dirty" @close="emit('close')">
    <label>Mã học phần <span style="color:var(--danger-strong)">*</span>
      <input id="f-cid" maxlength="30" placeholder="Ví dụ: INT3501" autocomplete="off" v-model="f.id" :class="{ invalid: err.id }" :aria-invalid="err.id ? 'true' : null" @input="f.id = f.id.toUpperCase(); err.id = ''">
      <small v-if="err.id" class="fe" role="alert">{{ err.id }}</small>
    </label>
    <label>Tên môn học <span style="color:var(--danger-strong)">*</span>
      <input id="f-cn" maxlength="200" placeholder="Ví dụ: An toàn thông tin" autocomplete="off" v-model="f.name" :data-af="edit ? '' : undefined" :class="{ invalid: err.name }" :aria-invalid="err.name ? 'true' : null" @input="err.name = ''">
      <small v-if="err.name" class="fe" role="alert">{{ err.name }}</small>
    </label>
    <label>Mô tả môn học (không bắt buộc)
      <textarea id="f-cdept" rows="3" maxlength="500" placeholder="Giới thiệu ngắn về nội dung và phạm vi của môn học" v-model="f.dept"></textarea>
      <small id="f-dlen" style="text-align:right;margin-top:.25rem">{{ f.dept.length }}/500</small>
    </label>
    <label v-if="!edit">Chương / Chủ đề đầu tiên
      <input id="f-ct1" maxlength="150" autocomplete="off" v-model="f.t1">
    </label>
    <label class="qall"><input type="checkbox" id="f-cact" v-model="f.active"> Đang sử dụng</label>
    <p id="f-e" class="err"></p>
    <template #footer>
      <button class="btn" @click="cancel">Hủy</button>
      <button class="btn pri" @click="submit">{{ edit ? 'Cập nhật' : 'Lưu môn học' }}</button>
    </template>
  </BaseModal>
</template>
