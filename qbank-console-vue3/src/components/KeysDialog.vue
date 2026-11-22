<script setup>
import BaseModal from './BaseModal.vue'
const emit = defineEmits(['close'])
</script>
<template>
  <BaseModal title="Phím tắt" @close="emit('close')">
    <dl class="keys">
      <dt><kbd>/</kbd></dt><dd>Tìm kiếm câu hỏi</dd>
      <dt><kbd>n</kbd></dt><dd>Thêm câu hỏi mới</dd>
      <dt><kbd>?</kbd></dt><dd>Mở bảng phím tắt</dd>
      <dt><kbd>Esc</kbd></dt><dd>Đóng hộp thoại, danh sách xổ xuống hoặc menu</dd>
      <dt><kbd>Enter</kbd></dt><dd>Xác nhận ở hộp thoại ngắn (không có ô nhiều dòng)</dd>
      <dt><kbd>Ctrl</kbd> + <kbd>Enter</kbd></dt><dd>Xác nhận ở form có ô nhiều dòng (ví dụ: câu hỏi)</dd>
      <dt><kbd>Ctrl</kbd> + <kbd>Z</kbd></dt><dd>Hoàn tác thao tác vừa xóa (khi thông báo còn hiện)</dd>
      <dt><kbd>↑</kbd> <kbd>↓</kbd></dt><dd>Chọn môn học trong danh sách xổ xuống</dd>
    </dl>
    <template #footer><button class="btn pri" style="grid-column:1/-1" @click="emit('close')">Đóng</button></template>
  </BaseModal>
</template>
