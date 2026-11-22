<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { COURSES, S, blankF, openDialog, askDeleteCourse, subjOk } from '../store.js'
import { norm } from '../utils.js'

const router = useRouter()
const q = computed(() => norm((S.cq || '').trim().replace(/\s+/g, ' ')))
const items = computed(() => COURSES.map((c, i) => [c, i]).filter(([c, i]) => subjOk(i) && (!q.value || norm(c.name).includes(q.value) || norm(c.id).includes(q.value))))
const count = computed(() => ((S.cq || '').trim() ? `Hiển thị ${items.value.length}/${COURSES.length} môn học` : `${COURSES.length} môn học`))
const stat = i => {
  const L = S.q.filter(x => x.c == i), ok = L.filter(x => x.st == 'ok').length
  return { n: L.length, ok, fl: L.filter(x => x.st == 'flag').length, pct: L.length ? Math.round(ok / L.length * 100) : 0 }
}
const hl = name => {
  const n = norm(name), k = q.value ? n.indexOf(q.value) : -1
  return k < 0 || n.length !== name.length ? { a: name, m: '', z: '' } : { a: name.slice(0, k), m: name.slice(k, k + q.value.length), z: name.slice(k + q.value.length) }
}
const goCourse = i => { S.ci = i; S.sel.clear(); S.f = blankF(S.f.hideAns); router.push('/course/' + i) }
const focusQ = () => document.getElementById('cq')?.focus()
</script>

<template>
  <div v-if="!COURSES.length" class="empty-box">
    <p class="mut">Chưa có môn học nào trong ngân hàng câu hỏi.</p>
    <button class="btn pri" @click="openDialog('course', { index: -1 })">Thêm môn học đầu tiên</button>
  </div>
  <template v-else>
    <div class="csb">
      <div class="csb__in">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 21l-4.35-4.35M11 19a8 8 0 1 1 0-16 8 8 0 0 1 0 16z"/></svg>
        <input id="cq" type="search" class="csb__input" placeholder="Tìm môn học theo tên, ví dụ: Cấu trúc" v-model="S.cq" autocomplete="off" enterkeyhint="search" aria-label="Tìm môn học theo tên" @keydown.esc="S.cq = ''">
        <button type="button" id="cq-clr" class="csb__clr" aria-label="Xóa nội dung tìm kiếm" :hidden="!S.cq" @click="S.cq = ''; focusQ()">×</button>
      </div>
      <span id="cq-n" class="mut csb__n" role="status" aria-live="polite">{{ count }}</span>
    </div>
    <div id="cgrid" class="tgrid">
      <div v-if="!items.length" class="empty-box" style="grid-column:1/-1">
        <p class="mut">Không tìm thấy môn học nào khớp “{{ (S.cq || '').trim() }}”.</p>
        <button type="button" class="btn" @click="S.cq = ''; focusQ()">Xóa tìm kiếm</button>
      </div>
      <div v-for="[c, i] in items" :key="c.id" class="card tcard">
        <button type="button" class="tcard__main" @click="goCourse(i)">
          <div class="tcard__top"><span class="b code">{{ c.id }}</span><span class="b n">{{ c.topics.length }} chương</span></div>
          <b class="tcard__n">{{ hl(c.name).a }}<mark v-if="hl(c.name).m">{{ hl(c.name).m }}</mark>{{ hl(c.name).z }}</b>
          <span class="tcard__dept">{{ c.dept || 'Chưa có mô tả' }}</span>
          <span v-if="c.active === false" class="b er">Ngừng sử dụng</span>
          <span class="tcard__c">{{ stat(i).n }} câu hỏi</span>
          <span class="bt"><i :style="{ width: stat(i).pct + '%' }"></i></span>
          <small class="mut">{{ stat(i).ok }}/{{ stat(i).n }} đã kiểm định ({{ stat(i).pct }}%)</small>
          <span class="tags">
            <template v-if="stat(i).n"><span class="b ok">{{ stat(i).ok }} đạt chuẩn</span><span v-if="stat(i).fl" class="b wa">{{ stat(i).fl }} có đề xuất AI</span></template>
            <span v-else class="b n">Chưa có câu hỏi</span>
          </span>
        </button>
        <div class="tcard__foot">
          <button type="button" class="btn sm" @click="openDialog('course', { index: i })">Sửa môn</button>
          <button type="button" class="btn sm dan" @click="askDeleteCourse(i)">Xóa môn</button>
        </div>
      </div>
    </div>
  </template>
</template>
