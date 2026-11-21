package com.example.be_web_admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Khóa quy ước đường dẫn API để lần sau gõ nhầm là test báo ngay, không phải đợi tới lúc client nhận 404.
 * Test này KHÔNG khởi động Spring, nên không cần MariaDB / Redis.
 *
 * Quy ước (URL đầy đủ = context-path + base path của controller + path của method):
 *
 *   http://localhost:8080/api                 context-path chung (application.yaml)
 *   http://localhost:8080/api/web/admin/...   controller trong package controller.web    -> @RequestMapping("/web/admin")
 *   http://localhost:8080/api/mobile/admin/.. controller trong package controller.mobile -> @RequestMapping("/mobile/admin")
 *
 * Client tương ứng:
 *   Vue     VITE_SPRING_API_PREFIX=/api/web     + path "/admin/..."
 *   Flutter SPRING_API_PREFIX=/api/mobile       + path "/admin/..."
 */
class ApiPathConventionTest {

    private static final String CONTROLLER_PACKAGE = "com.example.be_web_admin.controller";

    @Test
    void contextPathIsApi() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));
        Properties properties = yaml.getObject();
        assertNotNull(properties, "Không đọc được application.yaml");
        assertEquals("/api", properties.getProperty("server.servlet.context-path"),
                "context-path chung của mọi API phải là /api");
    }

    @Test
    void webControllersAreUnderWebAdmin() throws ClassNotFoundException {
        assertControllersUnder("web", "/web/admin");
    }

    @Test
    void mobileControllersAreUnderMobileAdmin() throws ClassNotFoundException {
        assertControllersUnder("mobile", "/mobile/admin");
    }

    private void assertControllersUnder(String subPackage, String basePath) throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<BeanDefinition> controllers = scanner.findCandidateComponents(CONTROLLER_PACKAGE + "." + subPackage);
        assertFalse(controllers.isEmpty(), "Không tìm thấy @RestController nào trong package " + subPackage);

        for (BeanDefinition definition : controllers) {
            Class<?> controller = Class.forName(definition.getBeanClassName());

            CrossOrigin crossOrigin = AnnotatedElementUtils.findMergedAnnotation(controller, CrossOrigin.class);
            if (crossOrigin != null) {
                for (String origin : crossOrigin.origins()) {
                    assertFalse("*".equals(origin),
                            controller.getSimpleName() + " không được dùng @CrossOrigin(origins = \"*\"); CORS tập trung tại SecurityConfig");
                }
            }

            RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            assertNotNull(classMapping, controller.getSimpleName() + " thiếu @RequestMapping ở cấp class");
            assertTrue(classMapping.path().length > 0, controller.getSimpleName() + " chưa khai báo path");
            for (String path : classMapping.path()) {
                assertTrue(path.equals(basePath) || path.startsWith(basePath + "/"),
                        controller.getSimpleName() + " khai báo \"" + path + "\", phải bắt đầu bằng " + basePath);
            }

            // Path của method KHÔNG được lặp lại context-path hay base path, nếu không URL bị nhân đôi => 404.
            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping methodMapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (methodMapping == null) continue;
                for (String path : methodMapping.path()) {
                    String where = controller.getSimpleName() + "." + method.getName() + " (\"" + path + "\")";
                    assertFalse(path.startsWith("/api/") || path.equals("/api"), where + " không được lặp context-path /api");
                    assertFalse(path.startsWith("/web/") || path.startsWith("/mobile/"),
                            where + " không được lặp /web hoặc /mobile, đã có ở @RequestMapping của class");
                }
            }
        }
    }
}
