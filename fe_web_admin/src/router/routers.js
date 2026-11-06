import LoginPage from "@/pages/login-page/LoginPage.vue";
// import RegisterCourses from "@/pages/register-courses-page/RegisterCourses.vue";
import InformationPage from "@/pages/information-page/InformationPage.vue";
// import ListCourses from "@/pages/list-courses/ListCourses.vue";
import PageNotFound from "@/pages/404-not-found-page/PageNotFound.vue";


const routers = [
    // '/' khi init trang, prop param de truyen tham so.
    { path: '/', component: LoginPage, allow: true},
    { path: '/login', component: LoginPage, allow: false},
    // { path: '/register-courses', component: RegisterCourses, allow: true },
    { path: '/information-student', component: InformationPage, allow: true},
    // { path: '/list-courses', component: ListCourses, allow: true},
    { path: '/404-not-found', component: PageNotFound, allow: true},
];
export default routers;
/*
*
* props: (route) => ({ bankTestJavaOopID: route.query.bankTestJavaOopID })
*/