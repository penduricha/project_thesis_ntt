<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { TS, LV } from '../data/mock.js'
import { t as tr } from '../i18n/index.js'
import { S, act, code, runAI } from '../store.js'

const router = useRouter()
const live = /pending|running/
const counts = computed(() => ({ all: S.tasks.length, running: act(), succeeded: S.tasks.filter(t => t.st === 'succeeded').length, failed: S.tasks.filter(t => t.st === 'failed').length }))
const tabs = ['all', 'running', 'succeeded', 'failed']
const rows = computed(() => S.tasks.filter(t => (S.tf === 'all' ? true : S.tf === 'running' ? live.test(t.st) : t.st === S.tf)))
const hasSug = t => S.sug.some(s => s.qid === t.qid)
const taskStatusLabel = status => (Object.prototype.hasOwnProperty.call(TS, status) || status === 'cancelled' || status === 'unknown') ? tr('taskStatus.' + status) : status
const taskStatusClass = status => status === 'cancelled' ? 'wa' : TS[status]?.[1] || 'n'
const taskMessage = task => {
  if (!task.msgKey) return task.msg || tr('search.pendingWorker')
  const params = { ...task.msgParams }
  if (task.msgKey === 'job.foundDifficulty') {
    const index = LV.indexOf(params.d)
    if (index >= 0) params.d = tr('difficulty.' + index)
  }
  return tr(task.msgKey, params)
}
</script>

<template>
  <div class="card">
    <div class="card__head">
      <h3>{{ $t('ai.tracking') }}</h3>
      <small class="mut"><span class="b ok" style="font-size:.75rem">{{ $t('dash.liveDot') }}</span></small>
    </div>
    <p class="mut">{{ $t('ai.taskTrackingDesc') }}</p>
    <div class="chips">
      <button v-for="k in tabs" :key="k" type="button" class="chip" :class="{ on: S.tf === k }" @click="S.tf = k">{{ $t('taskStatus.' + k) }} ({{ counts[k] }})</button>
    </div>
    <div v-if="rows.length" class="tw tw--tasks">
      <table>
        <thead><tr><th>{{ $t('ai.taskId') }}</th><th>{{ $t('nav.group.questions') }}</th><th>{{ $t('common.start') }}</th><th>{{ $t('common.status') }}</th><th>{{ $t('common.duration') }}</th><th>{{ $t('common.result') }}</th><th>{{ $t('common.action') }}</th></tr></thead>
        <tbody>
          <tr v-for="t in rows" :key="t.id">
            <td :data-l="$t('ai.taskId')"><b>{{ t.id }}</b></td>
            <td :data-l="$t('nav.group.questions')"><b>{{ code({ id: t.qid }) }}</b></td>
            <td :data-l="$t('common.start')">{{ t.started || '—' }}</td>
            <td :data-l="$t('common.status')"><span class="b" :class="taskStatusClass(t.st)"><template v-if="live.test(t.st)"><i class="spin"></i> </template>{{ taskStatusLabel(t.st) }}</span></td>
            <td :data-l="$t('common.duration')">{{ t.ms ? (t.ms / 1000).toFixed(1) + $t('search.secondsSuffix') : '…' }}</td>
            <td :data-l="$t('common.result')">{{ taskMessage(t) }}</td>
            <td :data-l="$t('common.action')">
              <button v-if="t.st === 'failed'" class="btn sm pri" @click="runAI([t.qid])">{{ $t('common.retry') }}</button>
              <button v-else-if="t.st === 'succeeded' && hasSug(t)" class="btn sm" @click="router.push('/review')">{{ $t('ai.viewProposal') }}</button>
              <template v-else>—</template>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-else class="empty-box">
      <p class="mut">{{ $t('ai.emptyState') }}</p>
      <button class="btn sm pri" @click="router.push('/bank')">{{ $t('ai.selectQuestions') }}</button>
    </div>
  </div>
</template>
