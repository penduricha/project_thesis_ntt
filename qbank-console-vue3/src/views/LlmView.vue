<script setup>
import { reactive, ref, computed } from 'vue'
import { S, log, toast } from '../store.js'

/* ---- chuẩn hóa / kiểm tra URL ---- */
const norm = u => (u || '').trim().replace(/\/(api\/chat|health)\/?$/i, '').replace(/\/+$/, '')
function issue(u) {
  if (!u) return null
  let p; try { p = new URL(u) } catch { return ['er', 'URL chưa đúng dạng. Ví dụ: https://ten-ngau-nhien.trycloudflare.com'] }
  if (p.protocol !== 'https:') return ['er', 'URL phải bắt đầu bằng https://']
  if (!p.hostname.endsWith('.trycloudflare.com')) return ['wa', 'Không phải địa chỉ trycloudflare.com, Server 2 có thể từ chối.']
  return null
}
const dirty = c => c.url !== c.sv.url || c.tok !== c.sv.tok || c.en !== c.sv.en
const bad = c => { const x = issue(norm(c.url)); return !c.url || !!(x && x[0] == 'er') }

/* ---- trạng thái hiển thị của từng ô (đèn LED, ghi chú, nút) ---- */
function calc(c) {
  const has = c.url || c.tok, d = dirty(c), is = issue(norm(c.url)); let led, tx
  if (c.busy) { led = 'checking'; tx = 'Đang kiểm tra…' }
  else if (!has) { led = 'empty'; tx = 'Chưa nhập' }
  else if (c.ok === false) { led = 'fail'; tx = 'Lỗi kết nối' }
  else if (d) { led = c.ok ? 'ok' : 'draft'; tx = c.ok ? 'Dùng được · chưa lưu' : 'Chưa lưu' }
  else { led = c.en ? 'ok' : 'off'; tx = c.en ? 'Sẵn sàng' : 'Đã tắt' }
  const n = c.note ? [c.nk, c.note] : is ? is : (c.tok && c.tok.length < 20) ? ['wa', 'Token thường dài 32 ký tự. Kiểm tra có bị cắt không.'] : (d && S.api.on) ? ['', 'Chưa lưu lên Server 2.'] : ['', '']
  const off = !S.api.on || c.busy
  return { led, tx, n, tps: led == 'ok' && !d && c.tps ? c.tps + ' tok/s' : '', chk: off || bad(c) || !c.tok, sav: off || !d || bad(c) || !c.tok, clr: c.busy || !has }
}
const slots = computed(() => S.lm.map(calc))
const sum = computed(() => {
  const f = S.lm.filter(c => c.url && c.tok && !bad(c)).length, b = S.lm.filter(c => c.url && c.tok && bad(c)).length, d = S.lm.filter(c => dirty(c) && !bad(c) && c.tok).length
  return { text: (S.api.on ? `${f} node hợp lệ · ${d} chưa lưu` : `${f} node hợp lệ`) + (b ? ` · ${b} node sai URL` : ''), allOff: !S.api.on || !d, envOff: !f, free: `${S.lm.filter(c => !(c.url || c.tok)).length}/${S.lm.length} ô trống` }
})
const st = computed(() => (S.api.busy ? ['wa', 'Đang kết nối…'] : S.api.on ? ['ok', 'Đã kết nối'] : ['n', 'Chưa nối']))

/* ---- thao tác (mock API: setTimeout + dữ liệu cứng) ---- */
const show = reactive({}), flash = ref([]), paste = ref(''), tg = ref('free')
function set(i, f, v) { const c = S.lm[i]; c[f] = v; c.ok = null; c.note = ''; c.nk = '' }
function check(i) {
  const c = S.lm[i]; c.busy = true; c.note = ''
  setTimeout(() => {
    c.busy = false; const up = S.colab; c.ok = up; c.nk = up ? 'ok' : 'er'
    c.note = up ? `Node phản hồi bình thường (độ trễ ${(.3 + Math.random() * .6).toFixed(1)} giây).` : 'Không kết nối được tới node: hết thời gian chờ. Kiểm tra phiên Colab còn chạy.'
    if (up && !c.tps) c.tps = +(14 + Math.random() * 6).toFixed(1)
    log(`Kiểm tra LLM ${i + 1}: ${up ? 'dùng được' : 'lỗi kết nối'}`, up ? 'ok' : 'er')
  }, 700 + Math.random() * 600)
}
function save(i, quiet) {
  const c = S.lm[i]; if (!S.api.on || bad(c) || !c.tok) return 0
  c.url = norm(c.url); c.sv = { url: c.url, tok: c.tok, en: c.en }
  log(`Lưu cấu hình LLM ${i + 1} lên Server 2`, 'ok'); if (!quiet) toast(`Đã lưu LLM ${i + 1}`, 'ok'); return 1
}
function saveAll() { const n = S.lm.reduce((a, c, i) => a + (dirty(c) && c.url && c.tok ? save(i, 1) : 0), 0); if (n) toast(`Đã lưu ${n} node`, 'ok') }
function clear(i) {
  const c = S.lm[i], b = { url: c.url, tok: c.tok, en: c.en, sv: { ...c.sv }, tps: c.tps }
  Object.assign(c, { url: '', tok: '', en: false, sv: { url: '', tok: '', en: false }, ok: null, note: '', nk: '', tps: 0 })
  log(`Xóa cấu hình LLM ${i + 1}`, 'wa'); document.getElementById('lu' + i)?.focus()
  toast(`Đã xóa LLM ${i + 1}`, 'wa', 'Hoàn tác', () => Object.assign(c, b))
}
function conn() {
  const a = S.api
  if (a.on) { a.on = false; log('Ngắt kết nối Server 2', 'wa'); return toast('Đã ngắt kết nối Server 2', 'wa') }
  a.base = a.base.trim(); a.jwt = a.jwt.trim()
  if (!/^https?:\/\//i.test(a.base)) return toast('Địa chỉ API chưa đúng dạng, ví dụ http://localhost:8082/api', 'er')
  if (!a.jwt) return toast('Hãy nhập JWT hoặc Internal Token.', 'er')
  a.busy = true; setTimeout(() => { a.busy = false; a.on = true; log('Kết nối Server 2 thành công', 'ok'); toast('Đã kết nối Server 2', 'ok') }, 700)
}
function parse(t) {
  const u = [], k = []
  for (const m of t.matchAll(/URL\s*[:=]\s*(https?:\/\/[^\s"'<>]+)|(https:\/\/[a-z0-9-]+\.trycloudflare\.com)/gi)) { const x = norm((m[1] || m[2]).replace(/[),.;]+$/, '')); if (x && !u.includes(x)) u.push(x) }
  for (const m of t.matchAll(/TOKEN\s*[:=]\s*([A-Za-z0-9_\-]{16,})/gi)) if (!k.includes(m[1])) k.push(m[1])
  return u.slice(0, Math.min(u.length, k.length)).map((x, j) => ({ url: x, tok: k[j] }))
}
function fill() {
  const pr = parse(paste.value)
  if (!pr.length) return toast('Chưa nhận ra cặp URL và TOKEN. Mỗi node cần một dòng URL và một dòng TOKEN.', 'wa')
  const idx = tg.value === 'free' ? S.lm.map((c, i) => (c.url || c.tok ? -1 : i)).filter(i => i >= 0) : S.lm.map((_, i) => i).slice(+tg.value), n = Math.min(pr.length, idx.length)
  if (!n) return toast('Không còn ô trống. Hãy chọn “Từ LLM … (ghi đè)”.', 'wa')
  for (let k = 0; k < n; k++) Object.assign(S.lm[idx[k]], { url: pr[k].url, tok: pr[k].tok, en: true, ok: null, note: '', nk: '' })
  paste.value = ''; flash.value = idx.slice(0, n); setTimeout(() => { flash.value = [] }, 1800)
  toast(n < pr.length ? `Đã điền ${n}/${pr.length} node, còn ${pr.length - n} cặp chưa có ô.` : `Đã điền ${n} node. Nội dung dán đã được xóa.`, n < pr.length ? 'wa' : 'ok')
}
async function env() {
  const L = ['# Dán vào .env của Server 2 (AI Worker) rồi khởi động lại'], skip = []
  S.lm.forEach((c, i) => {
    if (!(c.url && c.tok)) return
    if (bad(c)) { skip.push(i + 1); return }
    const n = i + 1; L.push(`AI_NODE_${n}_URL=${norm(c.url)}`, `AI_NODE_${n}_TOKEN=${c.tok.trim()}`); if (n > 1) L.push(`AI_NODE_${n}_ENABLED=${c.en}`)
  })
  if (L.length == 1) return toast('Chưa có node hợp lệ để xuất. URL phải bắt đầu bằng https://', 'er')
  try { await navigator.clipboard.writeText(L.join('\n') + '\n'); toast(skip.length ? `Đã sao chép .env, bỏ qua LLM ${skip.join(', ')} vì URL chưa hợp lệ. Tệp có chứa token, đừng gửi qua kênh công khai.` : 'Đã sao chép .env. Tệp có chứa token, đừng gửi qua kênh công khai.', skip.length ? 'wa' : 'ok') }
  catch { toast('Trình duyệt chặn clipboard. Hãy dùng HTTPS hoặc sao chép thủ công.', 'wa') }
}
const phPaste = 'URL  : https://ten-ngau-nhien.trycloudflare.com\nTOKEN: dán token ở đây'
</script>

<template>
  <div class="lmhead">
    <section class="card" aria-labelledby="lm-t1">
      <div class="card__head"><h3 id="lm-t1">Server 2 · AI Worker</h3><span class="b" :class="st[0]" role="status"><i v-if="S.api.busy" class="spin"></i>{{ st[1] }}</span></div>
      <div class="lmpills"><span class="b code n">model: {{ S.api.on ? 'qwen2.5-14b-instruct-q4_K_M' : 'chưa kết nối' }}</span><span class="b code n">mode: {{ S.api.on ? 'async · Redis queue' : '-' }}</span></div>
      <label>Địa chỉ API<input id="lm-base" class="mono" inputmode="url" spellcheck="false" autocomplete="off" v-model="S.api.base" :readonly="S.api.on"></label>
      <label>JWT hoặc Internal Token (Bearer)
        <div class="pw">
          <input id="lm-jwt" class="mono" :type="show['lm-jwt'] ? 'text' : 'password'" spellcheck="false" autocomplete="off" placeholder="Nhập ACCESS JWT hoặc AI_INTERNAL_TOKEN" v-model="S.api.jwt" :readonly="S.api.on">
          <button class="btn sm" type="button" :aria-pressed="String(!!show['lm-jwt'])" @click="show['lm-jwt'] = !show['lm-jwt']">{{ show['lm-jwt'] ? 'Ẩn' : 'Hiện' }}</button>
        </div>
      </label>
      <button class="btn w100 lmact" :class="{ pri: !S.api.on }" :disabled="S.api.busy" @click="conn">{{ S.api.on ? 'Ngắt kết nối' : 'Kết nối' }}</button>
    </section>
    <section class="card" aria-labelledby="lm-t2">
      <div class="card__head"><h3 id="lm-t2">Dán kết quả từ Colab</h3><span class="b n" id="lm-free">{{ sum.free }}</span></div>
      <p class="mut">Dán hai dòng URL và TOKEN mà notebook Colab in ra. Hỗ trợ dán nhiều node cùng lúc. Nội dung dán tự xóa ngay sau khi điền.</p>
      <textarea id="lm-paste" class="area" spellcheck="false" autocomplete="off" aria-label="Kết quả từ Colab" :placeholder="phPaste" v-model="paste"></textarea>
      <div class="bar lmact">
        <label class="lmtg">Điền vào<select id="lm-tg" v-model="tg"><option value="free">Ô trống đầu tiên</option><option v-for="(_, i) in S.lm" :key="i" :value="String(i)">Từ LLM {{ i + 1 }} (ghi đè)</option></select></label>
        <button class="btn pri" @click="fill">Điền vào ô</button>
      </div>
    </section>
  </div>
  <p class="lmwarn" :hidden="S.api.on">Chưa nối Server 2. Bạn vẫn dán và xuất dạng .env được; muốn kiểm tra và lưu vào bộ nhớ worker thì hãy kết nối ở trên.</p>

  <div class="rack" aria-label="Các node Colab">
    <article v-for="(c, i) in S.lm" :key="i" class="slot" :class="{ flash: flash.includes(i) }" :id="'slot' + i" :data-led="slots[i].led">
      <div class="slot__rail"><div class="slot__id"><span class="led" aria-hidden="true"></span><b>LLM {{ i + 1 }}</b></div><span class="slot__st">{{ slots[i].tx }}</span><span class="slot__tps">{{ slots[i].tps }}</span></div>
      <div class="slot__body">
        <label>URL dịch vụ suy luận<input :id="'lu' + i" class="mono" type="url" inputmode="url" spellcheck="false" autocomplete="off" placeholder="https://ten-ngau-nhien.trycloudflare.com" :value="c.url" @input="set(i, 'url', $event.target.value)"></label>
        <label>Token
          <div class="pw">
            <input :id="'tk' + i" class="mono" :type="show['tk' + i] ? 'text' : 'password'" spellcheck="false" autocomplete="off" placeholder="Dán token từ Colab" :value="c.tok" @input="set(i, 'tok', $event.target.value)">
            <button class="btn sm" type="button" :aria-pressed="String(!!show['tk' + i])" @click="show['tk' + i] = !show['tk' + i]">{{ show['tk' + i] ? 'Ẩn' : 'Hiện' }}</button>
          </div>
        </label>
        <small class="slot__note" :class="slots[i].n[0]" aria-live="polite">{{ slots[i].n[1] }}</small>
      </div>
      <div class="slot__acts">
        <label class="sw"><input type="checkbox" role="switch" :checked="c.en" @change="set(i, 'en', $event.target.checked)"><span class="sw__ui" aria-hidden="true"></span><span>Dùng node này</span></label>
        <div class="slot__btns">
          <button class="btn sm" :disabled="slots[i].chk" @click="check(i)">Kiểm tra</button>
          <button class="btn sm pri" :disabled="slots[i].sav" @click="save(i)">Lưu</button>
          <button class="btn sm del" :disabled="slots[i].clr" @click="clear(i)">Xóa</button>
        </div>
      </div>
    </article>
  </div>

  <div class="lmbar">
    <span id="lm-sum" class="mut" role="status">{{ sum.text }}</span>
    <div class="bar"><button id="lm-env" class="btn" :disabled="sum.envOff" @click="env">Sao chép dạng .env</button><button id="lm-all" class="btn pri" :disabled="sum.allOff" @click="saveAll">Lưu tất cả</button></div>
  </div>
</template>
