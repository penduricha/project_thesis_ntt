<script setup>
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NAV, EXTRA, SUB, ICO } from './data/mock.js'
import ModalHost from './components/ModalHost.vue'
import { COURSES, S, act, busy, list, toast, notYet, loadQuestions, runAI, removeQuestions, editQuestion, openDialog, askDeleteCourse, toastHold, toastRelease } from './store.js'
import { auth, logout } from './auth/authStore.js'
import { REQUEST_STATUS } from './auth/AdminRepository.js'
import { net, isOffline, watchBrowserNetwork, startHealthCheck, probe } from './netStatus.js'
import { t as tr } from './i18n/index.js'

const route = useRoute(), router = useRouter()
const mq = matchMedia('(min-width:768px)')
const nav = ref(mq.matches ? 'open' : 'closed')
const amOpen = ref(false)
const desk = ref(mq.matches)
const sideBar = ref(null)
const yr = new Date().getFullYear()

const isLoginPage = computed(() => route.name === 'login')
/* 404 hiển thị "trần" (không topbar/menu) khi CHƯA đăng nhập — tránh lộ menu của ứng dụng cho
   người gõ sai link. Đã đăng nhập thì vẫn giữ khung đầy đủ để họ đi tiếp bằng menu. */
const is404 = computed(() => route.name === 'not-found')
const isBarePage = computed(() => isLoginPage.value || (is404.value && !auth.isAuthenticated))
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
const toggleNav = () => {
  nav.value = nav.value === 'open' ? 'closed' : 'open'
  if (nav.value === 'open' && !mq.matches) requestAnimationFrame(() => (document.querySelector('#menu .nv.on') || document.querySelector('#menu .nv'))?.focus())
}
const focusView = () => { const v = document.getElementById('view'); if (v) { v.tabIndex = -1; v.focus() } }
const showSel = computed(() => onBank.value && S.sel.size > 0)
/* v10: thanh chọn không che hàng cuối – body.has-sel + biến --sideBar-h */
function fitSel() {
  const sb = sideBar.value; if (!sb) return
  document.body.classList.toggle('has-sel', !sb.hidden)
  if (!sb.hidden) document.documentElement.style.setProperty('--sideBar-h', sb.offsetHeight + 'px')
}
watch(showSel, () => nextTick(fitSel))
watch(amOpen, v => { if (v) nextTick(() => document.querySelector('#am .acct__item')?.focus()) })
function menuKey(e) {
  const it = [...document.querySelectorAll('#am .acct__item')], i = it.indexOf(document.activeElement); let n = -1
  if (e.key === 'ArrowDown') n = (i + 1) % it.length; else if (e.key === 'ArrowUp') n = (i - 1 + it.length) % it.length
  else if (e.key === 'Home') n = 0; else if (e.key === 'End') n = it.length - 1
  if (n >= 0) { e.preventDefault(); it[n].focus() }
}

function bulk(k) {
  if (!S.sel.size) return toast('Hãy chọn ít nhất một câu hỏi trước', 'wa')
  k === 'ai' ? runAI([...S.sel]) : go('moodle')
}
function bulkDel() {
  const ids = [...S.sel].filter(id => !busy({ id }))
  if (!ids.length) return toast('Các câu đang chọn đều đang kiểm định, không thể xóa', 'er')
  openDialog('confirm', { title: `Xóa ${ids.length} câu hỏi đã chọn?`, text: 'Hành động này sẽ xóa các câu hỏi và đề xuất AI liên quan khỏi ngân hàng.', cancel: 'Giữ lại', ok: `Xóa ${ids.length} câu`, danger: true, action: () => removeQuestions(ids) })
}
function undo() { const cb = S.toast.cb; S.toast.on = false; cb && cb() }

const loggingOut = ref(false)
async function doLogout() {
  if (loggingOut.value) return
  loggingOut.value = true
  amOpen.value = false
  try {
    const status = await logout()
    if (status === REQUEST_STATUS.OK) {
      S.sel.clear()
      await router.push('/login')
    } else {
      toast('Không thể đăng xuất lúc này. Vui lòng thử lại.', 'er')
    }
  } catch {
    toast('Không thể kết nối máy chủ để đăng xuất.', 'er')
  } finally {
    loggingOut.value = false
  }
}
// Đồng bộ tên/vai trò thật từ backend vào store dùng chung (App.vue + các view đang đọc S.name/S.role)
watch(() => auth.adminName, (v) => { if (v) S.name = v }, { immediate: true })
watch(() => auth.role, (v) => { if (v) S.role = v }, { immediate: true })

let booted = false
watch(() => route.fullPath, async () => {
  if (!route.name) return
  amOpen.value = false
  if (!mq.matches) nav.value = 'closed'
  scrollTo(0, 0)
  await nextTick()
  // Trang đăng nhập tự đặt tiêu đề riêng; nếu ghép theo mẫu ' · QBank Console' thì
  // route login không có title -> tab trình duyệt hiện ' · QBank Console' (dư dấu chấm).
  document.title = isLoginPage.value ? 'Đăng nhập'
    : is404.value ? 'Không tìm thấy trang · QBank Console'
    : (title.value ? `${title.value} · QBank Console` : 'QBank Console')
  if (booted) { const h = document.getElementById('ttl'); if (h) { h.tabIndex = -1; h.focus({ preventScroll: true }) } }
  booted = true
}, { immediate: true })

const onDoc = e => { if (!e.target.closest('.acct')) amOpen.value = false }
const onMq = e => { desk.value = e.matches; nav.value = e.matches ? 'open' : 'closed' }
const onKey = e => {
  if (e.key === 'Escape' && amOpen.value) { amOpen.value = false; document.getElementById('ab')?.focus() }
  const typing = /INPUT|TEXTAREA|SELECT/.test(e.target.tagName) || e.target.isContentEditable
  if ((e.ctrlKey || e.metaKey) && !e.shiftKey && !e.altKey && String(e.key).toLowerCase() === 'z' && !typing && S.toast.cb && S.toast.on) { e.preventDefault(); undo() }
  if (e.key === 'Escape' && !mq.matches && nav.value === 'open' && !S.dlg.kind) { nav.value = 'closed'; document.getElementById('mt')?.focus() }
  if (e.key === '/' && onBank.value && !typing) { e.preventDefault(); document.getElementById('q-srch')?.focus() }
  if (!typing && !e.ctrlKey && !e.metaKey && !e.altKey && !S.dlg.kind) {
    if (e.key === '?') { e.preventDefault(); openDialog('keys') }
    else if (e.key === 'n' && onBank.value) { e.preventDefault(); editQuestion(0, page.value === 'course' ? { c: S.ci, tp: S.f.tp !== '' ? +S.f.tp : 0 } : {}) }
  }
}
/* ---------- Theo dõi kết nối mạng ----------
   Hai nguồn tín hiệu bổ sung nhau (xem netStatus.js): trạng thái mạng của trình duyệt và
   kết quả request thật. Server sống lại thì banner tự tắt, không cần người dùng tải lại trang. */
let stopHealth = null
onMounted(() => {
  document.addEventListener('click', onDoc); document.addEventListener('keydown', onKey); mq.addEventListener('change', onMq)
  if (window.ResizeObserver && sideBar.value) new ResizeObserver(fitSel).observe(sideBar.value)
  watchBrowserNetwork()
  stopHealth = startHealthCheck(30000)
})

// Chỉ tải dữ liệu sau khi biết chắc đã đăng nhập (đừng gọi API qbank khi còn ở trang /login),
// và tải lại đúng một lần mỗi khi trạng thái đăng nhập chuyển sang true.
let loadedFor = null
watch(() => auth.isAuthenticated, (ok) => {
  if (!ok) { loadedFor = null; return }
  if (loadedFor === auth.adminId) return
  loadedFor = auth.adminId
  loadQuestions()
}, { immediate: true })
onBeforeUnmount(() => {
  document.removeEventListener('click', onDoc); document.removeEventListener('keydown', onKey); mq.removeEventListener('change', onMq)
  stopHealth?.()
})

/* Nút "Thử lại": kiểm tra lại ngay thay vì đợi nhịp 30 giây, rồi nạp lại dữ liệu
   đang thiếu. Nhờ vậy người dùng không phải F5. */
const retrying = ref(false)
async function retryConnection() {
  if (retrying.value) return
  retrying.value = true
  try {
    await probe()
    if (!isOffline.value) {
      loadQuestions()
      toast(tr('net.backOnline'), 'ok')
    }
  } finally { retrying.value = false }
}
</script>

<template>
  <RouterView v-if="isBarePage" />
  <template v-else>
  <a href="#view" class="skip" @click.prevent="focusView">{{ $t('common.skipNav') }}</a>
  <div v-if="auth.isDemo" class="demo-banner" role="status">{{ $t('brand.demoBanner') }}</div>
  <!-- Banner mất kết nối: dùng role="alert" (không phải "status") vì đây là sự cố người dùng
       phải biết ngay; trình đọc màn hình sẽ đọc ngay thay vì xếp hàng sau thông báo khác.
       Nút Thử lại cho phép phục hồi tại chỗ, không phải F5. -->
  <div v-if="isOffline" class="net-banner" role="alert">
    <span class="net-banner__dot" aria-hidden="true"></span>
    <b>{{ $t('net.offline') }}</b>
    <span class="net-banner__hint">{{ $t('net.offlineHint') }}</span>
    <button type="button" class="btn sm net-banner__btn" :disabled="retrying" @click="retryConnection">
      {{ retrying ? $t('net.retrying') : $t('common.retry') }}
    </button>
  </div>
  <div id="app" class="shell" :data-nav="nav" :inert="S.dlg.kind ? '' : undefined">
    <header class="topbar">
      <button class="topbar__toggle" id="mt" :aria-label="$t('dialog.openMenu')" :aria-expanded="String(nav === 'open')" aria-controls="side" @click="toggleNav">
        <!--
          3 gạch vẽ bằng SVG chứ không bằng 3 thẻ <span> như trước.
          Lý do: với <span> (height:2px, gap:5px trong khung 44px) chiều cao nội dung là 16px,
          căn giữa còn dư 14px ⇒ mỗi thanh rơi vào toạ độ nửa pixel và trình duyệt vẽ thanh
          thứ ba mảnh hơn hai thanh kia (đo trên ảnh chụp: 3px–3px–2px). Toạ độ nguyên trong
          SVG luôn cho ba gạch dày bằng nhau ở mọi mức zoom và mọi tỉ lệ màn hình.
        -->
        <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true" focusable="false"><path d="M4 7h16M4 12h16M4 17h16"/></svg>
      </button>
      <RouterLink to="/dash" class="logo-link">
        <span class="logo-badge">QB</span>
        <span class="logo-text"><strong>{{ $t('brand.name') }}</strong><span>{{ $t('brand.sub') }}</span></span>
      </RouterLink>
      <div class="acct" id="acct-wrap" @focusout="e => { if (!e.currentTarget.contains(e.relatedTarget)) amOpen = false }">
        <button class="acct__btn" id="ab" aria-haspopup="menu" :aria-expanded="String(amOpen)" aria-controls="am" :aria-label="$t('admin.accountMenu')" @click="amOpen = !amOpen" @keydown.down.prevent="amOpen = true"><span class="av js-av">{{ ini }}</span><span class="acct__chev" aria-hidden="true"></span></button>
        <div class="acct__menu" id="am" role="menu" :aria-label="$t('nav.accountMenu')" :hidden="!amOpen" @keydown="menuKey">
          <div class="acct__head"><div class="who"><span class="av av--lg js-av">{{ ini }}</span><div><b id="acn">{{ S.name }}</b><small id="acr">{{ S.role === 'admin' ? $t('admin.roleAdmin') : $t('admin.roleLecturer') }}</small></div></div></div>
          <button role="menuitem" class="acct__item" @click="go('account')">{{ $t('nav.account') }}</button>
          <button role="menuitem" class="acct__item" @click="go('settings')">{{ $t('nav.settings') }}</button>
          <button role="menuitem" class="acct__item" @click="amOpen = false; openDialog('keys')">{{ $t('dialog.shortcuts') }}</button>
          <div class="acct__sep" role="separator"></div>
          <button role="menuitem" class="acct__item out" :disabled="loggingOut" @click="doLogout">{{ loggingOut ? $t('common.loggingOut') : $t('common.logout') }}</button>
        </div>
      </div>
    </header>

    <aside class="sidebar" id="side">
      <nav id="menu" :aria-label="$t('admin.function')">
        <template v-for="([k, g, t], i) in menu" :key="k">
          <p v-if="i && g !== menu[i - 1][1]" class="nav-group">{{ g }}</p>
          <button type="button" class="nv" :class="{ on: k === page || (k === 'courses' && page === 'course') }" @click="go(k)">
            <svg viewBox="0 0 24 24" aria-hidden="true" v-html="ICO[k]"></svg><span>{{ t }}</span>
            <em v-if="badge(k)" :aria-label="badge(k) + $t('common.newCount')">{{ badge(k) }}</em>
          </button>
        </template>
      </nav>
    </aside>
    <div class="scrim" @click="nav = 'closed'"></div>

    <main class="content" :inert="nav === 'open' && !desk ? '' : undefined">
      <div class="page">
        <div id="crumb">
          <div v-if="page === 'course' && crs" class="crumb-bar"><button type="button" @click="go('courses')">{{ $t('nav.backCourses') }}</button><span>/</span><span><b>{{ crs.id }}</b> · {{ crs.name }}</span></div>
        </div>
        <!-- Trang 404 không có tiêu đề/phụ đề và không có nút hành động nào ở đây, nên bỏ hẳn
             khối này: trước đây nó vẫn chiếm chỗ (h1 rỗng + margin-bottom 1.25rem), làm khối 404
             bị đẩy lệch xuống thay vì nằm giữa vùng nội dung. -->
        <div v-if="!is404" class="page__head">
          <div><h1 class="page__title" id="ttl">{{ title }}</h1><p class="page__sub" id="sub">{{ subtitle }}</p></div>
          <div id="pact">
            <button v-if="page === 'dash'" class="btn pri" @click="editQuestion(0)">{{ $t('bank.addQuestion') }}</button>
            <button v-else-if="page === 'courses'" class="btn pri" @click="openDialog('course', { index: -1 })">{{ $t('course.addCourse') }}</button>
            <template v-else-if="page === 'course'">
              <button class="btn sm" @click="openDialog('course', { index: S.ci })">{{ $t('course.editCourse') }}</button>
              <button class="btn sm dan" @click="askDeleteCourse(S.ci, () => go('courses'))">{{ $t('course.deleteCourse') }}</button>
            </template>
            <button v-else-if="page === 'moodle'" class="btn pri" @click="openDialog('import')">{{ $t('bank.importMoodle') }}</button>
          </div>
        </div>
        <div id="view"><RouterView /></div>
      </div>
      <footer class="foot">©<span id="yr">{{ yr }}</span> - Nguyen Tat Thanh University - All Rights Reserved.</footer>
    </main>
  </div>

  <div id="sideBar" ref="sideBar" :hidden="!showSel" :inert="S.dlg.kind ? '' : undefined">
    <b id="sn">{{ $t('bank.selectedCount', { n: S.sel.size }) }}</b>
    <div class="sel-acts">
      <button class="btn sm" id="sb-all" @click="list(page === 'course').forEach(q => S.sel.add(q.id))">{{ $t('common.selectAll') }} {{ shown }}</button>
      <button class="btn sm pri" @click="bulk('ai')">{{ $t('nav.group.ai') }}</button>
      <button class="btn sm" @click="bulk('xml')">{{ $t('common.export') }} <span class="x-long">Moodle </span>XML</button>
      <button class="btn sm dan" @click="bulkDel" :aria-label="$t('bank.deleteSelected')">{{ $t('common.delete') }}</button>
      <button class="btn sm" @click="S.sel.clear()">{{ $t('common.clearAll') }}</button>
    </div>
  </div>

  <ModalHost />

  <div id="toast" role="status" aria-live="polite" :class="S.toast.on ? ['on', S.toast.tone] : ''" @click="S.toast.on = false" @mouseenter="toastHold" @focusin="toastHold" @mouseleave="toastRelease" @focusout="toastRelease">
    <span>{{ S.toast.msg }}</span>
    <button v-if="S.toast.label && S.toast.cb" type="button" title="Ctrl+Z" aria-keyshortcuts="Control+Z" @click.stop="undo">{{ S.toast.label }}</button>
  </div>
  </template>
</template>
