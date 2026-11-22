<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { TS } from '../data/mock.js'
import { S, act, code, runAI } from '../store.js'

const router = useRouter()
const live = /pending|running/
const counts = computed(() => ({ all: S.tasks.length, running: act(), succeeded: S.tasks.filter(t => t.st == 'succeeded').length, failed: S.tasks.filter(t => t.st == 'failed').length }))
const tabs = [['all', 'Tất cả'], ['running', 'Đang chạy'], ['succeeded', 'Hoàn tất'], ['failed', 'Thất bại']]
const rows = computed(() => S.tasks.filter(t => (S.tf === 'all' ? true : S.tf === 'running' ? live.test(t.st) : t.st === S.tf)))
const hasSug = t => S.sug.some(s => s.qid == t.qid)
</script>

<template>
  <div class="card">
    <div class="card__head">
      <h3>Theo dõi tác vụ AI bất đồng bộ</h3>
      <small class="mut"><span class="b ok" style="font-size:.75rem">● Tự động cập nhật thời gian thực</span></small>
    </div>
    <p class="mut">Mỗi yêu cầu có một mã tác vụ để theo dõi từ lúc tiếp nhận đến khi có kết quả. Bản demo mô phỏng các trạng thái của bảng ntt_ai_tasks trong MariaDB.</p>
    <div class="chips">
      <button v-for="[k, l] in tabs" :key="k" type="button" class="chip" :class="{ on: S.tf === k }" @click="S.tf = k">{{ l }} ({{ counts[k] }})</button>
    </div>
    <div v-if="rows.length" class="tw">
      <table>
        <thead><tr><th>Mã tác vụ</th><th>Câu hỏi</th><th>Bắt đầu</th><th>Trạng thái</th><th>Thời lượng</th><th>Kết quả</th><th>Hành động</th></tr></thead>
        <tbody>
          <tr v-for="t in rows" :key="t.id">
            <td><b>{{ t.id }}</b></td>
            <td><b>{{ code({ id: t.qid }) }}</b></td>
            <td>{{ t.started || '—' }}</td>
            <td><span class="b" :class="TS[t.st][1]"><template v-if="live.test(t.st)"><i class="spin"></i> </template>{{ TS[t.st][0] }}</span></td>
            <td>{{ t.ms ? (t.ms / 1000).toFixed(1) + ' giây' : '…' }}</td>
            <td>{{ t.msg || 'Đang chờ worker xử lý…' }}</td>
            <td>
              <button v-if="t.st === 'failed'" class="btn sm pri" @click="runAI([t.qid])">Thử lại</button>
              <button v-else-if="t.st === 'succeeded' && hasSug(t)" class="btn sm" @click="router.push('/review')">Xem đề xuất</button>
              <template v-else>—</template>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-else class="empty-box">
      <p class="mut">Chưa có tác vụ nào thuộc trạng thái này.</p>
      <button class="btn sm pri" @click="router.push('/bank')">Chọn câu hỏi để kiểm định</button>
    </div>
  </div>
</template>
