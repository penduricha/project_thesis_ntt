<script setup>
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { TokenLogin } from '@/model/TokenLogin.js';
import { readSession } from '@/model/qbank/sessionRole.js';

/**
 * Khung giao diện ngân hàng câu hỏi: sidebar theo nhóm + thanh tiêu đề.
 *
 * Bố cục và thứ tự nhóm lấy đúng từ `project-design/UI_Accept_v11.html` (NAV + EXTRA).
 * Mục chỉ dành cho quản trị viên (Quản trị hệ thống, Cài đặt LLM) ẩn với vai LECTURER —
 * đây chỉ là lớp giao diện, backend vẫn chặn bằng @PreAuthorize.
 */
const props = defineProps({
    title: { type: String, required: true },
    subtitle: { type: String, default: '' },
});

const router = useRouter();
const session = readSession(new TokenLogin());

const NAV = [
    { group: 'Tổng quan', items: [{ to: '/dashboard', label: 'Tổng quan' }] },
    {
        group: 'Câu hỏi',
        items: [
            { to: '/qbank/questions', label: 'Tất cả câu hỏi' },
            { to: '/qbank/subjects', label: 'Môn học' },
            { to: '/qbank/search', label: 'Tìm kiếm thông minh' },
        ],
    },
    {
        group: 'Kiểm định AI',
        items: [
            { to: '/qbank/review', label: 'Xem xét đề xuất AI' },
            { to: '/qbank/tasks', label: 'Tác vụ AI' },
        ],
    },
    { group: 'Tích hợp', items: [{ to: '/qbank/moodle', label: 'Moodle XML' }] },
    {
        group: 'Quản trị',
        items: [
            { to: '/qbank/admin', label: 'Quản trị hệ thống', adminOnly: true },
            { to: '/qbank/llm', label: 'Cài đặt LLM', adminOnly: true },
        ],
    },
    { group: 'Tài khoản', items: [{ to: '/information-admin', label: 'Thông tin tài khoản' }] },
];

const visibleNav = computed(() =>
    NAV.map((entry) => ({
        group: entry.group,
        items: entry.items.filter((item) => !item.adminOnly || session.role === 'ADMIN'),
    })).filter((entry) => entry.items.length > 0),
);

const roleLabel = computed(() => (session.role === 'ADMIN' ? 'Quản trị viên' : 'Giảng viên'));

const currentPath = computed(() => router.currentRoute.value.path);

function isActive(path) {
    return currentPath.value === path;
}
</script>

<template>
    <div class="qb-shell">
        <aside class="qb-sidebar">
            <div class="qb-brand">
                <span class="qb-brand-mark">NTT</span>
                <div>
                    <p class="qb-brand-title">QBank Console</p>
                    <p class="qb-brand-sub">Kiểm định ngân hàng câu hỏi</p>
                </div>
            </div>

            <nav class="qb-nav">
                <template v-for="entry in visibleNav" :key="entry.group">
                    <p class="qb-nav-group">{{ entry.group }}</p>
                    <router-link
                        v-for="item in entry.items"
                        :key="item.to"
                        :to="item.to"
                        class="qb-nav-item"
                        :class="{ 'qb-nav-item--active': isActive(item.to) }"
                    >
                        {{ item.label }}
                    </router-link>
                </template>
            </nav>

            <p class="qb-role">
                Vai: <strong>{{ roleLabel }}</strong>
            </p>
        </aside>

        <div class="qb-main">
            <header class="qb-topbar">
                <div>
                    <h1 class="qb-title">{{ props.title }}</h1>
                    <p v-if="props.subtitle" class="qb-subtitle">{{ props.subtitle }}</p>
                </div>
                <slot name="actions" />
            </header>

            <main class="qb-content">
                <slot />
            </main>
        </div>
    </div>
</template>

<style scoped lang="scss">
.qb-shell {
    display: grid;
    grid-template-columns: 264px minmax(0, 1fr);
    min-height: 100vh;
    background: #f4f6fb;
}

.qb-sidebar {
    display: flex;
    flex-direction: column;
    gap: 1.25rem;
    padding: 1.25rem 1rem;
    background: #10263f;
    color: #e8eef7;
}

.qb-brand {
    display: flex;
    align-items: center;
    gap: 0.75rem;
}

.qb-brand-mark {
    display: grid;
    place-items: center;
    width: 40px;
    height: 40px;
    border-radius: 12px;
    background: #1f6feb;
    font-weight: 700;
    font-size: 0.8rem;
}

.qb-brand-title {
    margin: 0;
    font-weight: 600;
}

.qb-brand-sub {
    margin: 0;
    font-size: 0.75rem;
    color: #9db2cc;
}

.qb-nav {
    display: flex;
    flex-direction: column;
    gap: 0.15rem;
    overflow-y: auto;
}

.qb-nav-group {
    margin: 0.9rem 0 0.35rem;
    font-size: 0.7rem;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: #7f95b0;
}

.qb-nav-item {
    padding: 0.5rem 0.65rem;
    border-radius: 8px;
    color: #d5e0ee;
    text-decoration: none;
    font-size: 0.9rem;
}

.qb-nav-item:hover {
    background: rgba(255, 255, 255, 0.08);
}

.qb-nav-item--active {
    background: #1f6feb;
    color: #fff;
    font-weight: 600;
}

.qb-role {
    margin-top: auto;
    font-size: 0.8rem;
    color: #9db2cc;
}

.qb-main {
    display: flex;
    flex-direction: column;
    min-width: 0;
}

.qb-topbar {
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    gap: 1rem;
    padding: 1.25rem 1.75rem;
    background: #fff;
    border-bottom: 1px solid #e2e8f2;
}

.qb-title {
    margin: 0;
    font-size: 1.25rem;
}

.qb-subtitle {
    margin: 0.15rem 0 0;
    font-size: 0.85rem;
    color: #60708a;
}

.qb-content {
    padding: 1.5rem 1.75rem 2.5rem;
    display: flex;
    flex-direction: column;
    gap: 1rem;
}

@media (max-width: 900px) {
    .qb-shell {
        grid-template-columns: minmax(0, 1fr);
    }

    .qb-sidebar {
        flex-direction: row;
        align-items: center;
        overflow-x: auto;
        gap: 0.5rem;
    }

    .qb-nav {
        flex-direction: row;
    }

    .qb-nav-group,
    .qb-role {
        display: none;
    }
}
</style>
