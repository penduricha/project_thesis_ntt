<script setup>
/**
 * Trang 404 cho mọi đường dẫn không có trong bộ định tuyến.
 *
 * Vì sao cần: trước bản này, router chỉ khai báo 13 route cụ thể và KHÔNG có route bắt-tất-cả.
 * Người dùng gõ sai (ví dụ /fwiehgoihergplij) sẽ vào hash không khớp route nào, Vue Router
 * render RouterView rỗng ⇒ màn hình trắng không lời giải thích.
 *
 * Ba nút của trình duyệt (← → ↻) là thứ dễ hỏng nhất khi làm trang 404, nên xử lý riêng:
 *
 *  - ← (Back):  vue-router 4 ghi `history.state.back`. Nếu có ⇒ dùng router.back() để quay lại
 *    đúng trang trước TRONG ứng dụng. Nếu KHÔNG có (người dùng dán thẳng link lỗi vào thanh địa
 *    chỉ) thì router.back() sẽ đẩy họ ra khỏi website — nên phải về trang chủ thay thế.
 *  - → (Forward): vẫn hoạt động vì mọi chuyển trang đều đi qua router (push/replace), nghĩa là
 *    mỗi entry đều có state. Không cần can thiệp.
 *  - ↻ (Reload): đường dẫn lỗi phải hiển thị lại trang 404 chứ không trắng trang. Điều này phụ
 *    thuộc vào `requestedPath` đọc từ `route.path` (phản ánh đúng hash hiện tại) chứ KHÔNG lưu
 *    vào biến lúc created() — nếu lưu một lần thì sau reload/biến mất và hiện lại link cũ sai.
 *
 * Dùng `history.state.back` thay vì `window.history.length > 1` vì cách sau đếm cả những lần
 * điều hướng ngoài ứng dụng (ví dụ từ tab khác mở sang) nên hay cho kết quả sai.
 */
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { auth } from '../auth/authStore.js'
import { t } from '../i18n/index.js'

const route = useRoute()
const router = useRouter()

/* Hiển thị nguyên văn đường dẫn người dùng gõ để họ thấy mình sai ở đâu.
   `route.path` là hash path, không có query string ⇒ không lộ tham số nhạy cảm. */
const requestedPath = computed(() => route.fullPath || '')

/* Nơi quay về: đã đăng nhập ⇒ bảng điều khiển, chưa ⇒ trang đăng nhập.
   Tính lại mỗi lần bấm (không chốt lúc tạo) để đúng cả khi phiên vừa hết hạn. */
function homeTarget() {
  return auth.isAuthenticated ? { name: 'dash' } : { name: 'login' }
}
const homeLabel = computed(() => t(auth.isAuthenticated ? 'error.goHome' : 'error.goLogin'))

function goHome() {
  /* replace chứ không push: nếu push, bấm ← của trình duyệt sẽ quay lại đúng trang 404 vừa
     rời, tạo vòng lặp khó chịu. replace ghi đè entry lỗi bằng entry hợp lệ. */
  router.replace(homeTarget()).catch(() => {})
}

function goBack() {
  /* history.state.back có giá trị ⇔ trang trước là một trang TRONG ứng dụng. */
  if (window.history.state && window.history.state.back) {
    router.back()
  } else {
    goHome()
  }
}

function retry() {
  /* ↻ của trình duyệt nạp lại cả tài liệu; nút này chỉ cần nạp lại phần định tuyến.
     Dùng replace để giữ nguyên đường dẫn sai (người dùng có thể sửa trên thanh địa chỉ). */
  window.location.reload()
}
</script>

<template>
  <section class="nf" aria-labelledby="nf-title">
    <div class="nf__card">
      <!-- Số 404 dùng ký tự thật.
           Bản trước vẽ số 0 bằng một <span> rỗng + border-radius để "đồng bộ độ dày nét",
           nhưng kết quả là một vòng tròn rỗng trông như CHỮ O chứ không phải số 0 — người
           dùng đọc thành "4O4". Font Be Vietnam Pro đã có sẵn số 0 cân với số 4, không cần vẽ. -->
      <p class="nf__code" aria-hidden="true">404</p>

      <span class="nf__badge">{{ $t('error.code404') }}</span>
      <h1 id="nf-title" class="nf__title">{{ $t('error.notFound') }}</h1>
      <p class="nf__desc">
        {{ $t('error.notFoundDesc') }}
      </p>

      <p v-if="requestedPath" class="nf__path">
        <span>{{ $t('error.path') }}</span>
        <code :title="requestedPath">{{ requestedPath }}</code>
      </p>

      <div class="nf__actions">
        <button type="button" class="btn pri" @click="goHome">{{ homeLabel }}</button>
        <button type="button" class="btn" @click="goBack">{{ $t('auth.back') }}</button>
        <button type="button" class="btn ghost" @click="retry">{{ $t('common.reload') }}</button>
      </div>
    </div>
  </section>
</template>

<style scoped>
/* Trang này nằm trong <RouterView> của App.vue nên thừa hưởng topbar/sidebar/footer và toàn bộ
   token màu (:root). Chỉ cần dựng khối nội dung, không tự vẽ lại theme. */
/* CĂN GIỮA — hai trường hợp hiển thị khác nhau nên phải xử lý riêng:
   1) ĐÃ đăng nhập: trang nằm trong .page → .content, tức phía dưới topbar (--topbar-h) và phía
      trên footer. Dùng min-height:100vh ở đây (bản trước) là SAI: 100vh là chiều cao CẢ cửa sổ,
      nên khối cao hơn vùng trống thật đúng bằng topbar + footer ⇒ bị đẩy xuống dưới, lệch tâm
      (đúng như ảnh chụp màn hình). Ở trường hợp này để flex:1 lo việc giãn — .page và #view đã
      được khai báo display:flex;flex-direction:column.
   2) CHƯA đăng nhập: App.vue vẽ trang "trần" (không .page/.content) nên không có flex nào để
      giãn; phải tự đặt chiều cao tối thiểu. Dùng 100dvh (không phải 100vh) vì trên Safari/iOS
      thanh địa chỉ động làm vh sai, khiến khối bị cắt mất phần dưới. */
.nf{display:grid;place-items:center;flex:1;width:100%;padding:var(--space-4) var(--space-2);min-height:100dvh}
/* Trong khung ứng dụng (.content của App.vue) thì KHÔNG cần và KHÔNG được dùng 100dvh: vùng đó
   đã ngắn hơn cửa sổ đúng bằng topbar + footer, nên 100dvh sẽ tràn và đẩy khối xuống dưới.
   Ở đây flex:1 (từ .page → #view → .nf) đã lo việc giãn hết chiều cao còn lại. */
.content .nf{min-height:0}
.nf__card{display:grid;justify-items:center;gap:.75rem;text-align:center;max-width:34rem;padding:var(--space-5) var(--space-3);background:var(--surface);border:1px solid var(--border);border-radius:12px;box-shadow:var(--shadow-card)}
.nf__code{margin:0;font-size:clamp(3rem,10vw,4.5rem);font-weight:700;line-height:1;color:var(--primary-ink);letter-spacing:.02em}
.nf__badge{padding:.15rem .6rem;border-radius:999px;background:var(--danger-soft);border:1px solid var(--danger-border);color:var(--danger-strong);font-size:var(--fs-xs);font-weight:600}
.nf__title{margin:0;font-size:1.5rem;color:var(--text)}
.nf__desc{margin:0;color:var(--text-muted);font-size:var(--fs-sm);line-height:1.6}
/* max-width + overflow-wrap: đường dẫn người dùng gõ có thể rất dài, không được đẩy vỡ khung */
.nf__path{display:flex;flex-wrap:wrap;justify-content:center;gap:.4rem;margin:0;max-width:100%;font-size:var(--fs-xs);color:var(--text-muted)}
.nf__path code{max-width:100%;padding:.15rem .5rem;background:var(--surface-hover);border:1px solid var(--border);border-radius:6px;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;overflow-wrap:anywhere;color:var(--text)}
.nf__actions{display:flex;flex-wrap:wrap;justify-content:center;gap:.5rem;margin-top:.5rem}
@media(max-width:479.98px){.nf__actions{display:grid;width:100%}.nf__actions .btn{width:100%}}
</style>
