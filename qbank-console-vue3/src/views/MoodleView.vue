<script setup>
import { ref, computed } from 'vue'
import { COURSES, S, code, topicName, log, toast, openDialog } from '../store.js'
import { buildXml, isValidXml } from '../services/moodleXml.js'

const picked = computed(() => S.q.filter(q => S.sel.has(q.id)))
const meta = ref(null)

function pickAll() {
  const all = () => S.q.forEach(q => S.sel.add(q.id))
  if (S.sel.size && S.sel.size < S.q.length)
    return openDialog('confirm', { title: 'Chọn toàn bộ câu hỏi?', text: `Đang có ${S.sel.size} câu được chọn. Bạn muốn chọn toàn bộ ${S.q.length} câu?`, ok: 'Chọn tất cả', cancel: 'Giữ nguyên', action: all })
  all()
}
function make() {
  if (!picked.value.length) return toast('Chưa chọn câu hỏi nào – hãy chọn ở “Tất cả câu hỏi” hoặc bấm Chọn tất cả', 'wa')
  S.xml = buildXml(picked.value, { COURSES, code, topicName })
  meta.value = { ok: isValidXml(S.xml), n: picked.value.length }
  log(`Tạo gói xuất ${picked.value.length} câu hỏi chuẩn Moodle XML`, 'ok')
}
function download() {
  if (!S.xml) return toast('Hãy bấm “Tạo tệp XML” trước', 'wa')
  const fn = (S.xmlName || '').trim() || `qbank-export-${new Date().toISOString().slice(0, 10)}.xml`
  const a = document.createElement('a'), name = fn.endsWith('.xml') ? fn : fn + '.xml'
  a.href = URL.createObjectURL(new Blob([S.xml], { type: 'application/xml;charset=utf-8' })); a.download = name; a.click()
  setTimeout(() => URL.revokeObjectURL(a.href), 1000)
  log(`Tải xuống tệp ${name}`, 'ok'); toast(`Đã tải xuống ${name}`, 'ok')
}
function copy() {
  if (!S.xml) return toast('Hãy tạo tệp XML trước', 'wa')
  navigator.clipboard?.writeText(S.xml).then(() => toast('Đã sao chép nội dung XML vào bộ nhớ tạm', 'ok'), () => toast('Trình duyệt chặn sao chép tự động – hãy bôi đen khung XML', 'wa'))
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
