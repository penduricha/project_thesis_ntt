//package com.example.be_web_admin.controller.rabbitMQ;
//
//import org.springframework.amqp.rabbit.core.RabbitTemplate;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.util.HashMap;
//import java.util.Map;
//
//@RestController
//@RequestMapping("/admin")
//public class AdminProducerController {
//
//    @Autowired
//    private RabbitTemplate rabbitTemplate;
//
//    // Tên Queue (phải khớp với bên Consumer)
//    private static final String QUEUE_NAME = "admin_insert_queue";
//
//    @PostMapping("/async")
//    public ResponseEntity<?> postAdminWithQueue(@RequestBody Map<String, Object> adminToPost) throws RuntimeException {
//
//        // 1. Validate sơ bộ (Fail-fast) để tránh đẩy dữ liệu rác vào Queue
//        Integer dayObj = (Integer) adminToPost.get("dateOfBirthAdminDay");
//        Integer monthObj = (Integer) adminToPost.get("dateOfBirthAdminMonth");
//        Integer yearObj = (Integer) adminToPost.get("dateOfBirthAdminYear");
//
//        if (dayObj == null || monthObj == null || yearObj == null) {
//            return ResponseEntity.badRequest().body("Lỗi: Thiếu thông tin ngày, tháng, năm sinh!");
//        }
//
//        String password = (String) adminToPost.get("password");
//        String adminName = (String) adminToPost.get("adminName");
//        if (password == null || password.trim().isEmpty() || adminName == null || adminName.trim().isEmpty()) {
//            return ResponseEntity.badRequest().body("Lỗi: Tên và mật khẩu không được để trống!");
//        }
//
//        // 2. Đẩy TOÀN BỘ dữ liệu (Map adminToPost) vào RabbitMQ
//        // Spring AMQP sẽ tự động chuyển Map này thành chuỗi JSON và gửi đi.
//        // Mọi field (adminId, adminName, password, gender, dateOfBirth...) đều được đẩy vào.
//        try {
//            rabbitTemplate.convertAndSend(QUEUE_NAME, adminToPost);
//        } catch (Exception e) {
//            // Nếu không kết nối được tới RabbitMQ Server
//            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
//                    .body("Hệ thống đang bận, vui lòng thử lại sau!");
//        }
//
//        // 3. Trả về phản hồi NGAY LẬP TỨC (HTTP 202 Accepted)
//        Map<String, Object> response = new HashMap<>();
//        response.put("success", true);
//        response.put("status", HttpStatus.ACCEPTED.value());
//        response.put("message", "Yêu cầu đã được tiếp nhận và đang xử lý ngầm.");
//
//        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
//    }
//}