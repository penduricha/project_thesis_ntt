<script setup>
defineProps({ ld: Boolean, v: String, badge: String })
</script>
<template>
  <span v-if="ld" class="sk" aria-hidden="true"></span>
  <template v-else-if="v"><span v-if="badge" class="ubd" :class="'ubd--' + badge">{{ v }}</span><template v-else>{{ v }}</template></template>
  <i v-else style="color:var(--text-muted);font-weight:400">Chưa cập nhật</i>
</template>
