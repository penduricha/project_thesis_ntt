<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { LV, LV_DB, ENUM_DB, IC } from '../data/mock.js'
import { COURSES, S, code, topicName, decide, bulkDecide, editQuestion } from '../store.js'
import { diffWords } from '../utils.js'

const router = useRouter()
const types = ['all', 'Chính tả', 'Ngữ pháp', 'Logic', 'Phương án nhiễu']
const items = computed(() => S.sug.filter(s => S.q.some(x => x.id === s.qid)))
const cards = computed(() => items.value
  .filter(s => S.rf === 'all' || s.is.some(i => i.k === S.rf))
  .map(s => {
    const q = S.q.find(x => x.id === s.qid), same = q.text === s.tx
    const json = JSON.stringify({ taskId: s.tid, questionId: code(q), valid: true, issues: s.is.map(i => ({ type: i.k, description: i.d })),
      suggestedText: same ? null : s.tx, suggestedDifficulty: LV_DB[LV.indexOf(s.diff)] || null, severity: ENUM_DB.sev[s.sev] || 'low' }, null, 2)
    return { s, q, same, df: diffWords(q.text, s.tx), json }
  }))
const toggle = (id, on) => (on ? S.rsel.add(id) : S.rsel.delete(id))
</script>

<template>
  <div v-if="!items.length" class="empty-box">
    <p class="mut">Hiện chưa có đề xuất chỉnh sửa nào từ AI cần xem xét.</p>
    <button class="btn pri" @click="router.push('/bank')">Đi tới Tất cả câu hỏi</button>
  </div>
  <template v-else>
    <div class="card">
      <div class="bar" style="justify-content:space-between">
        <div class="chips">
          <button v-for="t in types" :key="t" type="button" class="chip" :class="{ on: S.rf === t }" @click="S.rf = t">{{ t === 'all' ? 'Tất cả (' + items.length + ')' : t }}</button>
        </div>
        <div class="bar">
          <span class="mut" style="font-size:var(--fs-xs)">Đã chọn {{ S.rsel.size }}</span>
          <button class="btn sm" :disabled="!S.rsel.size" @click="bulkDecide(false)">Từ chối đã chọn</button>
          <button class="btn sm pri" :disabled="!S.rsel.size" @click="bulkDecide(true)">Chấp nhận đã chọn</button>
        </div>
      </div>
    </div>

    <div v-for="c in cards" :key="c.s.qid" class="card">
      <div class="bar">
        <input type="checkbox" :aria-label="'Chọn đề xuất ' + code(c.q)" :checked="S.rsel.has(c.s.qid)" @change="toggle(c.s.qid, $event.target.checked)">
        <b>{{ code(c.q) }}</b>
        <span class="mut">· {{ COURSES[c.q.c]?.id || '' }} ({{ topicName(c.q.c, c.q.tp) }}) · Tác vụ <RouterLink to="/tasks" style="color:var(--primary-ink);font-weight:600">{{ c.s.tid }}</RouterLink></span>
        <span class="grow"></span>
        <span class="b" :class="c.s.sev === 'Cao' ? 'er' : 'wa'">Mức độ: {{ c.s.sev }} ({{ c.s.is.length }} lỗi)</span>
        <span class="b n">Độ khó gợi ý: {{ c.s.diff }}</span>
      </div>
      <div class="cmp" :hidden="c.same">
        <div><h4>Nội dung gốc</h4><p><template v-for="(w, i) in c.df.oldTok" :key="i"><del v-if="w.k === 'del'" class="diff-del">{{ w.t }}</del><template v-else>{{ w.t }}</template>{{ ' ' }}</template></p></div>
        <div class="new"><h4>Đề xuất của AI</h4><p><template v-for="(w, i) in c.df.newTok" :key="i"><ins v-if="w.k === 'ins'" class="diff-ins">{{ w.t }}</ins><template v-else>{{ w.t }}</template>{{ ' ' }}</template></p></div>
      </div>
      <div class="qrow__opts"><span v-for="(o, i) in c.q.o" :key="i" class="o" :class="{ on: i === c.q.a }"><i>{{ 'ABCD'[i] }}</i>{{ o }}</span></div>
      <p v-if="c.same" class="warn-inline" style="font-size:var(--fs-sm)">AI chỉ nhận xét, không đề xuất văn bản thay thế. Hãy mở trình sửa để chỉnh phương án hoặc giữ nguyên.</p>
      <ul class="iss"><li v-for="(i, k) in c.s.is" :key="k"><span class="b" :class="IC[i.k]">{{ i.k }}</span> {{ i.d }}</li></ul>
      <details><summary class="mut" style="cursor:pointer;font-size:var(--fs-sm)">Phản hồi JSON đã kiểm tra hợp lệ</summary><pre class="codeblock" style="margin-top:.5rem">{{ c.json }}</pre></details>
      <small>Lưu ý: AI không tự ý thay đổi đáp án đúng. Nội dung câu hỏi chỉ cập nhật khi giảng viên bấm Chấp nhận.</small>
      <div class="end">
        <button class="btn" @click="decide(c.s.qid, false)">{{ c.same ? 'Đã xem, giữ nguyên' : 'Từ chối, giữ bản gốc' }}</button>
        <button v-if="c.same" class="btn pri" @click="editQuestion(c.s.qid)">Mở trình sửa</button>
        <button v-else class="btn pri" @click="decide(c.s.qid, true)">Chấp nhận và cập nhật</button>
      </div>
    </div>
  </template>
</template>
