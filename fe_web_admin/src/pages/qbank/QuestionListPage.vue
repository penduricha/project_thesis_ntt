<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import QbankLayout from '@/components/qbank/QbankLayout.vue';
import QuestionFormDialog from '@/components/qbank/QuestionFormDialog.vue';
import { QbankRepository } from '@/repository/QbankRepository.js';
import {
    DIFFICULTY_LABELS,
    QUESTION_STATUS_LABELS,
    VALIDATION_STATUS_LABELS,
    difficultyLabel,
    formatInstant,
    statusLabel,
    validationLabel,
    validationTone,
} from '@/model/qbank/constants.js';

const repository = new QbankRepository();
const route = useRoute();
const router = useRouter();

const filters = reactive({
    subjectId: route.query.subjectId ? Number(route.query.subjectId) : null,
    topicId: null,
    status: '',
    validationStatus: '',
    q: '',
    page: 0,
    size: 20,
});

const subjects = ref([]);
const topics = ref([]);
const page = ref({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
const loading = ref(false);
const errorMessage = ref('');
const selected = ref(new Set());
const dialogOpen = ref(false);
const editingId = ref(null);
const busyIds = ref(new Set());
const toast = ref('');

const showDialog = computed(() => dialogOpen.value);

const statusOptions = Object.entries(QUESTION_STATUS_LABELS);
const validationOptions = Object.entries(VALIDATION_STATUS_LABELS);
const difficultyOptions = Object.entries(DIFFICULTY_LABELS);

function flash(message) {
    toast.value = message;
    setTimeout(() => {
        if (toast.value === message) {
            toast.value = '';
        }
    }, 6000);
}

async function loadSubjects() {
    try {
        subjects.value = await repository.listSubjects(false);
    } catch (error) {
        errorMessage.value = error.message;
    }
}

async function loadTopics() {
    if (!filters.subjectId) {
        topics.value = [];
        filters.topicId = null;
        return;
    }
    try {
        topics.value = await repository.listTopics(filters.subjectId);
    } catch {
        topics.value = [];
    }
}

async function loadQuestions() {
    loading.value = true;
    errorMessage.value = '';
    try {
        page.value = await repository.listQuestions({ ...filters });
        selected.value = new Set();
    } catch (error) {
        errorMessage.value = error.message;
    } finally {
        loading.value = false;
    }
}

async function applySubjectFilter() {
    filters.page = 0;
    await loadTopics();
    await loadQuestions();
    // Đưa bộ lọc lên URL để F5 hoặc chia sẻ liên kết vẫn giữ đúng môn đang xem.
    router.replace({ path: '/qbank/questions', query: filters.subjectId ? { subjectId: filters.subjectId } : {} });
}

async function applyFilters() {
    filters.page = 0;
    await loadQuestions();
}

function goPage(next) {
    if (next < 0 || next >= page.value.totalPages) {
        return;
    }
    filters.page = next;
    loadQuestions();
}

function toggleRow(questionId) {
    const next = new Set(selected.value);
    if (next.has(questionId)) {
        next.delete(questionId);
    } else {
        next.add(questionId);
    }
    selected.value = next;
}

function toggleAll() {
    if (selected.value.size === page.value.items.length) {
        selected.value = new Set();
    } else {
        selected.value = new Set(page.value.items.map((item) => item.questionId));
    }
}

function openCreate() {
    editingId.value = null;
    dialogOpen.value = true;
}

function openEdit(questionId) {
    editingId.value = questionId;
    dialogOpen.value = true;
}

async function onSaved(detail) {
    dialogOpen.value = false;
    flash(`Đã lưu câu hỏi #${detail.questionId} (phiên bản ${detail.version}).`);
    await loadQuestions();
}

async function withBusy(questionId, action) {
    busyIds.value = new Set(busyIds.value).add(questionId);
    try {
        await action();
    } catch (error) {
        errorMessage.value = error.message;
    } finally {
        const next = new Set(busyIds.value);
        next.delete(questionId);
        busyIds.value = next;
    }
}

async function archive(questionId) {
    await withBusy(questionId, async () => {
        await repository.archiveQuestion(questionId);
        flash(`Đã lưu trữ câu hỏi #${questionId}. Có thể khôi phục bằng nút "Khôi phục".`);
        await loadQuestions();
    });
}

async function restore(questionId) {
    await withBusy(questionId, async () => {
        await repository.restoreQuestion(questionId);
        flash(`Đã khôi phục câu hỏi #${questionId}.`);
        await loadQuestions();
    });
}

async function startAiCheck(questionId) {
    await withBusy(questionId, async () => {
        const job = await repository.startAiCheck(questionId);
        // Backend trả 202 + taskId; chuyển sang màn tác vụ để theo dõi tiến độ.
        await router.push({ path: '/qbank/tasks', query: { taskId: job.taskId } });
    });
}

async function bulkArchive() {
    if (!selected.value.size) {
        return;
    }
    try {
        const result = await repository.bulkArchive([...selected.value]);
        flash(
            `Đã lưu trữ ${result.done.length}/${result.requested} câu hỏi` +
                (result.skipped.length ? `; ${result.skipped.length} câu bị bỏ qua.` : '.'),
        );
        await loadQuestions();
    } catch (error) {
        errorMessage.value = error.message;
    }
}

async function bulkRestore() {
    if (!selected.value.size) {
        return;
    }
    try {
        const result = await repository.bulkRestore([...selected.value]);
        flash(`Đã khôi phục ${result.done.length}/${result.requested} câu hỏi.`);
        await loadQuestions();
    } catch (error) {
        errorMessage.value = error.message;
    }
}

const allSelected = computed(
    () => page.value.items.length > 0 && selected.value.size === page.value.items.length,
);

onMounted(async () => {
    await loadSubjects();
    await loadTopics();
    await loadQuestions();
});
</script>

<template>
    <QbankLayout
        title="Tất cả câu hỏi"
        subtitle="Thêm, sửa, lưu trữ, khôi phục và gửi kiểm định AI"
    >
        <template #actions>
            <button class="btn btn-sm btn-primary" @click="openCreate">Tạo câu hỏi</button>
        </template>

        <p v-if="toast" class="qb-alert qb-alert--ok">{{ toast }}</p>
        <p v-if="errorMessage" class="qb-alert qb-alert--danger">{{ errorMessage }}</p>

        <section class="qb-card qb-filters">
            <label class="qb-field">
                <span>Môn học</span>
                <select v-model.number="filters.subjectId" class="form-select form-select-sm" @change="applySubjectFilter">
                    <option :value="null">Tất cả môn</option>
                    <option v-for="subject in subjects" :key="subject.subjectId" :value="subject.subjectId">
                        {{ subject.subjectCode }} · {{ subject.subjectName }}
                    </option>
                </select>
            </label>

            <label class="qb-field">
                <span>Chương</span>
                <select v-model.number="filters.topicId" class="form-select form-select-sm" @change="applyFilters">
                    <option :value="null">Tất cả chương</option>
                    <option v-for="topic in topics" :key="topic.topicId" :value="topic.topicId">
                        {{ topic.topicName }}
                    </option>
                </select>
            </label>

            <label class="qb-field">
                <span>Trạng thái</span>
                <select v-model="filters.status" class="form-select form-select-sm" @change="applyFilters">
                    <option value="">Tất cả</option>
                    <option v-for="[value, label] in statusOptions" :key="value" :value="value">{{ label }}</option>
                </select>
            </label>

            <label class="qb-field">
                <span>Kiểm định</span>
                <select
                    v-model="filters.validationStatus"
                    class="form-select form-select-sm"
                    @change="applyFilters"
                >
                    <option value="">Tất cả</option>
                    <option v-for="[value, label] in validationOptions" :key="value" :value="value">
                        {{ label }}
                    </option>
                </select>
            </label>

            <label class="qb-field qb-field--grow">
                <span>Tìm theo nội dung</span>
                <input
                    v-model="filters.q"
                    class="form-control form-control-sm"
                    placeholder="Nhập từ khoá trong câu dẫn…"
                    @keyup.enter="applyFilters"
                />
            </label>

            <button class="btn btn-sm btn-outline-secondary align-self-end" @click="applyFilters">Lọc</button>
        </section>

        <section v-if="selected.size" class="qb-bulkbar">
            <span>Đã chọn {{ selected.size }} câu hỏi</span>
            <div class="d-flex gap-2">
                <button class="btn btn-sm btn-outline-warning" @click="bulkArchive">Lưu trữ đã chọn</button>
                <button class="btn btn-sm btn-outline-success" @click="bulkRestore">Khôi phục đã chọn</button>
            </div>
        </section>

        <section class="qb-card qb-table-wrap">
            <p v-if="loading" class="qb-muted">Đang tải danh sách…</p>
            <p v-else-if="!page.items.length" class="qb-muted">
                Không có câu hỏi nào khớp bộ lọc. Thử xoá bớt điều kiện hoặc tạo câu hỏi mới.
            </p>

            <table v-else class="table table-sm qb-table">
                <thead>
                    <tr>
                        <th style="width: 32px">
                            <input
                                type="checkbox"
                                class="form-check-input"
                                :checked="allSelected"
                                @change="toggleAll"
                            />
                        </th>
                        <th style="width: 88px">Mã</th>
                        <th>Câu dẫn</th>
                        <th style="width: 170px">Môn / chương</th>
                        <th style="width: 90px">Độ khó</th>
                        <th style="width: 110px">Kiểm định</th>
                        <th style="width: 100px">Trạng thái</th>
                        <th style="width: 140px">Cập nhật</th>
                        <th style="width: 210px"></th>
                    </tr>
                </thead>
                <tbody>
                    <tr v-for="item in page.items" :key="item.questionId">
                        <td>
                            <input
                                type="checkbox"
                                class="form-check-input"
                                :checked="selected.has(item.questionId)"
                                @change="toggleRow(item.questionId)"
                            />
                        </td>
                        <td class="qb-mono">#{{ item.questionId }}</td>
                        <td>
                            <p class="qb-cell-title">{{ item.title || item.stemPreview }}</p>
                            <p class="qb-muted qb-cell-stem">{{ item.stemPreview }}</p>
                        </td>
                        <td>
                            <span class="d-block">{{ item.subjectName || '—' }}</span>
                            <span class="qb-muted">{{ item.topicName || '—' }}</span>
                        </td>
                        <td>{{ difficultyLabel(item.difficulty) }}</td>
                        <td>
                            <span class="qb-badge" :class="`qb-badge--${validationTone(item.validationStatus)}`">
                                {{ validationLabel(item.validationStatus) }}
                            </span>
                        </td>
                        <td>{{ statusLabel(item.status) }}</td>
                        <td class="qb-muted">{{ formatInstant(item.updatedAt) }}</td>
                        <td class="qb-actions">
                            <button
                                class="btn btn-sm btn-outline-secondary"
                                :disabled="busyIds.has(item.questionId)"
                                @click="openEdit(item.questionId)"
                            >
                                Sửa
                            </button>
                            <button
                                class="btn btn-sm btn-outline-primary"
                                :disabled="busyIds.has(item.questionId) || item.status === 'archived'"
                                @click="startAiCheck(item.questionId)"
                            >
                                Kiểm định AI
                            </button>
                            <button
                                v-if="item.status === 'archived'"
                                class="btn btn-sm btn-outline-success"
                                :disabled="busyIds.has(item.questionId)"
                                @click="restore(item.questionId)"
                            >
                                Khôi phục
                            </button>
                            <button
                                v-else
                                class="btn btn-sm btn-outline-warning"
                                :disabled="busyIds.has(item.questionId)"
                                @click="archive(item.questionId)"
                            >
                                Lưu trữ
                            </button>
                        </td>
                    </tr>
                </tbody>
            </table>

            <footer class="qb-pager">
                <span class="qb-muted">
                    {{ page.totalElements }} câu hỏi · trang {{ page.page + 1 }}/{{ Math.max(page.totalPages, 1) }}
                </span>
                <div class="d-flex gap-2">
                    <button class="btn btn-sm btn-outline-secondary" :disabled="filters.page === 0" @click="goPage(filters.page - 1)">
                        Trước
                    </button>
                    <button
                        class="btn btn-sm btn-outline-secondary"
                        :disabled="filters.page >= page.totalPages - 1"
                        @click="goPage(filters.page + 1)"
                    >
                        Sau
                    </button>
                </div>
            </footer>
        </section>

        <QuestionFormDialog
            v-if="showDialog"
            :question-id="editingId"
            :subjects="subjects"
            :default-subject-id="filters.subjectId"
            @close="dialogOpen = false"
            @saved="onSaved"
        />
    </QbankLayout>
</template>
