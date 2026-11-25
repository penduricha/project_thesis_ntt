<script setup>
import { computed } from 'vue'
const props = defineProps({ d: { type: String, required: true }, sw: { type: [Number, String], default: 2 } })
const inner = computed(() => (props.d.charAt(0) === '<' ? props.d : `<path d="${props.d}"/>`))
</script>
<template>
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" :stroke-width="sw" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" v-html="inner"></svg>
</template>
