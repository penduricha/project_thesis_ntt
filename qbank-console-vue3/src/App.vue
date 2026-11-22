<script setup>
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NAV, EXTRA, SUB, ICO, COURSES } from './data/mock.js'
import { S, act, busy, list, toast, notYet, loadQuestions, runAI, removeQuestions } from './store.js'

const route = useRoute(), router = useRouter()
const mq = matchMedia('(min-width:768px)')
const nav = ref(mq.matches ? 'open' : 'closed')
const amOpen = ref(false)
const yr = new Date().getFullYear()

const page = computed(() => route.name || 'dash')
const onBank = computed(() => /^(bank|course)$/.test(page.value))
const crs = computed(() => COURSES[S.ci])
const cur = computed(() => NAV.concat(EXTRA).find(n => n[0] === page.value))
const title = computed(() => (page.value === 'course' && crs.value ? `${crs.value.id} – ${crs.value.name}` : cur.value?.[2] || ''))
const subtitle = computed(() => page.value === 'courses' ? `${SUB.courses} (hiện có ${COURSES.length} môn học).`
  : page.value === 'course' && crs.value ? `${crs.value.dept || 'Chưa có mô tả'} · ${crs.value.topics.length} chương/chủ đề con` : SUB[page.value])
const menu = computed(() => NAV.filter(n => !/^(admin|llm)$/.test(n[0]) || S.role === 'admin'))
const badge = k => (k === 'review' ? S.sug.length : k === 'tasks' ? act() : 0)
const ini = computed(() => { const w = S.name.split(' '); return (w.length > 1 ? w[0][0] + w[w.length - 1][0] : S.name.slice(0, 2)).toUpperCase() })
const shown = computed(() => (onBank.value ? list(page.value === 'course').length : 0))

const go = k => router.push('/' + k)
const toggleNav = () => { nav.value = nav.value === 'open' ? 'closed' : 'open' }
const focusView = () => document.getElementById('view')?.scrollIntoView()

function bulk(k) {
  if (!S.sel.size) return toast('Hãy chọn ít nhất một câu hỏi trước', 'wa')
  k === 'ai' ? runAI([...S.sel]) : go('moodle')
}
function bulkDel() {
  const ids = [...S.sel].filter(id => !busy({ id }))
  if (!ids.length) return toast('Các câu đang chọn đều đang kiểm định, không thể xóa', 'er')
  if (confirm(`Xóa ${ids.length} câu hỏi đã chọn?\nHành động này sẽ xóa các câu hỏi và đề xuất AI liên quan khỏi ngân hàng.`)) removeQuestions(ids)
}
function undo() { const cb = S.toast.cb; S.toast.on = false; cb && cb() }

let booted = false
watch(() => route.fullPath, async () => {
  if (!route.name) return
  amOpen.value = false
  if (!mq.matches) nav.value = 'closed'
  scrollTo(0, 0)
  await nextTick()
  document.title = title.value + ' · QBank Console'
  if (booted) { const h = document.getElementById('ttl'); if (h) { h.tabIndex = -1; h.focus({ preventScroll: true }) } }
  booted = true
}, { immediate: true })

const onDoc = e => { if (!e.target.closest('.acct')) amOpen.value = false }
const onMq = e => { nav.value = e.matches ? 'open' : 'closed' }
const onKey = e => {
  if (e.key === 'Escape' && amOpen.value) { amOpen.value = false; document.getElementById('ab')?.focus() }
  const typing = /INPUT|TEXTAREA|SELECT/.test(e.target.tagName) || e.target.isContentEditable
  if (e.key === '/' && onBank.value && !typing) { e.preventDefault(); document.getElementById('q-srch')?.focus() }
  if (!typing && !e.ctrlKey && !e.metaKey && !e.altKey) {
    if (e.key === '?') { e.preventDefault(); notYet('Bảng phím tắt') }
    else if (e.key === 'n' && onBank.value) { e.preventDefault(); notYet('Hộp thoại thêm câu hỏi') }
  }
}
onMounted(() => {
  document.addEventListener('click', onDoc); document.addEventListener('keydown', onKey); mq.addEventListener('change', onMq)
  loadQuestions()
})
onBeforeUnmount(() => { document.removeEventListener('click', onDoc); document.removeEventListener('keydown', onKey); mq.removeEventListener('change', onMq) })
</script>

<template>
  <a href="#view" class="skip" @click.prevent="focusView">Bỏ qua điều hướng</a>
  <div id="app" class="shell" :data-nav="nav">
    <header class="topbar">
      <button class="topbar__toggle" id="mt" aria-label="Mở hoặc đóng menu" :aria-expanded="String(nav === 'open')" aria-controls="side" @click="toggleNav"><span></span><span></span><span></span></button>
      <RouterLink to="/dash" class="logo-link">
        <span class="logo-badge">QB</span>
        <span class="logo-text"><strong>QBank Console</strong><span>NTTU · Kiểm định LLM</span></span>
      </RouterLink>
      <div class="acct" id="acct-wrap" @focusout="e => { if (!e.currentTarget.contains(e.relatedTarget)) amOpen = false }">
        <button class="acct__btn" id="ab" aria-haspopup="true" :aria-expanded="String(amOpen)" aria-controls="am" aria-label="Menu tài khoản" @click="amOpen = !amOpen"><span class="av js-av">{{ ini }}</span><span class="acct__chev" aria-hidden="true"></span></button>
        <div class="acct__menu" id="am" :hidden="!amOpen">
          <div class="acct__head"><div class="who"><span class="av av--lg js-av">{{ ini }}</span><div><b id="acn">{{ S.name }}</b><small id="acr">{{ S.role === 'admin' ? 'Quản trị viên' : 'Giảng viên' }}</small></div></div></div>
          <button class="acct__item" @click="go('account')">Thông tin tài khoản</button>
          <button class="acct__item" @click="go('settings')">Cài đặt</button>
          <button class="acct__item" @click="amOpen = false; notYet('Bảng phím tắt')">Phím tắt</button>
          <div class="acct__sep"></div>
          <button class="acct__item out" @click="amOpen = false; toast('Bản demo không có màn hình đăng nhập nên chưa đăng xuất thật', 'wa')">Đăng xuất</button>
        </div>
      </div>
    </header>

    <aside class="sidebar" id="side">
      <nav id="menu" aria-label="Chức năng">
        <template v-for="([k, g, t], i) in menu" :key="k">
          <p v-if="i && g !== menu[i - 1][1]" class="nav-group">{{ g }}</p>
          <button type="button" class="nv" :class="{ on: k === page || (k === 'courses' && page === 'course') }" @click="go(k)">
            <svg viewBox="0 0 24 24" aria-hidden="true" v-html="ICO[k]"></svg><span>{{ t }}</span>
            <em v-if="badge(k)" :aria-label="badge(k) + ' mục mới'">{{ badge(k) }}</em>
          </button>
        </template>
      </nav>
    </aside>
    <div class="scrim" @click="nav = 'closed'"></div>

    <main class="content">
      <div class="page">
        <div id="crumb">
          <div v-if="page === 'course' && crs" class="crumb-bar"><button type="button" @click="go('courses')">‹ Môn học</button><span>/</span><span><b>{{ crs.id }}</b> · {{ crs.name }}</span></div>
        </div>
        <div class="page__head">
          <div><h1 class="page__title" id="ttl">{{ title }}</h1><p class="page__sub" id="sub">{{ subtitle }}</p></div>
          <div id="pact">
            <button v-if="page === 'dash'" class="btn pri" @click="notYet('Hộp thoại thêm câu hỏi')">Thêm câu hỏi</button>
            <button v-else-if="page === 'courses'" class="btn pri" @click="notYet('Hộp thoại thêm môn học')">Thêm môn học</button>
            <template v-else-if="page === 'course'">
              <button class="btn sm" @click="notYet('Hộp thoại sửa môn học')">Sửa môn học</button>
              <button class="btn sm dan" @click="notYet('Hộp thoại xóa môn học')">Xóa môn học</button>
            </template>
            <button v-else-if="page === 'moodle'" class="btn pri" @click="notYet('Hộp thoại nhập Moodle XML')">Nhập Moodle XML</button>
          </div>
        </div>
        <div id="view"><RouterView /></div>
      </div>
      <footer class="foot">©<span id="yr">{{ yr }}</span> - Nguyen Tat Thanh University - All Rights Reserved.</footer>
    </main>
  </div>

  <div id="selbar" :hidden="!(onBank && S.sel.size)">
    <b id="sn">Đã chọn {{ S.sel.size }} câu</b>
    <div class="sel-acts">
      <button class="btn sm" id="sb-all" @click="list(page === 'course').forEach(q => S.sel.add(q.id))">Chọn tất cả {{ shown }}</button>
      <button class="btn sm pri" @click="bulk('ai')">Kiểm định AI</button>
      <button class="btn sm" @click="bulk('xml')">Xuất Moodle XML</button>
      <button class="btn sm dan" @click="bulkDel" aria-label="Xóa các câu hỏi đã chọn">Xóa</button>
      <button class="btn sm" @click="S.sel.clear()">Bỏ chọn tất cả</button>
    </div>
  </div>

  <div id="toast" role="status" aria-live="polite" :class="S.toast.on ? ['on', S.toast.tone] : ''" @click="S.toast.on = false">
    <span>{{ S.toast.msg }}</span>
    <button v-if="S.toast.label && S.toast.cb" type="button" @click.stop="undo">{{ S.toast.label }}</button>
  </div>
</template>
