//package com.example.be_web_admin.controller.rabbitMQ;
//
//import com.example.be_web_admin.model.jpa.Admin;
//import com.example.be_web_admin.security.password.MgmtPassword;
//import com.example.be_web_admin.service.jpa.impl.AdminServiceImpl;
//import org.springframework.amqp.rabbit.annotation.RabbitListener;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Component;
//
//import java.time.LocalDate;
//import java.util.Map;
//
//@Component
//public class AdminConsumer {
//
//    @Autowired
//    private AdminServiceImpl adminServiceImpl;
//
//    // Lắng nghe Queue
//    @RabbitListener(queues = "admin_insert_queue")
//    public void processAdminMessage(Map<String, Object> adminToPost) {
//
//        try {
//            // 1. Lấy đầy đủ các field từ Map ra
//            Number adminIdInt = (Number) adminToPost.get("adminId");
//            Long adminId = adminIdInt != null ? adminIdInt.longValue() : null;
//
//            String adminName = (String) adminToPost.get("adminName");
//            String password = (String) adminToPost.get("password");
//            Boolean gender = (Boolean) adminToPost.get("gender");
//            String address = (String) adminToPost.get("address");
//            String idCard = (String) adminToPost.get("idCard");
//
//            int dateOfBirthAdminDay = (Integer) adminToPost.get("dateOfBirthAdminDay");
//            int dateOfBirthAdminMonth = (Integer) adminToPost.get("dateOfBirthAdminMonth");
//            int dateOfBirthAdminYear = (Integer) adminToPost.get("dateOfBirthAdminYear");
//
//            LocalDate dateOfBirth = LocalDate.of(dateOfBirthAdminYear, dateOfBirthAdminMonth, dateOfBirthAdminDay);
//
//            Number courseIdInt = (Number) adminToPost.get("courseId");
//            Long courseId = courseIdInt != null ? courseIdInt.longValue() : null;
//
//            // 2. Băm mật khẩu (Tác vụ tốn thời gian, giờ đã được chạy ngầm)
//            MgmtPassword mgmtPassword = new MgmtPassword();
//
//            Admin admin = new Admin();
//            admin.setAdminId(adminId);
//            admin.setPassword(mgmtPassword.generateHash(password, 12));
//            admin.setAdminName(adminName);
//            admin.setGender(gender);
//            admin.setDateOfBirthAdmin(dateOfBirth);
//            admin.setAddress(address);
//            admin.setIdCard(idCard);
//
//            // 3. Lưu vào Database
//            if(adminServiceImpl.addAdmin(admin, courseId)) {
//                System.out.println("✅ [Consumer] Đã lưu Admin thành công: " + adminName);
//            } else {
//                System.err.println("❌ [Consumer] Lỗi lưu Admin: " + adminName);
//            }
//
//        } catch (Exception e) {
//            System.err.println("❌ [Consumer] Lỗi xử lý tin nhắn: " + e.getMessage());
//            e.printStackTrace();
//            // Ném lỗi ra để RabbitMQ biết và có thể retry
//            throw e;
//        }
//    }
//}