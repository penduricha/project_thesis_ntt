<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { QbankRepository } from '@/repository/QbankRepository.js';
import { CONTENT_FORMATS, OPTION_LABELS } from '@/model/qbank/constants.js';

/**
 * Hộp thoại tạo/sửa câu hỏi.
 *
 * Ba điểm khác bản demo (đều do ràng buộc thật của backend):
 *  1. Sửa câu hỏi phải gửi kèm `version` đang giữ — thiếu/lệch thì backend trả 409 VERSION_CONFLICT.
 *  2. Backend KHÔNG tự quyết đáp án đúng: người soạn chọn nhãn A–D, AI chỉ đề xuất chứ không ghi.
 *  3. Form tự lưu nháp lên Redis (24 giờ) mỗi khi ngừng gõ 800ms, để rời trang không mất nội dung.
 */
const props = defineProps({
    questionId: { type: Number, default: null },
    subjects: { type: Array, required: true },
    defaultSubjectId: { type: Number, default: null },
});

const emit = defineEmits(['close', 'saved']);

const repository = new QbankRepository();

const form = reactive({
    subjectId: props.defaultSubjectId,
    topicId: null,
    title: '',
    stem: '',
    optionA: '',
    optionB: '',
    optionC: '',
    optionD: '',
    correctLabel: 'A',
    contentFormat: 'plain',
    generalFeedback: '',
    difficulty: 2,
});

const topics = ref([]);
const loading = ref(true);
const saving = ref(false);
const errorMessage = ref('');
const fieldErrors = reactive({});
const version = ref(null);
const draftRestored = ref(false);

const isEdit = computed(() => props.questionId != null);
const dialogTitle = computed(() => (isEdit.value ? `Sửa câu hỏi #${props.questionId}` : 'Tạo câu hỏi mới'));

let draftTimer = null;
let draftDirty = false;

function options() {
    return {
        A: form.optionA,
        B: form.optionB,
        C: form.optionC,
        D: form.optionD,
    };
}

async function loadTopics(subjectId) {
    if (!subjectId) {
        topics.value = [];
        return;
    }
    try {
        topics.value = await repository.listTopics(subjectId);
    } catch (error) {
        topics.value = [];
        errorMessage.value = error.message;
    }
}

async function load() {
    loading.value = true;
    errorMessage.value = '';
    try {
        if (isEdit.value) {
            const detail = await repository.getQuestion(props.questionId);
            Object.assign(form, {
                subjectId: detail.subjectId,
                topicId: detail.topicId,
                title: detail.title || '',
                stem: detail.stem || '',
                optionA: detail.optionA || '',
                optionB: detail.optionB || '',
                optionC: detail.optionC || '',
                optionD: detail.optionD || '',
                correctLabel: detail.correctLabel || 'A',
                contentFormat: detail.contentFormat || 'plain',
                generalFeedback: detail.generalFeedback || '',
                difficulty: detail.difficulty ?? 2,
            });
            version.value = detail.version;
            await loadTopics(form.subjectId);
        } else {
            await loadTopics(form.subjectId);
        }

        // Nháp đến sau dữ liệu gốc để không ghi đè nội dung vừa sửa trên máy khác?
        // Không: nháp là của CHÍNH người dùng này (Redis khoá theo adminId), nên ưu tiên nháp.
        const draft = await repository.getDraft(props.questionId);
        if (draft) {
            Object.assign(form, {
                subjectId: draft.subjectId ?? form.subjectId,
                topicId: draft.topicId ?? form.topicId,
                title: draft.title ?? form.title,
                stem: draft.stem ?? form.stem,
                optionA: draft.optionA ?? form.optionA,
                optionB: draft.optionB ?? form.optionB,
                optionC: draft.optionC ?? form.optionC,
                optionD: draft.optionD ?? form.optionD,
                correctLabel: draft.correctLabel ?? form.correctLabel,
                contentFormat: draft.contentFormat ?? form.contentFormat,
                generalFeedback: draft.generalFeedback ?? form.generalFeedback,
                difficulty: draft.difficulty ?? form.difficulty,
            });
            await loadTopics(form.subjectId);
            draftRestored.value = true;
        }
    } catch (error) {
        errorMessage.value = error.message;
    } finally {
        loading.value = false;
    }
}

function scheduleDraftSave() {
    draftDirty = true;
    if (draftTimer) {
        clearTimeout(draftTimer);
    }
    draftTimer = setTimeout(() => {
        if (draftDirty) {
            draftDirty = false;
            repository.saveDraft({ ...form }, props.questionId).catch(() => {
                /* nháp là tính năng phụ, lỗi không chặn người dùng */
            });
        }
    }, 800);
}

watch(form, scheduleDraftSave, { deep: true });
watch(
    () => form.subjectId,
    async (next, previous) => {
        if (previous !== undefined && next !== previous) {
            form.topicId = null;
            await loadTopics(next);
        }
    },
);

function validate() {
    Object.keys(fieldErrors).forEach((key) => delete fieldErrors[key]);
    if (!form.subjectId) {
        fieldErrors.subjectId = 'Chọn môn học.';
    }
    if (!form.stem.trim()) {
        fieldErrors.stem = 'Câu dẫn không được để trống.';
    }
    OPTION_LABELS.forEach((label) => {
        if (!String(options()[label] || '').trim()) {
            fieldErrors[`option${label}`] = `Phương án ${label} không được để trống.`;
        }
    });
    return Object.keys(fieldErrors).length === 0;
}

async function submit() {
    errorMessage.value = '';
    if (!validate()) {
        return;
    }
    saving.value = true;
    const payload = {
        subjectId: form.subjectId,
        topicId: form.topicId || null,
        title: form.title.trim() || null,
        stem: form.stem.trim(),
        optionA: form.optionA.trim(),
        optionB: form.optionB.trim(),
        optionC: form.optionC.trim(),
        optionD: form.optionD.trim(),
        correctLabel: form.correctLabel,
        contentFormat: form.contentFormat,
        generalFeedback: form.generalFeedback.trim() || null,
        difficulty: Number(form.difficulty),
    };

    try {
        const saved = isEdit.value
            ? await repository.updateQuestion(props.questionId, { ...payload, version: version.value })
            : await repository.createQuestion(payload);
        emit('saved', saved);
    } catch (error) {
        errorMessage.value =
            error.code === 'VERSION_CONFLICT'
                ? `${error.message} Nội dung bạn đang sửa vẫn được giữ nguyên.`
                : error.message;
    } finally {
        saving.value = false;
    }
}

async function close() {
    // Người dùng đóng khi còn thay đổi: giữ nháp đã lưu tự động, không xoá.
    emit('close');
}

onMounted(load);
onBeforeUnmount(() => {
    if (draftTimer) {
        clearTimeout(draftTimer);
    }
});
</script>

<template>
    <div class="qb-modal-backdrop" @click.self="close">
        <section class="qb-modal" role="dialog" aria-modal="true">
            <header class="qb-modal-head">
                <div>
                    <h2 class="qb-modal-title">{{ dialogTitle }}</h2>
                    <p v-if="draftRestored" class="qb-muted">
                        Đã khôi phục bản nháp bạn nhập dở (lưu trên Redis 24 giờ).
                    </p>
                </div>
                <button class="btn btn-sm btn-outline-secondary" @click="close">Đóng</button>
            </header>

            <div v-if="loading" class="qb-modal-body">Đang tải…</div>

            <form v-else class="qb-modal-body" @submit.prevent="submit">
                <p v-if="errorMessage" class="qb-alert qb-alert--danger">{{ errorMessage }}</p>

                <div class="qb-form-row">
                    <label class="qb-field">
                        <span>Môn học <em>*</em></span>
                        <select v-model.number="form.subjectId" class="form-select form-select-sm">
                            <option :value="null">— Chọn môn —</option>
                            <option v-for="subject in subjects" :key="subject.subjectId" :value="subject.subjectId">
                                {{ subject.subjectCode }} · {{ subject.subjectName }}
                            </option>
                        </select>
                        <small v-if="fieldErrors.subjectId" class="qb-error">{{ fieldErrors.subjectId }}</small>
                    </label>

                    <label class="qb-field">
                        <span>Chương / chủ đề</span>
                        <select v-model.number="form.topicId" class="form-select form-select-sm">
                            <option :value="null">— Không gán chương —</option>
                            <option v-for="topic in topics" :key="topic.topicId" :value="topic.topicId">
                                {{ topic.topicName }}
                            </option>
                        </select>
                    </label>
                </div>

                <label class="qb-field">
                    <span>Tiêu đề ngắn</span>
                    <input v-model="form.title" class="form-control form-control-sm" maxlength="255" />
                </label>

                <label class="qb-field">
                    <span>Câu dẫn <em>*</em></span>
                    <textarea v-model="form.stem" rows="3" class="form-control form-control-sm"></textarea>
                    <small v-if="fieldErrors.stem" class="qb-error">{{ fieldErrors.stem }}</small>
                </label>

                <fieldset class="qb-options">
                    <legend>Bốn phương án (chọn nút tròn để đánh dấu đáp án đúng)</legend>
                    <label v-for="label in OPTION_LABELS" :key="label" class="qb-option">
                        <input
                            v-model="form.correctLabel"
                            type="radio"
                            name="correctLabel"
                            :value="label"
                            class="form-check-input"
                        />
                        <span class="qb-option-label">{{ label }}</span>
                        <textarea
                            v-model="form[`option${label}`]"
                            rows="2"
                            class="form-control form-control-sm"
                        ></textarea>
                    </label>
                    <small v-if="fieldErrors.optionA" class="qb-error">
                        Mỗi phương án phải có nội dung.
                    </small>
                </fieldset>

                <div class="qb-form-row">
                    <label class="qb-field">
                        <span>Độ khó</span>
                        <select v-model.number="form.difficulty" class="form-select form-select-sm">
                            <option :value="1">1 · Dễ</option>
                            <option :value="2">2 · Trung bình</option>
                            <option :value="3">3 · Khó</option>
                            <option :value="4">4 · Khó hơn</option>
                            <option :value="5">5 · Rất khó</option>
                        </select>
                    </label>
                    <label class="qb-field">
                        <span>Định dạng nội dung</span>
                        <select v-model="form.contentFormat" class="form-select form-select-sm">
                            <option v-for="item in CONTENT_FORMATS" :key="item.value" :value="item.value">
                                {{ item.label }}
                            </option>
                        </select>
                    </label>
                </div>

                <label class="qb-field">
                    <span>Phản hồi chung</span>
                    <textarea
                        v-model="form.generalFeedback"
                        rows="2"
                        class="form-control form-control-sm"
                        maxlength="4000"
                    ></textarea>
                </label>

                <footer class="qb-modal-foot">
                    <span v-if="isEdit" class="qb-muted">Phiên bản hiện tại: {{ version ?? '—' }}</span>
                    <div class="d-flex gap-2">
                        <button type="button" class="btn btn-sm btn-outline-secondary" @click="close">Huỷ</button>
                        <button type="submit" class="btn btn-sm btn-primary" :disabled="saving">
                            {{ saving ? 'Đang lưu…' : 'Lưu câu hỏi' }}
                        </button>
                    </div>
                </footer>
            </form>
        </section>
    </div>
</template>
