import LoginPage from "@/pages/login-page/LoginPage.vue";
// import RegisterCourses from "@/pages/register-courses-page/RegisterCourses.vue";
import InformationPage from "@/pages/information-page/InformationPage.vue";
// import ListCourses from "@/pages/list-courses/ListCourses.vue";
import PageNotFound from "@/pages/404-not-found-page/PageNotFound.vue";
// import UniversitySchedule from "@/pages/university-schedule/UniversitySchedule.vue";


const routers = [
    // '/' khi init trang, prop param de truyen tham so.
    { path: '/', component: LoginPage, allow: true},
    { path: '/login', component: LoginPage, allow: true},
    // { path: '/register-courses', component: RegisterCourses, allow: false },
    { path: '/information-student', component: InformationPage, allow: false},
    // { path: '/list-courses', component: ListCourses, allow: false},
    { path: '/404-not-found', component: PageNotFound, allow: true},
    // { path: '/university-schedule', component: UniversitySchedule, allow: false},
];
export default routers;
/*
*
* props: (route) => ({ bankTestJavaOopID: route.query.bankTestJavaOopID })
*/