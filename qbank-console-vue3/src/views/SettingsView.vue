<script setup>
import { computed, onMounted } from 'vue'
import { TFA_VIEW, TFA_ICON } from '../data/account.js'
import { S, asUser, previewRole, log, toast, tfaOpen, tfaAsk, tfaDone, loadTfaStatus } from '../store.js'
import { auth } from '../auth/authStore.js'
import Ico from '../components/Ico.vue'

const st = computed(() => S.tfa.st), v = computed(() => TFA_VIEW[st.value])
const left = computed(() => { const t = Math.max(0, Math.ceil((S.tfa.until - S.now) / 1000)); return String(Math.floor(t / 60)).padStart(2, '0') + ':' + String(t % 60).padStart(2, '0') })
const gv = computed(() => (S.role === 'gv' && asUser() ? `Đang mô phỏng phạm vi môn của ${asUser().name}.` : ''))
// Chỉ chế độ demo mới có nút "mô phỏng ứng dụng đã xác nhận"; bản thật chờ Flutter xác nhận qua SSE/poll.
const demo = computed(() => auth.isDemo || S.dataMode === 'mock')
onMounted(loadTfaStatus)
const opts = [
  ['notifyTasks', 'Thông báo khi tác vụ AI hoàn tất', 'Hiện thông báo khi một yêu cầu kiểm định kết thúc (lỗi luôn được báo).', 'Đổi tùy chọn thông báo'],
  ['confirmDelete', 'Hỏi xác nhận trước khi xóa câu hỏi', 'Tắt đi thì câu hỏi bị xóa ngay và vẫn có nút Hoàn tác. Xóa nhiều câu cùng lúc luôn hỏi xác nhận.', 'Đổi tùy chọn xác nhận xóa'],
]
function setOpt(k, on, msg) { S[k] = on; log(msg, 'info'); toast('Đã lưu tùy chọn', 'ok') }
</script>

<template>
  <div class="card set">
    <section class="set__row" aria-labelledby="tfa-t">
      <div class="set__intro"><h3 id="tfa-t" class="set__t">Xác thực hai yếu tố</h3><p class="set__d">Thêm bước nhập mã từ ứng dụng Authenticator mỗi khi đăng nhập.</p></div>
      <div class="set__body">
        <div class="set__tfa">
          <div class="tfa" :class="'tfa--' + v.cls">
            <div class="tfa__main">
              <span class="tfa__ic"><Ico :d="TFA_ICON[st]" /></span>
              <div class="tfa__txt">
                <p class="tfa__st" role="status"><span>Trạng thái:</span><span class="ubd" :class="'ubd--' + v.bd">{{ v.text }}</span></p>
                <p class="tfa__desc">{{ v.desc }}</p>
                <p v-if="st == 'PENDING'" class="tfa__hint">Còn <b id="tfa-cd">{{ left }}</b> để hoàn tất. Trong lúc chờ, đăng nhập chưa yêu cầu mã xác thực.</p>
              </div>
            </div>
            <button v-if="st == 'NOT_ENABLED'" type="button" class="btn pri" @click="tfaOpen">Kích hoạt 2FA</button>
            <button v-else-if="st == 'PENDING'" type="button" class="btn" @click="tfaAsk">Hủy và cài lại</button>
          </div>
          <p v-if="st == 'PENDING' && demo" class="mut" style="margin:0;font-size:var(--fs-sm)">Bản demo: <button type="button" class="btn sm" @click="tfaDone">Mô phỏng ứng dụng đã xác nhận</button></p>
          <p v-else-if="st == 'PENDING'" class="mut" style="margin:0;font-size:var(--fs-sm)">Đang chờ ứng dụng trên điện thoại xác nhận. Trang tự cập nhật khi xác nhận xong.</p>
        </div>
      </div>
    </section>
    <section class="set__row" aria-labelledby="role-t">
      <div class="set__intro"><h3 id="role-t" class="set__t">Chế độ xem theo vai trò</h3><p class="set__d">Chỉ đổi giao diện xem thử, không đổi quyền của tài khoản.</p></div>
      <div class="set__body">
        <label for="preview-role">Vai trò xem thử</label>
        <select id="preview-role" :value="S.role" @change="previewRole($event.target.value)"><option value="admin">Quản trị viên</option><option value="gv">Giảng viên</option></select>
        <small v-if="gv">{{ gv }}</small>
      </div>
    </section>
    <section class="set__row" aria-labelledby="opt-t">
      <div class="set__intro"><h3 id="opt-t" class="set__t">Thông báo và xác nhận</h3><p class="set__d">Lưu tự động khi bạn bật hoặc tắt.</p></div>
      <div class="set__body">
        <label v-for="[k, t, d, msg] in opts" :key="k" class="sopt">
          <span class="sopt__t"><b>{{ t }}</b><small>{{ d }}</small></span>
          <span class="sw"><input type="checkbox" role="switch" :checked="S[k]" @change="setOpt(k, $event.target.checked, msg)"><span class="sw__ui" aria-hidden="true"></span></span>
        </label>
      </div>
    </section>
  </div>
</template>
