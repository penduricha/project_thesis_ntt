<script setup>
/**
 * Khung xương (skeleton) cho danh sách đang tải.
 *
 * VÌ SAO DÙNG SKELETON THAY SPINNER
 *   - Spinner chỉ nói "đang chờ", không cho biết SẮP hiện ra cái gì.
 *   - Skeleton vẽ trước bố cục của nội dung thật, nên khi dữ liệu về, khung đã
 *     đúng chỗ => không bị "nhảy" (layout shift). Đây là lý do đề bài yêu cầu
 *     hạn chế spinner.
 *
 * KHẢ NĂNG TIẾP CẬN
 *   Khung xương là thuần trang trí (aria-hidden). Trạng thái "đang tải" được báo
 *   qua `role="status"` + chữ ẩn đọc được bằng trình đọc màn hình, nên người dùng
 *   screen reader không phải nghe 20 dòng khung rỗng.
 *
 * TÔN TRỌNG prefers-reduced-motion
 *   Hiệu ứng nhấp nháy tắt khi người dùng chọn giảm chuyển động.
 */
defineProps({
  rows: { type: Number, default: 6 },
  // 'qrow' = hàng danh sách câu hỏi, 'table' = bảng tài khoản
  variant: { type: String, default: 'qrow' },
  label: { type: String, default: '' },
})
</script>

<template>
  <div class="skel" :class="'skel--' + variant" aria-hidden="true">
    <div v-for="i in rows" :key="i" class="skel__row">
      <template v-if="variant === 'table'">
        <span class="skel__bar" style="width: 5.5rem"></span>
        <span class="skel__bar" style="width: 9rem"></span>
        <span class="skel__bar grow"></span>
        <span class="skel__bar" style="width: 4rem"></span>
        <span class="skel__bar" style="width: 6rem"></span>
      </template>
      <template v-else>
        <span class="skel__box"></span>
        <span class="skel__main">
          <span class="skel__bar" style="width: 62%"></span>
          <span class="skel__bar" style="width: 38%"></span>
        </span>
        <span class="skel__bar" style="width: 5rem"></span>
      </template>
    </div>
  </div>
  <p v-if="label" class="sr-only" role="status" aria-live="polite">{{ label }}</p>
</template>

<style scoped>
.skel { display: grid; gap: .55rem; }
.skel__row {
  display: flex; align-items: center; gap: .8rem;
  /* Cùng chiều cao với hàng thật để lúc dữ liệu về không bị nhảy bố cục. */
  min-height: 3.25rem; padding: .7rem .85rem;
  border: 1px solid var(--border); border-radius: var(--radius);
  background: var(--surface);
}
.skel__box { flex: none; width: 1.05rem; height: 1.05rem; border-radius: 4px; background: var(--surface-sunken); }
.skel__main { flex: 1 1 auto; display: grid; gap: .4rem; min-width: 0; }
.skel__bar {
  display: block; height: .7rem; border-radius: 999px; background: var(--surface-sunken);
}
.skel__bar.grow { flex: 1 1 auto; }

/* Một nhịp thở chung cho cả khối: rẻ hơn nhiều so với animation từng phần tử. */
.skel__row > *, .skel__main > * { animation: skel-pulse 1.15s ease-in-out infinite; }
@keyframes skel-pulse { 0%, 100% { opacity: 1 } 50% { opacity: .5 } }

@media (prefers-reduced-motion: reduce) {
  .skel__row > *, .skel__main > * { animation: none; }
}
</style>
