<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { COURSES, S, act, clearFilters, log, loadDashboard } from '../store.js'
const router = useRouter()
// Dashboard ưu tiên số liệu thật của GET /dashboard/summary (cache Redis 30s) khi đã ghép backend;
// chưa ghép / demo thì tính từ mảng câu hỏi mẫu như cũ.
const dash = computed(() => S.dashboard)
const total = computed(() => (dash.value ? dash.value.totalActive : S.q.length))
const okC = computed(() => (dash.value ? (dash.value.byValidation?.clean ?? 0) : S.q.filter(x => x.st === 'ok').length))
const pct = computed(() => (total.value ? Math.round(okC.value / total.value * 100) : 0))
const sugC = computed(() => (dash.value ? dash.value.pendingSuggestions : S.sug.length))
const actC = computed(() => (dash.value ? dash.value.runningTasks : act()))
const archived = computed(() => dash.value?.totalArchived ?? 0)
const totalTopics = computed(() => COURSES.reduce((a, c) => a + c.topics.length, 0))
const now = computed(() => { S.log.length; return new Date().toLocaleTimeString('vi-VN') })
const toBank = () => { clearFilters(); router.push('/bank') }
const toOk = () => { clearFilters(); S.f.s = 'ok'; router.push('/bank') }
// Ở chế độ thật, trạng thái dịch vụ do backend/Server 2 quyết định: không cho bật/tắt giả lập.
const toggleColab = () => {
  if (S.dataMode === 'real') return
  S.colab = !S.colab; log(S.colab ? 'Kết nối lại dịch vụ LLM' : 'Tạm ngắt kết nối dịch vụ LLM', S.colab ? 'ok' : 'er')
}
</script>

<template>
  <div class="grid">
    <button type="button" class="card stat" @click="toBank">
      <span class="mut">{{ $t('dash.totalQuestions') }}</span>
      <b :class="total ? '' : 'zero'">{{ total }}</b>
      <span class="stat__sub">{{ $t('dash.courseSummary', { n: COURSES.length, m: totalTopics }) }}<template v-if="archived">{{ $t('dash.archivedCount', { n: archived }) }}</template></span>
    </button>
    <button type="button" class="card stat" @click="toOk">
      <span class="mut">{{ $t('bank.verified') }}</span>
      <b :class="okC ? '' : 'zero'">{{ okC }}</b>
      <span class="stat__sub">{{ $t('dash.questionSummary', { n: total, p: pct }) }}</span>
    </button>
    <button type="button" class="card stat" @click="router.push('/review')">
      <span class="mut">{{ $t('ai.pendingProposals') }}</span>
      <b :class="sugC ? '' : 'zero'">{{ sugC }}</b>
      <span class="stat__sub">{{ sugC ? sugC + $t('dash.pendingReview') : $t('dash.noPendingSug') }}</span>
    </button>
    <button type="button" class="card stat" @click="router.push('/tasks')">
      <span class="mut">{{ $t('ai.aiRunning') }}</span>
      <b :class="actC ? '' : 'zero'">{{ actC }}</b>
      <span class="stat__sub">{{ actC ? actC + $t('dash.queueCount') : $t('dash.queueIdle') }}</span>
    </button>
  </div>

  <div class="cols dashcols">
    <div class="card">
      <h3>{{ $t('llm.inferenceService') }}</h3>
      <div class="flow-box">{{ $t('brand.stack') }} <span>→</span> {{ $t('brand.server1') }} <span>→</span> {{ $t('ai.taskTable') }} <span>→</span> {{ $t('brand.server2') }} <span>→</span> {{ $t('llm.brandMark') }}</div>
      <div class="two_fa svc" :class="S.colab ? 'two_fa--on' : 'two_fa--off'">
        <div>
          <p class="two_fa__status"><span>{{ $t('llm.llmStatus') }}</span><span class="b" :class="S.colab ? 'ok' : 'er'">{{ S.colab ? $t('dash.running') : $t('dash.disconnected') }}</span></p>
          <p class="two_fa__desc">{{ S.colab ? $t('dash.ready') : $t('dash.aiDown') }}</p>
        </div>
        <div>
          <button class="btn" :class="S.colab ? '' : 'pri'" :disabled="S.dataMode === 'real'" @click="toggleColab">{{ S.colab ? $t('dash.pauseConnection') : $t('dash.reconnect') }}</button>
          <small style="margin-top:.35rem;text-align:center">{{ S.dataMode === 'real' ? $t('dash.serverReported') : $t('dash.simulateOutage') }}</small>
          <button v-if="S.dataMode === 'real'" class="btn sm" style="margin-top:.35rem" @click="loadDashboard">{{ $t('dash.refresh') }}</button>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card__head">
        <h3>{{ $t('dash.activity') }}</h3>
        <small class="mut">{{ $t('dash.updatedAt', { t: now }) }}</small>
      </div>
      <ul v-if="S.log.length" class="lg">
        <li v-for="(l, i) in S.log" :key="i"><span class="dot" :class="l[2] || 'info'"></span><div><span class="mut">{{ l[0] }}</span> · {{ l[1] }}</div></li>
      </ul>
      <div v-else class="empty-box" style="padding:1.5rem">
        <p class="mut">{{ $t('dash.noActivity') }}</p>
        <button class="btn sm pri" @click="router.push('/bank')">{{ $t('ai.sendFirst') }}</button>
      </div>
    </div>
  </div>
</template>
