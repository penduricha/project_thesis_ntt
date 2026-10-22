import LoginPage from "@/pages/login-page/LoginPage.vue";
// import RegisterCourses from "@/pages/register-courses-page/RegisterCourses.vue";
import InformationPage from "@/pages/information-page/InformationPage.vue";
// import ListCourses from "@/pages/list-courses/ListCourses.vue";
import PageNotFound from "@/pages/404-not-found-page/PageNotFound.vue";
// import UniversitySchedule from "@/pages/university-schedule/UniversitySchedule.vue";


const routers = [
    // '/' khi init trang, prop param de truyen tham so.
    { path: '/', component: InformationPage, allow: true, props: (route) => ({})},
    { path: '/login', component: LoginPage, allow: true, props: (route) => ({})},
    // { path: '/register-courses', component: RegisterCourses, allow: true},
    { path: '/information-student', component: InformationPage, allow: true, props: (route) => ({})},
    // { path: '/list-courses', component: ListCourses, allow: true},
    { path: '/404-not-found', component: PageNotFound, allow: true, props: (route) => ({})},
    // { path: '/university-schedule', component: UniversitySchedule, allow: true},
];
export default routers;
/*
*
* props: (route) => ({ bankTestJavaOopID: route.query.bankTestJavaOopID })
*/