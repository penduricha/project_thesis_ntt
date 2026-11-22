import { createRouter, createWebHashHistory } from 'vue-router'
import { S, resetFilters } from '../store.js'
const Dashboard = () => import('../views/DashboardView.vue')
const Bank = () => import('../views/QuestionBankView.vue')

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', redirect: '/dash' },
    { path: '/dash', name: 'dash', component: Dashboard },
    { path: '/bank', name: 'bank', component: Bank },
    { path: '/course/:ci(\\d+)', name: 'course', component: Bank },
    { path: '/courses', name: 'courses', component: () => import('../views/CoursesView.vue') },
    { path: '/review', name: 'review', component: () => import('../views/ReviewView.vue') },
    { path: '/tasks', name: 'tasks', component: () => import('../views/TasksView.vue') },
    { path: '/search', name: 'search', component: () => import('../views/SearchView.vue') },
    { path: '/moodle', name: 'moodle', component: () => import('../views/MoodleView.vue') },
    { path: '/admin', name: 'admin', component: () => import('../views/AdminView.vue') },
    { path: '/llm', name: 'llm', component: () => import('../views/LlmView.vue') },
    { path: '/account', name: 'account', component: () => import('../views/AccountView.vue') },
    { path: '/settings', name: 'settings', component: () => import('../views/SettingsView.vue') },
  ],
})
/* Giống go(): đổi trang (trừ 'bank') thì xoá bộ lọc, giữ trạng thái ẩn đáp án */
router.beforeEach((to, from) => {
  if (to.name !== from.name && to.name !== 'bank') resetFilters()
  if (to.name === 'course') S.ci = +to.params.ci
  if (/^(admin|llm)$/.test(to.name) && S.role !== 'admin') return { name: 'dash' }
})
export default router
