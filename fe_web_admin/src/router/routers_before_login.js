import LoginPage from "@/pages/login-page/LoginPage.vue";
// import RegisterCourses from "@/pages/register-courses-page/RegisterCourses.vue";
import InformationPage from "@/pages/information-page/InformationPage.vue";
// import ListCourses from "@/pages/list-courses/ListCourses.vue";
import PageNotFound from "@/pages/page-not-found/PageNotFound.vue";
// Module ngân hàng câu hỏi (ghép từ mockup UI_Accept_v11.html + qbank-console-vue3)
import DashboardPage from "@/pages/qbank/DashboardPage.vue";
import QuestionListPage from "@/pages/qbank/QuestionListPage.vue";
import SubjectListPage from "@/pages/qbank/SubjectListPage.vue";
import AiReviewPage from "@/pages/qbank/AiReviewPage.vue";
import PlaceholderPage from "@/pages/qbank/PlaceholderPage.vue";

// import UniversitySchedule from "@/pages/university-schedule/UniversitySchedule.vue";


const routers = [
    // '/' khi init trang, prop param de truyen tham so.
    { path: '/', component: LoginPage, allow: true},
    { path: '/login', component: LoginPage, allow: true},
    // { path: '/register-courses', component: RegisterCourses, allow: false },
    { path: '/information-admin', component: InformationPage, allow: false},
    // { path: '/list-courses', component: ListCourses, allow: false},
    // ----- Ngân hàng câu hỏi -----
    { path: '/dashboard', component: DashboardPage, allow: false },
    { path: '/qbank/questions', component: QuestionListPage, allow: false },
    { path: '/qbank/subjects', component: SubjectListPage, allow: false },
    {
        path: '/qbank/review',
        component: AiReviewPage,
        props: { mode: 'review' },
        allow: false,
    },
    {
        path: '/qbank/tasks',
        component: AiReviewPage,
        props: { mode: 'tasks' },
        allow: false,
    },
    {
        path: '/qbank/search',
        component: PlaceholderPage,
        props: {
            title: 'Tìm kiếm thông minh',
            subtitle: 'Tìm câu hỏi bằng ngôn ngữ tự nhiên qua phân tích 3 bước',
            needs: [
                'API tìm kiếm ngữ nghĩa (phân tích câu hỏi -> bộ lọc JSON -> truy vấn tham số hoá)',
                'Thuộc Server 2 (server-ai-worker); Server 1 hiện chỉ có lọc theo từ khoá',
            ],
        },
        allow: false,
    },
    {
        path: '/qbank/moodle',
        component: PlaceholderPage,
        props: {
            title: 'Moodle XML',
            subtitle: 'Xuất và nhập câu hỏi chuẩn Moodle XML theo môn và chương',
            needs: [
                'API xuất XML: GET /web/admin/questions/export/moodle?subjectId=...',
                'API nhập XML: POST /web/admin/questions/import/moodle (kèm kiểm trùng)',
            ],
        },
        allow: false,
    },
    {
        path: '/qbank/admin',
        component: PlaceholderPage,
        props: {
            title: 'Quản trị hệ thống',
            subtitle: 'Tra cứu tài khoản, quản lý vai trò và quyền truy cập',
            needs: [
                'API CRUD tài khoản cho môi trường thật (hiện chỉ có ở profile dev/local/test)',
                'Phân trang + @PreAuthorize("hasRole(\'ADMIN\')")',
            ],
        },
        allow: false,
    },
    {
        path: '/qbank/llm',
        component: PlaceholderPage,
        props: {
            title: 'Cài đặt LLM',
            subtitle: 'Quản lý tối đa 5 node Colab qua Cloudflare Quick Tunnel',
            needs: [
                'API cấu hình node suy luận (thuộc Server 2)',
                'Kiểm tra kết nối và lưu trạng thái node',
            ],
        },
        allow: false,
    },
    { path: '/404-not-found', component: PageNotFound, allow: true},
    // { path: '/university-schedule', component: UniversitySchedule, allow: false},
];
export default routers;
/*
*
* props: (route) => ({ bankTestJavaOopID: route.query.bankTestJavaOopID })
*/