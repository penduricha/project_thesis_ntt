<script setup>
import { reactive, ref, computed } from 'vue'
import { S, log, toast } from '../store.js'
import { t } from '../i18n/index.js'
import { DEMO_MODE } from '../config.js'

/* ---- chuẩn hóa / kiểm tra URL ---- */
const norm = u => (u || '').trim().replace(/\/(api\/chat|health)\/?$/i, '').replace(/\/+$/, '')
function issue(u) {
  if (!u) return null
  let p; try { p = new URL(u) } catch { return ['er', t('llmState.invalidUrl')] }
  if (p.protocol !== 'https:') return ['er', t('llmState.httpsRequired')]
  if (!p.hostname.endsWith('.trycloudflare.com')) return ['wa', t('llmState.domainWarning')]
  return null
}
const dirty = c => c.url !== c.sv.url || c.tok !== c.sv.tok || c.en !== c.sv.en
const bad = c => { const x = issue(norm(c.url)); return !c.url || !!(x && x[0] === 'er') }

/* ---- trạng thái hiển thị của từng ô (đèn LED, ghi chú, nút) ---- */
function calc(c) {
  const has = c.url || c.tok, d = dirty(c), is = issue(norm(c.url)); let led, tx
  if (c.busy) { led = 'checking'; tx = t('llmState.checking') }
  else if (!has) { led = 'empty'; tx = t('llmState.empty') }
  else if (c.ok === false) { led = 'fail'; tx = t('llmState.connectionError') }
  else if (d) { led = c.ok ? 'ok' : 'draft'; tx = c.ok ? t('llmState.readyUnsaved') : t('llmState.unsaved') }
  else { led = c.en ? 'ok' : 'off'; tx = c.en ? t('llmState.ready') : t('llmState.off') }
  const n = c.note ? [c.nk, t(c.note, { seconds: c.latency })] : is ? is : (c.tok && c.tok.length < 20) ? ['wa', t('llmState.tokenShort')] : (d && S.api.on) ? ['', t('llmState.notSaved')] : ['', '']
  const off = !S.api.on || c.busy
  return { led, tx, n, tps: led === 'ok' && !d && c.tps ? c.tps + ' tok/s' : '', chk: off || bad(c) || !c.tok, sav: off || !d || bad(c) || !c.tok, clr: c.busy || !has }
}
const slots = computed(() => S.lm.map(calc))
const sum = computed(() => {
  const f = S.lm.filter(c => c.url && c.tok && !bad(c)).length, b = S.lm.filter(c => c.url && c.tok && bad(c)).length, d = S.lm.filter(c => dirty(c) && !bad(c) && c.tok).length
  return { text: (S.api.on ? t('llmState.summaryUnsaved', { valid: f, unsaved: d }) : t('llmState.summary', { valid: f })) + (b ? t('llmState.summaryInvalid', { n: b }) : ''), allOff: !S.api.on || !d, envOff: !f, free: t('llmState.freeSlots', { free: S.lm.filter(c => !(c.url || c.tok)).length, total: S.lm.length }) }
})
const st = computed(() => (S.api.busy ? ['wa', t('llmState.connecting')] : S.api.on ? ['ok', t('llmState.connected')] : ['n', t('llmState.disconnected')]))

/* ---- thao tác (mock API: setTimeout + dữ liệu cứng) ---- */
const show = reactive({}), flash = ref([]), paste = ref(''), tg = ref('free')
function set(i, f, v) { const c = S.lm[i]; c[f] = v; c.ok = null; c.note = ''; c.nk = '' }
function check(i) {
  const c = S.lm[i]; c.busy = true; c.note = ''
  setTimeout(() => {
    c.busy = false; const up = S.colab; c.ok = up; c.nk = up ? 'ok' : 'er'
    c.latency = (.3 + Math.random() * .6).toFixed(1)
    c.note = up ? 'llmState.nodeHealthy' : 'llmState.nodeTimeout'
    if (up && !c.tps) c.tps = +(14 + Math.random() * 6).toFixed(1)
    log(t(up ? 'llmState.logCheckOk' : 'llmState.logCheckFailed', { n: i + 1 }), up ? 'ok' : 'er')
  }, 700 + Math.random() * 600)
}
function save(i, quiet) {
  const c = S.lm[i]; if (!S.api.on || bad(c) || !c.tok) return 0
  c.url = norm(c.url); c.sv = { url: c.url, tok: c.tok, en: c.en }
  log(t('llmState.logSaved', { n: i + 1 }), 'ok'); if (!quiet) toast(t('llmState.saved', { n: i + 1 }), 'ok'); return 1
}
function saveAll() { const n = S.lm.reduce((a, c, i) => a + (dirty(c) && c.url && c.tok ? save(i, 1) : 0), 0); if (n) toast(t('llmState.savedCount', { n }), 'ok') }
function clear(i) {
  const c = S.lm[i], b = { url: c.url, tok: c.tok, en: c.en, sv: { ...c.sv }, tps: c.tps }
  Object.assign(c, { url: '', tok: '', en: false, sv: { url: '', tok: '', en: false }, ok: null, note: '', nk: '', tps: 0 })
  log(t('llmState.logRemoved', { n: i + 1 }), 'wa'); document.getElementById('lu' + i)?.focus()
  toast(t('llmState.removed', { n: i + 1 }), 'wa', t('bank.undoAction'), () => Object.assign(c, b))
}
function conn() {
  const a = S.api
  if (a.on) { a.on = false; log(t('llmState.logDisconnected'), 'wa'); return toast(t('llmState.serverDisconnected'), 'wa') }
  a.base = a.base.trim(); a.jwt = a.jwt.trim()
  if (!/^https?:\/\//i.test(a.base)) return toast(t('llmState.invalidApi'), 'er')
  if (!a.jwt) return toast(t('llmState.tokenRequired'), 'er')
  a.busy = true; setTimeout(() => { a.busy = false; a.on = true; log(t('llmState.logConnected'), 'ok'); toast(t('llmState.serverConnected'), 'ok') }, 700)
}
function parse(t) {
  const u = [], k = []
  for (const m of t.matchAll(/URL\s*[:=]\s*(https?:\/\/[^\s"'<>]+)|(https:\/\/[a-z0-9-]+\.trycloudflare\.com)/gi)) { const x = norm((m[1] || m[2]).replace(/[),.;]+$/, '')); if (x && !u.includes(x)) u.push(x) }
  for (const m of t.matchAll(/TOKEN\s*[:=]\s*([A-Za-z0-9_\-]{16,})/gi)) if (!k.includes(m[1])) k.push(m[1])
  return u.slice(0, Math.min(u.length, k.length)).map((x, j) => ({ url: x, tok: k[j] }))
}
function fill() {
  const pr = parse(paste.value)
  if (!pr.length) return toast(t('llmState.noPair'), 'wa')
  const idx = tg.value === 'free' ? S.lm.map((c, i) => (c.url || c.tok ? -1 : i)).filter(i => i >= 0) : S.lm.map((_, i) => i).slice(+tg.value), n = Math.min(pr.length, idx.length)
  if (!n) return toast(t('llmState.noFreeSlot'), 'wa')
  for (let k = 0; k < n; k++) Object.assign(S.lm[idx[k]], { url: pr[k].url, tok: pr[k].tok, en: true, ok: null, note: '', nk: '' })
  paste.value = ''; flash.value = idx.slice(0, n); setTimeout(() => { flash.value = [] }, 1800)
  toast(n < pr.length ? t('llmState.filledPartial', { n, total: pr.length, left: pr.length - n }) : t('llmState.filled', { n }), n < pr.length ? 'wa' : 'ok')
}
async function env() {
  const L = ['# ' + t('llmState.envHeader')], skip = []
  S.lm.forEach((c, i) => {
    if (!(c.url && c.tok)) return
    if (bad(c)) { skip.push(i + 1); return }
    const n = i + 1; L.push(`AI_NODE_${n}_URL=${norm(c.url)}`, `AI_NODE_${n}_TOKEN=${c.tok.trim()}`); if (n > 1) L.push(`AI_NODE_${n}_ENABLED=${c.en}`)
  })
  if (L.length === 1) return toast(t('llmState.noValidExport'), 'er')
  try { await navigator.clipboard.writeText(L.join('\n') + '\n'); toast(skip.length ? t('llmState.envSkipped', { nodes: skip.join(', ') }) : t('llmState.envCopied'), skip.length ? 'wa' : 'ok') }
  catch { toast(t('llmState.clipboardBlocked'), 'wa') }
}
const phPaste = computed(() => t('llmState.pasteExample'))
</script>

<template>
  <div v-if="!DEMO_MODE" class="card" role="status">{{ $t('llmState.unavailableRealMode') }}</div>
  <template v-else>
  <div class="lmhead">
    <section class="card" aria-labelledby="lm-t1">
      <div class="card__head"><h3 id="lm-t1">{{ $t('llm.server2') }}</h3><span class="b" :class="st[0]" role="status"><i v-if="S.api.busy" class="spin"></i>{{ st[1] }}</span></div>
      <div class="lmpills"><span class="b code n">model: {{ S.api.on ? 'qwen2.5-14b-instruct-q4_K_M' : $t('common.notConnected') }}</span><span class="b code n">mode: {{ S.api.on ? 'async · Redis queue' : '-' }}</span></div>
      <label>{{ $t('llm.apiAddress') }}<input id="lm-base" class="mono" inputmode="url" spellcheck="false" autocomplete="off" v-model="S.api.base" :readonly="S.api.on"></label>
      <label>{{ $t('llm.tokenLabel') }}
        <div class="pw">
          <input id="lm-jwt" class="mono" :type="show['lm-jwt'] ? 'text' : 'password'" spellcheck="false" autocomplete="off" :placeholder="$t('llm.tokenRequired')" v-model="S.api.jwt" :readonly="S.api.on">
          <button class="btn sm" type="button" :aria-pressed="String(!!show['lm-jwt'])" @click="show['lm-jwt'] = !show['lm-jwt']">{{ show['lm-jwt'] ? $t('common.hide') : $t('common.show') }}</button>
        </div>
      </label>
      <button class="btn w100 lmact" :class="{ pri: !S.api.on }" :disabled="S.api.busy" @click="conn">{{ S.api.on ? $t('common.disconnect') : $t('common.connect') }}</button>
    </section>
    <section class="card" aria-labelledby="lm-t2">
      <div class="card__head"><h3 id="lm-t2">{{ $t('llm.pasteResult') }}</h3><span class="b n" id="lm-free">{{ sum.free }}</span></div>
      <p class="mut">{{ $t('llm.pasteNodeHint') }}</p>
      <textarea id="lm-paste" class="area" spellcheck="false" autocomplete="off" :aria-label="$t('llm.resultFromColab')" :placeholder="phPaste" v-model="paste"></textarea>
      <div class="bar lmact">
        <label class="lmtg">{{ $t('dialog.fillIn') }}<select id="lm-tg" v-model="tg"><option value="free">{{ $t('dialog.firstEmpty') }}</option><option v-for="(_, i) in S.lm" :key="i" :value="String(i)">{{ $t('llm.fromLlm', { n: i + 1 }) }}</option></select></label>
        <button class="btn pri" @click="fill">{{ $t('dialog.fillInBox') }}</button>
      </div>
    </section>
  </div>
  <p class="lmwarn" :hidden="S.api.on">{{ $t('llm.notConnected') }}</p>

  <div class="rack" :aria-label="$t('llm.connectNodes')">
    <article v-for="(c, i) in S.lm" :key="i" class="slot" :class="{ flash: flash.includes(i) }" :id="'slot' + i" :data-led="slots[i].led">
      <div class="slot__rail"><div class="slot__id"><span class="led" aria-hidden="true"></span><b>LLM {{ i + 1 }}</b></div><span class="slot__st">{{ slots[i].tx }}</span><span class="slot__tps">{{ slots[i].tps }}</span></div>
      <div class="slot__body">
        <label>{{ $t('llm.serviceUrl') }}<input :id="'lu' + i" class="mono" type="url" inputmode="url" spellcheck="false" autocomplete="off" :placeholder="$t('llm.pastePlaceholder')" :value="c.url" @input="set(i, 'url', $event.target.value)"></label>
        <label>{{ $t('common.token') }}
          <div class="pw">
            <input :id="'tk' + i" class="mono" :type="show['tk' + i] ? 'text' : 'password'" spellcheck="false" autocomplete="off" :placeholder="$t('llm.pasteToken')" :value="c.tok" @input="set(i, 'tok', $event.target.value)">
            <button class="btn sm" type="button" :aria-pressed="String(!!show['tk' + i])" @click="show['tk' + i] = !show['tk' + i]">{{ show['tk' + i] ? $t('common.hide') : $t('common.show') }}</button>
          </div>
        </label>
        <small class="slot__note" :class="slots[i].n[0]" aria-live="polite">{{ slots[i].n[1] }}</small>
      </div>
      <div class="slot__acts">
        <label class="sw"><input type="checkbox" role="switch" :checked="c.en" @change="set(i, 'en', $event.target.checked)"><span class="sw__ui" aria-hidden="true"></span><span>{{ $t('llm.useNode') }}</span></label>
        <div class="slot__btns">
          <button class="btn sm" :disabled="slots[i].chk" @click="check(i)">{{ $t('common.check') }}</button>
          <button class="btn sm pri" :disabled="slots[i].sav" @click="save(i)">{{ $t('common.save') }}</button>
          <button class="btn sm del" :disabled="slots[i].clr" @click="clear(i)">{{ $t('common.delete') }}</button>
        </div>
      </div>
    </article>
  </div>

  <div class="lmbar">
    <span id="lm-sum" class="mut" role="status">{{ sum.text }}</span>
    <div class="bar"><button id="lm-env" class="btn" :disabled="sum.envOff" @click="env">{{ $t('llm.copyEnv') }}</button><button id="lm-all" class="btn pri" :disabled="sum.allOff" @click="saveAll">{{ $t('common.saveAll') }}</button></div>
  </div>
  </template>
</template>
