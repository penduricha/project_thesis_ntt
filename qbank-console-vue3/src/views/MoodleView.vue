<script setup>
import { ref, computed } from 'vue'
import { COURSES, S, code, topicName, log, toast, openDialog } from '../store.js'
import { buildXml, isValidXml } from '../services/moodleXml.js'
import { t } from '../i18n/index.js'

const picked = computed(() => S.q.filter(q => S.sel.has(q.id)))
const meta = ref(null)

function pickAll() {
  const all = () => S.q.forEach(q => S.sel.add(q.id))
  if (S.sel.size && S.sel.size < S.q.length)
    return openDialog('confirm', { title: t('export.selectAllTitle'), text: t('export.selectAllConfirm', { selected: S.sel.size, total: S.q.length }), ok: t('common.selectAll'), cancel: t('selection.keep'), action: all })
  all()
}
function make() {
  if (!picked.value.length) return toast(t('export.selectFirst'), 'wa')
  S.xml = buildXml(picked.value, { COURSES, code, topicName })
  meta.value = { ok: isValidXml(S.xml), n: picked.value.length }
  log(t('export.logCreated', { n: picked.value.length }), 'ok')
}
function download() {
  if (!S.xml) return toast(t('export.createFirst'), 'wa')
  const fn = (S.xmlName || '').trim() || `qbank-export-${new Date().toISOString().slice(0, 10)}.xml`
  const a = document.createElement('a'), name = fn.endsWith('.xml') ? fn : fn + '.xml'
  a.href = URL.createObjectURL(new Blob([S.xml], { type: 'application/xml;charset=utf-8' })); a.download = name; a.click()
  setTimeout(() => URL.revokeObjectURL(a.href), 1000)
  log(t('export.logDownload', { name }), 'ok'); toast(t('export.downloaded', { name }), 'ok')
}
function copy() {
  if (!S.xml) return toast(t('export.createFirst'), 'wa')
  navigator.clipboard?.writeText(S.xml).then(() => toast(t('export.copied'), 'ok'), () => toast(t('export.copyBlocked'), 'wa'))
}
</script>

<template>
  <div class="card">
    <h3>{{ $t('bank.exportMoodle') }}</h3>
    <p>{{ $t('bank.selected') }} <b>{{ picked.length }}</b> {{ $t('moodle.exportHint') }} <code>{{ $t('nav.courseTopic') }}</code> {{ $t('moodle.exportCats') }}</p>
    <div v-if="picked.length" class="chips">
      <span v-for="q in picked" :key="q.id" class="chip-rm">{{ code(q) }} <button type="button" :aria-label="$t('moodle.removePrefix') + code(q)" @click="S.sel.delete(q.id)">×</button></span>
    </div>
    <label>{{ $t('moodle.fileName') }}<input id="x-fn" v-model="S.xmlName" :placeholder="$t('moodle.fileNameExample')"></label>
    <div class="btn-grid4">
      <button type="button" class="btn" @click="pickAll">{{ $t('common.selectAll') }}</button>
      <button type="button" class="btn pri" @click="make">{{ $t('moodle.createFile') }}</button>
      <button type="button" class="btn pri" id="x-dl" :disabled="!S.xml || (meta && !meta.ok)" @click="download">{{ $t('moodle.download') }}</button>
      <button type="button" class="btn" @click="copy">{{ $t('common.copy') }}</button>
    </div>
    <div id="xv"><template v-if="meta"><span class="b" :class="meta.ok ? 'ok' : 'er'">{{ meta.ok ? $t('moodle.validXml') : $t('moodle.invalidXml') }}</span> <span class="b n">{{ $t('moodle.exportSummary', { n: meta.n }) }}</span></template></div>
    <pre id="xo" class="codeblock" :class="{ empty: !S.xml }">{{ S.xml || $t('moodle.noFileYet') }}</pre>
  </div>
</template>
