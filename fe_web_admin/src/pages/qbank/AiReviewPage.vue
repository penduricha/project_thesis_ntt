<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import QbankLayout from '@/components/qbank/QbankLayout.vue';
import { QbankRepository } from '@/repository/QbankRepository.js';
import {
    decisionLabel,
    formatInstant,
    severityLabel,
    taskStatus,
} from '@/model/qbank/constants.js';

/**
 * Màn "Xem xét đề xuất AI" và "Tác vụ AI" dùng chung một component.
 *
 * - mode = 'tasks': nhận `?taskId=` (từ màn danh sách câu hỏi sau khi bấm "Kiểm định AI"),
 *   poll `pollAfterMs` do backend gợi ý (1,5 giây khi đang chạy, 0 khi đã xong).
 * - mode = 'review': nhập mã câu hỏi để xem đề xuất của lần kiểm định gần nhất.
 *
 * Quyết định đề xuất phải gửi kèm `expectedVersion` của câu hỏi; backend trả câu hỏi đã cập nhật
 * (version mới) để lần quyết định sau không bị 409.
 */
const props = defineProps({
    mode: { type: String, default: 'review' }, // 'review' | 'tasks'
});

const repository = new QbankRepository();
const route = useRoute();
const router = useRouter();

const taskId = ref(typeof route.query.taskId === 'string' ? route.query.taskId : '');
const questionIdInput = ref('');
const questionId = ref(null);

const job = ref(null);
const suggestions = ref([]);
const question = ref(null);
const loading = ref(false);
const deciding = ref(null);
const errorMessage = ref('');
const toast = ref('');

let pollTimer = null;

const isTaskMode = computed(() => props.mode === 'tasks');
const terminal = computed(() => {
    const status = job.value?.status;
    return status === 'succeeded' || status === 'failed' || status === 'cancelled' || status === 'timeout';
});
const statusInfo = computed(() => taskStatus(job.value?.status));

function stopPolling() {
    if (pollTimer) {
        clearTimeout(pollTimer);
        pollTimer = null;
    }
}

function flash(message) {
    toast.value = message;
    setTimeout(() => {
        if (toast.value === message) {
            toast.value = '';
        }
    }, 7000);
}

async function pollJob(task) {
    stopPolling();
    try {
        const data = await repository.getJob(task);
        job.value = data;
        if (!terminal.value) {
            const delay = data.pollAfterMs > 0 ? data.pollAfterMs : 1500;
            pollTimer = setTimeout(() => pollJob(task), delay);
        } else if (data.status === 'succeeded') {
            await loadJobSuggestions(task);
        }
    } catch (error) {
        errorMessage.value = error.message;
    }
}

async function loadJobSuggestions(task) {
    try {
        suggestions.value = await repository.getJobSuggestions(task);
    } catch (error) {
        errorMessage.value = error.message;
    }
}

async function submitTask() {
    if (!taskId.value.trim()) {
        return;
    }
    errorMessage.value = '';
    suggestions.value = [];
    job.value = null;
    await router.replace({ path: '/qbank/tasks', query: { taskId: taskId.value.trim() } });
    await pollJob(taskId.value.trim());
}

async function loadQuestionSuggestions() {
    const id = Number(questionIdInput.value);
    if (!id) {
        errorMessage.value = 'Nhập mã câu hỏi (số).';
        return;
    }
    loading.value = true;
    errorMessage.value = '';
    try {
        question.value = await repository.getQuestion(id);
        questionId.value = id;
        suggestions.value = await repository.getQuestionSuggestions(id);
        if (!suggestions.value.length) {
            flash('Câu hỏi này chưa có đề xuất nào. Hãy chạy kiểm định AI ở màn Tất cả câu hỏi.');
        }
    } catch (error) {
        errorMessage.value = error.message;
        question.value = null;
        suggestions.value = [];
    } finally {
        loading.value = false;
    }
}

async function decide(suggestion, accepted) {
    if (!question.value) {
        return;
    }
    deciding.value = suggestion.suggestionId;
    try {
        const updated = await repository.decideSuggestion(suggestion.suggestionId, {
            decision: accepted ? 'accepted' : 'rejected',
            expectedVersion: question.value.version,
            finalText: accepted ? suggestion.proposedText : undefined,
            rejectReason: accepted ? undefined : 'Không phù hợp với nội dung câu hỏi',
        });
        question.value = updated;
        flash(
            accepted
                ? `Đã nhận đề xuất #${suggestion.suggestionId}; câu hỏi lên phiên bản ${updated.version}.`
                : `Đã từ chối đề xuất #${suggestion.suggestionId}.`,
        );
        suggestions.value = await repository.getQuestionSuggestions(questionId.value);
    } catch (error) {
        errorMessage.value =
            error.code === 'SUGGESTION_STALE'
                ? `${error.message} Tải lại câu hỏi rồi kiểm định lại.`
                : error.message;
    } finally {
        deciding.value = null;
    }
}

watch(
    () => route.query.taskId,
    (next) => {
        if (isTaskMode.value && typeof next === 'string' && next !== taskId.value) {
            taskId.value = next;
            pollJob(next);
        }
    },
);

onMounted(() => {
    if (isTaskMode.value && taskId.value) {
        pollJob(taskId.value);
    }
});

onBeforeUnmount(stopPolling);
</script>

<template>
    <QbankLayout
        :title="isTaskMode ? 'Tác vụ AI' : 'Xem xét đề xuất AI'"
        :subtitle="
            isTaskMode
                ? 'Theo dõi tiến độ kiểm định và xem đề xuất khi xong'
                : 'So sánh nội dung gốc và đề xuất trước khi cập nhật câu hỏi'
        "
    >
        <p v-if="toast" class="qb-alert qb-alert--ok">{{ toast }}</p>
        <p v-if="errorMessage" class="qb-alert qb-alert--danger">{{ errorMessage }}</p>

        <section v-if="isTaskMode" class="qb-card qb-filters">
            <label class="qb-field qb-field--grow">
                <span>Mã tác vụ (taskId)</span>
                <input
                    v-model="taskId"
                    class="form-control form-control-sm"
                    placeholder="Ví dụ: q_chk_2500021775_1732…"
                    @keyup.enter="submitTask"
                />
            </label>
            <button class="btn btn-sm btn-primary align-self-end" @click="submitTask">Theo dõi</button>
        </section>

        <section v-else class="qb-card qb-filters">
            <label class="qb-field qb-field--grow">
                <span>Mã câu hỏi</span>
                <input
                    v-model="questionIdInput"
                    class="form-control form-control-sm"
                    placeholder="Nhập mã câu hỏi để xem đề xuất gần nhất"
                    @keyup.enter="loadQuestionSuggestions"
                />
            </label>
            <button class="btn btn-sm btn-primary align-self-end" :disabled="loading" @click="loadQuestionSuggestions">
                Xem đề xuất
            </button>
        </section>

        <section v-if="isTaskMode && job" class="qb-card">
            <div class="qb-job-head">
                <div>
                    <h2 class="qb-card-title">Tác vụ {{ job.taskId }}</h2>
                    <p class="qb-muted">
                        Câu hỏi #{{ job.questionId }} · {{ job.taskType }} · cập nhật
                        {{ formatInstant(job.updatedAt) }}
                    </p>
                </div>
                <span class="qb-badge" :class="`qb-badge--${statusInfo.tone}`">{{ statusInfo.label }}</span>
            </div>

            <p v-if="!terminal" class="qb-muted">
                Đang chờ máy chủ AI… giao diện tự cập nhật mỗi {{ job.pollAfterMs || 1500 }}ms (không cần F5).
            </p>
            <p v-else-if="job.status === 'failed'" class="qb-alert qb-alert--danger">
                Kiểm định thất bại ({{ job.errorCode || 'không rõ mã lỗi' }}). Thử lại sau hoặc kiểm tra máy chủ AI.
            </p>
            <p v-else-if="job.status === 'succeeded'" class="qb-muted">
                Tìm thấy {{ job.suggestionCount }} đề xuất.
            </p>
        </section>

        <section v-if="question" class="qb-card">
            <div class="qb-job-head">
                <div>
                    <h2 class="qb-card-title">Câu hỏi #{{ question.questionId }}</h2>
                    <p class="qb-muted">
                        {{ question.subjectName || '—' }} · phiên bản {{ question.version }} · kiểm định
                        {{ question.validationStatus }}
                    </p>
                </div>
            </div>
            <p class="qb-stem">{{ question.stem }}</p>
            <ul class="qb-options-readonly">
                <li v-for="label in ['A', 'B', 'C', 'D']" :key="label">
                    <span class="qb-option-label">{{ label }}</span>
                    <span :class="{ 'qb-option-correct': question.correctLabel === label }">
                        {{ question[`option${label}`] }}
                    </span>
                </li>
            </ul>
        </section>

        <section class="qb-card">
            <h2 class="qb-card-title">Đề xuất</h2>
            <p v-if="!suggestions.length" class="qb-muted">Chưa có đề xuất nào để hiển thị.</p>

            <article v-for="item in suggestions" :key="item.suggestionId" class="qb-suggestion">
                <header class="qb-suggestion-head">
                    <span class="qb-badge qb-badge--warn">{{ severityLabel(item.severity) }}</span>
                    <strong>{{ item.issueLabel || item.issueType }}</strong>
                    <span class="qb-muted">{{ item.targetField }} · {{ decisionLabel(item.decision) }}</span>
                </header>

                <p class="qb-muted">{{ item.explanation }}</p>

                <div class="qb-compare">
                    <div>
                        <p class="qb-compare-label">Nội dung gốc</p>
                        <pre class="qb-code">{{ item.originalText || '—' }}</pre>
                    </div>
                    <div>
                        <p class="qb-compare-label">Đề xuất</p>
                        <pre class="qb-code">{{ item.proposedText || '—' }}</pre>
                    </div>
                </div>

                <footer v-if="item.decision === 'pending' && question" class="qb-suggestion-foot">
                    <button
                        class="btn btn-sm btn-outline-success"
                        :disabled="deciding === item.suggestionId"
                        @click="decide(item, true)"
                    >
                        Nhận đề xuất
                    </button>
                    <button
                        class="btn btn-sm btn-outline-danger"
                        :disabled="deciding === item.suggestionId"
                        @click="decide(item, false)"
                    >
                        Từ chối
                    </button>
                    <span class="qb-muted">
                        Quyết định sẽ tạo phiên bản mới; AI không bao giờ tự ghi vào câu hỏi.
                    </span>
                </footer>
            </article>
        </section>
    </QbankLayout>
</template>
