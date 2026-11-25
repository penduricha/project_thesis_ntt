<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { toast } from '../store.js'
import { t } from '../i18n/index.js'
const props = defineProps({ title: String, dirty: Boolean, footerStyle: String, locked: Boolean })
const emit = defineEmits(['close'])
const root = ref(null), opener = document.activeElement
let down = false
function nudge() {
  root.value.classList.add('modal-static'); setTimeout(() => root.value?.classList.remove('modal-static'), 300)
  toast(t('validation.copyBeforeClose'), 'wa')
}
function ask() { if (props.locked) return nudge(); if (props.dirty && !confirm(t('validation.discard'))) return; emit('close') }
function onKey(e) {
  if (e.key === 'Escape') { e.preventDefault(); ask(); return }
  if (e.key !== 'Enter' || e.defaultPrevented || e.isComposing) return
  const t = e.target
  if (t.closest?.('[data-enter="none"]')) return
  const multi = !!root.value.querySelector('.modal-body textarea'), mod = e.ctrlKey || e.metaKey
  const ok = (t.tagName === 'INPUT' && !/^(checkbox|radio|button|submit)$/.test(t.type) && (!multi || mod)) || (t.tagName === 'TEXTAREA' && mod)
  if (!ok) return
  const b = root.value.querySelector('.modal-footer .btn.pri:not(:disabled)')
  if (b) { e.preventDefault(); b.click() }
}
onMounted(() => {
  document.body.classList.add('modal-open')
  const t = root.value.querySelector('[data-af],.modal-body input:not([type=checkbox]):not([type=radio]):not([type=hidden]):not([disabled]),.modal-body textarea,.modal-body select') || root.value.querySelector('.modal-footer .btn')
  t?.focus({ preventScroll: true })
})
onBeforeUnmount(() => {
  document.body.classList.remove('modal-open')
  if (opener && opener !== document.body && document.contains(opener)) opener.focus()
})
</script>

<template>
  <Teleport to="body">
    <div ref="root" class="modal fade show" id="bsm" tabindex="-1" style="display:block" role="dialog" aria-modal="true" aria-labelledby="dlg-t"
         @mousedown.self="down = true" @click.self="down && ask(); down = false" @keydown="onKey">
      <div class="modal-dialog modal-dialog-centered modal-dialog-scrollable">
        <div class="modal-content" id="dlg">
          <div class="modal-header">
            <h3 class="modal-title" id="dlg-t">{{ title }}</h3>
            <button type="button" class="dlg-x" :aria-label="$t('common.close')" @click="ask"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M18 6 6 18M6 6l12 12"/></svg></button>
          </div>
          <div class="modal-body"><slot /></div>
          <div v-if="$slots.footer" class="end modal-footer" :style="footerStyle"><slot name="footer" /></div>
        </div>
      </div>
    </div>
    <div class="modal-backdrop fade show"></div>
  </Teleport>
</template>
