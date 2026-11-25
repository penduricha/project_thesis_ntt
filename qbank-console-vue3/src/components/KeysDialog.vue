<script setup>
import BaseModal from './BaseModal.vue'
const emit = defineEmits(['close'])
</script>
<template>
  <BaseModal :title="$t('dialog.shortcuts')" @close="emit('close')">
    <dl class="keys">
      <dt><kbd>/</kbd></dt><dd>{{ $t('dialog.shortcutSearch') }}</dd>
      <dt><kbd>n</kbd></dt><dd>{{ $t('bank.addNew') }}</dd>
      <dt><kbd>?</kbd></dt><dd>{{ $t('dialog.openShortcuts') }}</dd>
      <dt><kbd>Esc</kbd></dt><dd>{{ $t('dialog.close') }}</dd>
      <dt><kbd>Enter</kbd></dt><dd>{{ $t('dialog.shortConfirm') }}</dd>
      <dt><kbd>Ctrl</kbd> + <kbd>Enter</kbd></dt><dd>{{ $t('dialog.longConfirm') }}</dd>
      <dt><kbd>Ctrl</kbd> + <kbd>Z</kbd></dt><dd>{{ $t('bank.undo') }}</dd>
      <dt><kbd>↑</kbd> <kbd>↓</kbd></dt><dd>{{ $t('course.selectPlaceholder') }}</dd>
    </dl>
    <template #footer><button class="btn pri" style="grid-column:1/-1" @click="emit('close')">{{ $t('common.close') }}</button></template>
  </BaseModal>
</template>
