<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import QbankLayout from '@/components/qbank/QbankLayout.vue';
import { QbankRepository } from '@/repository/QbankRepository.js';

const repository = new QbankRepository();
const router = useRouter();

const subjects = ref([]);
const topics = ref([]);
const activeSubject = ref(null);
const includeInactive = ref(false);
const loading = ref(true);
const errorMessage = ref('');

async function load() {
    loading.value = true;
    errorMessage.value = '';
    try {
        subjects.value = await repository.listSubjects(includeInactive.value);
        if (activeSubject.value) {
            const stillExists = subjects.value.some((s) => s.subjectId === activeSubject.value.subjectId);
            if (!stillExists) {
                activeSubject.value = null;
                topics.value = [];
            }
        }
    } catch (error) {
        errorMessage.value = error.message;
    } finally {
        loading.value = false;
    }
}

async function selectSubject(subject) {
    activeSubject.value = subject;
    topics.value = [];
    try {
        topics.value = await repository.listTopics(subject.subjectId);
    } catch (error) {
        errorMessage.value = error.message;
    }
}

function openQuestions(subject) {
    router.push({ path: '/qbank/questions', query: { subjectId: subject.subjectId } });
}

onMounted(load);
</script>

<template>
    <QbankLayout
        title="Môn học"
        subtitle="Chọn một môn để xem chương/chủ đề và ngân hàng câu hỏi bên trong"
    >
        <template #actions>
            <label class="qb-inline-check">
                <input v-model="includeInactive" type="checkbox" class="form-check-input" @change="load" />
                Hiện cả môn đã ngừng dùng
            </label>
        </template>

        <p v-if="errorMessage" class="qb-alert qb-alert--danger">{{ errorMessage }}</p>

        <div class="qb-grid qb-grid--two">
            <section class="qb-card">
                <h2 class="qb-card-title">Danh sách môn</h2>
                <p v-if="loading" class="qb-muted">Đang tải…</p>
                <p v-else-if="!subjects.length" class="qb-muted">Chưa có môn học nào trong CSDL.</p>
                <ul v-else class="qb-subjects">
                    <li
                        v-for="subject in subjects"
                        :key="subject.subjectId"
                        :class="{ 'qb-subjects-item--active': activeSubject?.subjectId === subject.subjectId }"
                    >
                        <button class="qb-subject-btn" @click="selectSubject(subject)">
                            <span class="qb-mono">{{ subject.subjectCode }}</span>
                            <span class="d-block">{{ subject.subjectName }}</span>
                        </button>
                        <span v-if="!subject.active" class="qb-badge qb-badge--neutral">Ngừng dùng</span>
                        <button class="btn btn-sm btn-outline-primary" @click="openQuestions(subject)">
                            Câu hỏi
                        </button>
                    </li>
                </ul>
            </section>

            <section class="qb-card">
                <h2 class="qb-card-title">
                    {{ activeSubject ? `Chương của ${activeSubject.subjectCode}` : 'Chương / chủ đề' }}
                </h2>
                <p v-if="!activeSubject" class="qb-muted">Chọn một môn ở cột bên trái để xem chương.</p>
                <p v-else-if="!topics.length" class="qb-muted">Môn này chưa có chương nào.</p>
                <ol v-else class="qb-topics">
                    <li v-for="topic in topics" :key="topic.topicId">
                        <span>{{ topic.topicName }}</span>
                        <span class="qb-muted qb-mono">#{{ topic.topicId }}</span>
                    </li>
                </ol>

                <p class="qb-muted qb-note">
                    Tạo/sửa/xoá môn và chương chưa có API (xem
                    <code>docs/doi-chieu-v10-va-vue-demo-voi-backend.md</code> mục 5) — màn này hiện chỉ đọc.
                </p>
            </section>
        </div>
    </QbankLayout>
</template>
