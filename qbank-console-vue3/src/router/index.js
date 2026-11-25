import { createRouter, createWebHashHistory } from 'vue-router'
import { S, resetFilters } from '../store.js'
import { auth } from '../auth/authStore.js'
const Dashboard = () => import('../views/DashboardView.vue')
const Bank = () => import('../views/QuestionBankView.vue')

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
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
    /* Bắt-tất-cả PHẢI nằm cuối mảng: vue-router khớp theo thứ tự khai báo.
       `:pathMatch(.*)*` khớp mọi độ sâu (kể cả /a/b/c) — dùng tham số lặp `*`.
       Trước khi có route này, đường dẫn lạ render RouterView rỗng ⇒ màn hình trắng. */
    { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('../views/NotFoundView.vue') },
  ],
})
/* Giống go(): đổi trang (trừ 'bank') thì xoá bộ lọc, giữ trạng thái ẩn đáp án.
   + Bảo vệ route: chưa đăng nhập -> /login; đã đăng nhập mà vào /login -> /dash.
   (fe_web_admin dùng 2 bộ route trước/sau đăng nhập; QBank dùng 1 bộ + guard cho đơn giản.) */
router.beforeEach((to, from) => {
  /* 404 phải tới được KỂ CẢ khi chưa đăng nhập: người dùng gõ sai đường dẫn mà bị đá về trang
     đăng nhập thì không hiểu mình sai ở đâu. Ở App.vue trang này được vẽ KHÔNG kèm topbar/sidebar
     khi chưa đăng nhập, nên không lộ menu của ứng dụng.
     Kiểm tra TRƯỚC nhánh chặn đăng nhập bên dưới. */
  if (to.name === 'not-found') return true
  if (to.name === 'login') {
    return auth.isAuthenticated ? { name: 'dash' } : true
  }
  if (!auth.isAuthenticated) return { name: 'login' }
  if (to.name !== from.name && to.name !== 'bank') resetFilters()
  if (to.name === 'course') S.ci = +to.params.ci
  if (/^(admin|llm)$/.test(to.name) && S.role !== 'admin') return { name: 'dash' }
})
export default router
