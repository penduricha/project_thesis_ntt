<script setup>
import { ref, computed } from 'vue'
import BaseModal from './BaseModal.vue'
import CourseCombobox from './CourseCombobox.vue'
import TopicCombobox from './TopicCombobox.vue'
import { LV } from '../data/mock.js'
import { COURSES, S, topicName, importQuestions, log, toast } from '../store.js'
import { parseMoodleXml } from '../services/moodleXml.js'
import { t } from '../i18n/index.js'

const emit = defineEmits(['close'])
const xml = ref(''), err = ref(''), chipErr = ref(''), res = ref(null), dirty = ref(false), busy = ref(false)
const crs = computed(() => COURSES[S.imp.c])
const ph = '<quiz>\n  <question type="multichoice">...</question>\n</quiz>'

function pickCourse(v) { const next = +v; if (next === S.imp.c) return; S.imp.c = next; S.imp.tp = 0; chipErr.value = '' }
function onFile(e) {
  const f = e.target.files[0]; if (!f) return
  dirty.value = true
  if (!/\.xml$/i.test(f.name)) { err.value = t('importXml.extension'); e.target.value = ''; return }
  err.value = ''
  const r = new FileReader(); r.onload = () => { xml.value = r.result }; r.readAsText(f)
}
function cancel() { if (dirty.value && !confirm(t('validation.discard'))) return; emit('close') }
async function save() {
  err.value = chipErr.value = ''; res.value = null
  const p = parseMoodleXml(xml.value)
  if (!p) { err.value = t('importXml.invalid'); return }
  if (!crs.value?.topics[S.imp.tp]) { chipErr.value = t('importXml.noChapter'); return }
  busy.value = true
  try {
    const ok = await importQuestions(p.items)
    dirty.value = false
    log(t('importXml.logResult', { saved: ok, skipped: p.reasons.length }), ok ? 'ok' : 'wa')
    if (ok && !p.reasons.length) { emit('close'); return toast(t('importXml.savedTo', { n: ok, id: crs.value.id, name: crs.value.name }), 'ok') }
    res.value = { ok, reasons: p.reasons }
  } catch { err.value = t('importXml.saveFailed') }
  finally { busy.value = false }
}
</script>

<template>
  <BaseModal :title="$t('bank.importMoodle')" :dirty="dirty" @close="emit('close')">
    <label>{{ $t('admin.assignedSubjects') }} <span style="color:var(--danger-strong)">*</span>
      <CourseCombobox uid="imp" :all="false" :placeholder="$t('course.searchPlaceholder')" :model-value="String(S.imp.c)" @update:model-value="pickCourse" />
    </label>
    <div class="topic-field">
      <label for="import-topic-input">{{ $t('course.chapters') }}</label>
      <TopicCombobox uid="import-topic" :course="crs" :model-value="S.imp.tp" :invalid="!!chipErr"
                     @update:model-value="v => { S.imp.tp = v; chipErr = '' }" />
      <small v-if="!crs?.topics.length">{{ $t('course.noTopic') }}</small>
      <small v-if="chipErr" class="fe" role="alert">{{ chipErr }}</small>
    </div>
    <label>{{ $t('bank.defaultLevel') }}
      <select v-model.number="S.imp.l"><option v-for="(x, i) in LV" :key="i" :value="i">{{ $t('difficulty.' + i) }}</option></select>
    </label>
    <label>{{ $t('moodle.pasteXml') }}
      <textarea id="xi" rows="7" v-model="xml" :placeholder="ph" @input="dirty = true; err = ''"></textarea>
    </label>
    <label>{{ $t('moodle.chooseFile') }}
      <input type="file" accept=".xml,text/xml" :aria-label="$t('moodle.selectFile')" @change="onFile">
    </label>
    <div id="xr">
      <p v-if="err" class="err">{{ err }}</p>
      <template v-if="res">
        <p><span class="b ok">{{ $t('moodle.savedCount', { n: res.ok }) }}</span> <span class="b" :class="res.reasons.length ? 'wa' : 'n'">{{ $t('moodle.skippedCount', { n: res.reasons.length }) }}</span></p>
        <small>{{ $t('moodle.questionsAssigned') }} <b>{{ crs.id }} – {{ crs.name }}</b> ({{ topicName(S.imp.c, S.imp.tp) }}){{ $t('moodle.assignedLevel') }} <b>{{ $t('difficulty.' + S.imp.l) }}</b>.</small>
        <ul v-if="res.reasons.length" class="iss" style="margin-top:.5rem"><li v-for="(r, i) in res.reasons" :key="i">{{ r }}</li></ul>
      </template>
    </div>
    <template #footer>
      <button type="button" class="btn" @click="cancel">{{ $t('common.close') }}</button>
      <button type="button" class="btn pri" :disabled="busy" @click="save">{{ busy ? $t('common.saving') : $t('bank.checkAndSave') }}</button>
    </template>
  </BaseModal>
</template>

<style scoped>
.topic-field { display: grid; gap: .35rem; }
</style>
