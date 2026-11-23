<script setup>
import { computed, onMounted, ref } from 'vue';
import QbankLayout from '@/components/qbank/QbankLayout.vue';
import { QbankRepository } from '@/repository/QbankRepository.js';
import { formatInstant, validationLabel, validationTone } from '@/model/qbank/constants.js';

const repository = new QbankRepository();

const summary = ref(null);
const activity = ref([]);
const loading = ref(true);
const errorMessage = ref('');

/** Thứ tự hiển thị cố định để thẻ không nhảy chỗ khi số liệu đổi. */
const VALIDATION_ORDER = ['clean', 'has_issues', 'stale', 'failed', 'not_checked'];

const validationRows = computed(() => {
    const map = summary.value?.byValidation || {};
    return VALIDATION_ORDER.filter((key) => Number(map[key] || 0) > 0 || key === 'clean').map((key) => ({
        key,
        label: validationLabel(key),
        tone: validationTone(key),
        count: Number(map[key] || 0),
    }));
});

const cards = computed(() => {
    const s = summary.value;
    if (!s) {
        return [];
    }
    return [
        { label: 'Câu hỏi đang dùng', value: s.totalActive },
        { label: 'Đã lưu trữ', value: s.totalArchived },
        { label: 'Sửa trong 7 ngày', value: s.updatedLast7Days },
        { label: 'Đề xuất AI chờ duyệt', value: s.pendingSuggestions },
        { label: 'Tác vụ AI đang chạy', value: s.runningTasks },
        { label: 'Môn học', value: s.subjectCount },
    ];
});

async function load() {
    loading.value = true;
    errorMessage.value = '';
    try {
        // Gọi song song: số liệu và hoạt động độc lập nhau.
        const [summaryData, activityData] = await Promise.all([
            repository.getDashboard(),
            repository.getActivity(10),
        ]);
        summary.value = summaryData;
        activity.value = activityData || [];
    } catch (error) {
        errorMessage.value = error.message || 'Không tải được số liệu tổng quan.';
    } finally {
        loading.value = false;
    }
}

onMounted(load);
</script>

<template>
    <QbankLayout title="Tổng quan" subtitle="Tình hình ngân hàng câu hỏi và các dịch vụ suy luận">
        <template #actions>
            <button class="btn btn-outline-secondary btn-sm" :disabled="loading" @click="load">
                Làm mới
            </button>
        </template>

        <p v-if="summary?.cached" class="qb-hint">
            Số liệu lấy từ bộ nhớ đệm (Redis, 30 giây) — tối đa cũ 30 giây so với CSDL.
        </p>

        <div v-if="errorMessage" class="qb-alert qb-alert--danger">
            {{ errorMessage }}
            <button class="btn btn-sm btn-outline-danger ms-2" @click="load">Thử lại</button>
        </div>

        <div v-if="loading" class="qb-card">Đang tải số liệu…</div>

        <template v-else-if="summary">
            <section class="qb-grid">
                <article v-for="card in cards" :key="card.label" class="qb-card qb-stat">
                    <p class="qb-stat-label">{{ card.label }}</p>
                    <p class="qb-stat-value">{{ card.value }}</p>
                </article>
            </section>

            <section class="qb-grid qb-grid--two">
                <article class="qb-card">
                    <h2 class="qb-card-title">Trạng thái kiểm định</h2>
                    <ul class="qb-list">
                        <li v-for="row in validationRows" :key="row.key">
                            <span class="qb-badge" :class="`qb-badge--${row.tone}`">{{ row.label }}</span>
                            <strong>{{ row.count }}</strong>
                        </li>
                    </ul>
                    <p class="qb-muted">
                        Số liệu tính trên toàn bộ câu hỏi đang dùng. Cập nhật lúc
                        {{ formatInstant(summary.generatedAt) }}.
                    </p>
                </article>

                <article class="qb-card">
                    <h2 class="qb-card-title">Hoạt động gần đây</h2>
                    <p v-if="!activity.length" class="qb-muted">Chưa có hoạt động nào được ghi.</p>
                    <ul v-else class="qb-timeline">
                        <li v-for="(item, index) in activity" :key="`${item.at}-${index}`">
                            <span class="qb-timeline-dot"></span>
                            <div>
                                <p class="qb-timeline-text">{{ item.message }}</p>
                                <p class="qb-muted">{{ formatInstant(item.at) }}</p>
                            </div>
                        </li>
                    </ul>
                </article>
            </section>
        </template>
    </QbankLayout>
</template>
