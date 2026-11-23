<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { COURSES, S, act, clearFilters, log, loadDashboard } from '../store.js'
const router = useRouter()
// Dashboard ưu tiên số liệu thật của GET /dashboard/summary (cache Redis 30s) khi đã ghép backend;
// chưa ghép / demo thì tính từ mảng câu hỏi mẫu như cũ.
const dash = computed(() => S.dashboard)
const total = computed(() => (dash.value ? dash.value.totalActive : S.q.length))
const okC = computed(() => (dash.value ? (dash.value.byValidation?.clean ?? 0) : S.q.filter(x => x.st == 'ok').length))
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
      <span class="mut">Tổng số câu hỏi</span>
      <b :class="total ? '' : 'zero'">{{ total }}</b>
      <span class="stat__sub">{{ COURSES.length }} môn học · {{ totalTopics }} chương<template v-if="archived"> · {{ archived }} đã lưu trữ</template></span>
    </button>
    <button type="button" class="card stat" @click="toOk">
      <span class="mut">Đã kiểm định</span>
      <b :class="okC ? '' : 'zero'">{{ okC }}</b>
      <span class="stat__sub">{{ okC }}/{{ total }} câu · đạt {{ pct }}%</span>
    </button>
    <button type="button" class="card stat" @click="router.push('/review')">
      <span class="mut">Đề xuất chờ duyệt</span>
      <b :class="sugC ? '' : 'zero'">{{ sugC }}</b>
      <span class="stat__sub">{{ sugC ? sugC + ' câu cần giảng viên xem xét' : 'Không có đề xuất tồn đọng' }}</span>
    </button>
    <button type="button" class="card stat" @click="router.push('/tasks')">
      <span class="mut">Tác vụ AI đang chạy</span>
      <b :class="actC ? '' : 'zero'">{{ actC }}</b>
      <span class="stat__sub">{{ actC ? actC + ' tác vụ trong hàng đợi' : 'Hàng đợi đang rảnh' }}</span>
    </button>
  </div>

  <div class="cols dashcols">
    <div class="card">
      <h3>Dịch vụ suy luận (Qwen2.5-14B)</h3>
      <div class="flow-box">Vue.js <span>→</span> Server 1 <span>→</span> Tác vụ (MariaDB) <span>→</span> Server 2 <span>→</span> LLM (Qwen2.5)</div>
      <div class="two_fa svc" :class="S.colab ? 'two_fa--on' : 'two_fa--off'">
        <div>
          <p class="two_fa__status"><span>Trạng thái kết nối LLM:</span><span class="b" :class="S.colab ? 'ok' : 'er'">{{ S.colab ? 'Đang hoạt động' : 'Đã ngắt kết nối' }}</span></p>
          <p class="two_fa__desc">{{ S.colab ? 'Hệ thống sẵn sàng tiếp nhận yêu cầu kiểm định câu hỏi và tìm kiếm ngôn ngữ tự nhiên.' : 'Dịch vụ AI tạm ngắt: các tác vụ mới sẽ báo lỗi có kiểm soát, chức năng quản lý câu hỏi vẫn hoạt động.' }}</p>
        </div>
        <div>
          <button class="btn" :class="S.colab ? '' : 'pri'" :disabled="S.dataMode === 'real'" @click="toggleColab">{{ S.colab ? 'Tạm ngắt kết nối' : 'Kết nối lại' }}</button>
          <small style="margin-top:.35rem;text-align:center">{{ S.dataMode === 'real' ? 'Trạng thái do máy chủ báo (không mô phỏng)' : 'Mô phỏng sự cố dịch vụ suy luận' }}</small>
          <button v-if="S.dataMode === 'real'" class="btn sm" style="margin-top:.35rem" @click="loadDashboard">Làm mới số liệu</button>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card__head">
        <h3>Nhật ký thao tác</h3>
        <small class="mut">Cập nhật lúc {{ now }}</small>
      </div>
      <ul v-if="S.log.length" class="lg">
        <li v-for="(l, i) in S.log" :key="i"><span class="dot" :class="l[2] || 'info'"></span><div><span class="mut">{{ l[0] }}</span> · {{ l[1] }}</div></li>
      </ul>
      <div v-else class="empty-box" style="padding:1.5rem">
        <p class="mut">Chưa có thao tác nào được ghi nhận.</p>
        <button class="btn sm pri" @click="router.push('/bank')">Gửi câu hỏi đầu tiên đi kiểm định</button>
      </div>
    </div>
  </div>
</template>
