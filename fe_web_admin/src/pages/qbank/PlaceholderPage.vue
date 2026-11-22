<script setup>
import QbankLayout from '@/components/qbank/QbankLayout.vue';

/**
 * Màn đã có thiết kế trong `UI_Accept_v11.html` nhưng CHƯA có API ở backend.
 *
 * Cố ý không dựng dữ liệu giả: hiển thị rõ phần còn thiếu để không ai tưởng tính năng đã chạy,
 * và ghi lại việc cần làm. Xem `docs/doi-chieu-v10-va-vue-demo-voi-backend.md` mục 5.
 */
defineProps({
    title: { type: String, required: true },
    subtitle: { type: String, default: '' },
    needs: { type: Array, default: () => [] },
    docRef: { type: String, default: 'docs/doi-chieu-v10-va-vue-demo-voi-backend.md' },
});
</script>

<template>
    <QbankLayout :title="title" :subtitle="subtitle">
        <section class="qb-card">
            <h2 class="qb-card-title">Chưa có API ở backend</h2>
            <p class="qb-muted">
                Màn này đã có thiết kế và đã được chốt trong mockup, nhưng server chưa cung cấp endpoint
                tương ứng. Giao diện không hiển thị dữ liệu giả để tránh hiểu nhầm là tính năng đã hoạt động.
            </p>

            <ul v-if="needs.length" class="qb-list qb-list--stack">
                <li v-for="item in needs" :key="item">
                    <span class="qb-dot"></span>{{ item }}
                </li>
            </ul>

            <p class="qb-muted qb-note">
                Chi tiết và ước lượng công việc: <code>{{ docRef }}</code> (mục 5).
            </p>
        </section>
    </QbankLayout>
</template>
