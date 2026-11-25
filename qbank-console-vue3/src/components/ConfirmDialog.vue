<script setup>
import BaseModal from './BaseModal.vue'
const props = defineProps({ title: String, text: String, cancel: { type: String, default: '' }, ok: String, danger: Boolean, action: Function })
const emit = defineEmits(['close'])
const run = () => { emit('close'); props.action?.() }
</script>
<template>
  <BaseModal :title="title" @close="emit('close')">
    <p>{{ text }}</p>
    <template #footer>
      <button class="btn" @click="emit('close')">{{ cancel || $t('selection.keep') }}</button>
      <button class="btn" :class="danger ? 'dan' : 'pri'" @click="run">{{ ok }}</button>
    </template>
  </BaseModal>
</template>
