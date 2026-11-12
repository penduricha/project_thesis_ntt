package com.example.be_web_admin.controller.web;

import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.security.password.MgmtPassword;
import com.example.be_web_admin.service.jpa.impl.AdminServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/web/admin")
//@ResponseBody
public class AdminControllerTest {

    private final AdminServiceImpl adminServiceImpl;

    public AdminControllerTest(AdminServiceImpl adminServiceImpl) {
        this.adminServiceImpl = adminServiceImpl;
    }

//    {
//        adminId : 2500021775,
//                //Nhat@1234
//                password : "$2a$12$FMP1aYy2CF1mxy5vY1SCTOmhac3BWH9odgC0gY1TcHgIc6pyi3mk.",
//            adminName : "Từ Quang Nhật",
//            gender : true,
//            clazzName : "25MTH1B",
//            dateOfBirthAdminDay : 25,
//            dateOfBirthAdminMonth : 12,
//            dateOfBirthAdminYear : 2003,
//            courseId : 8480201
//    }

    @PostMapping
    public ResponseEntity<?> postAdmin(@RequestBody Map<String, Object> adminToPost) throws RuntimeException {

        Number adminIdInt = (Number) adminToPost.get("adminId");
        Long adminId = adminIdInt != null ? adminIdInt.longValue() : null;

        String adminName = (String) adminToPost.get("adminName");
        String password = (String) adminToPost.get("password");
        Boolean gender = (Boolean) adminToPost.get("gender");
        String address = (String) adminToPost.get("address");
        String idCard = (String) adminToPost.get("idCard");

        Integer dayObj = (Integer) adminToPost.get("dateOfBirthAdminDay");
        Integer monthObj = (Integer) adminToPost.get("dateOfBirthAdminMonth");
        Integer yearObj = (Integer) adminToPost.get("dateOfBirthAdminYear");

        // Nếu bị null, ném ra lỗi Bad Request (400) thay vì để sập API (500)
        if (dayObj == null || monthObj == null || yearObj == null) {
            return ResponseEntity.badRequest().body("Lỗi: Thiếu thông tin ngày, tháng, năm sinh!");
        }

        // Ép kiểu an toàn sau khi đã check null
        int dateOfBirthAdminDay = dayObj;
        int dateOfBirthAdminMonth = monthObj;
        int dateOfBirthAdminYear = yearObj;

        LocalDate dateOfBirth = LocalDate.of(dateOfBirthAdminYear, dateOfBirthAdminMonth, dateOfBirthAdminDay);

        Number courseIdInt = (Number) adminToPost.get("courseId");
        Long courseId = courseIdInt != null ? courseIdInt.longValue() : null;

        MgmtPassword mgmtPassword = new MgmtPassword();

        Admin admin = new Admin();
        admin.setAdminId(adminId);
        admin.setPassword(mgmtPassword.generateHash(password, 12));
        admin.setAdminName(adminName);
        admin.setGender(gender);
        admin.setDateOfBirthAdmin(dateOfBirth);
        admin.setAddress(address);
        admin.setIdCard(idCard);

        if(adminServiceImpl.addAdmin(admin, courseId)) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("status", HttpStatus.CREATED.value());
            response.put("message", "Added admin successfully");
            response.put("data", admin);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }



//    @GetMapping ("/information/admin-name")
//    //@RequestHeader(value = "Authorization", required = false) String authHeader
//    public ResponseEntity<?> getAdminName(@RequestParam Long adminId) throws RuntimeException {
//        return ResponseEntity.status(HttpStatus.OK).body(adminServiceImpl.getAdminNameByAdminId(adminId));
//    }

    @GetMapping ("/all")
    //@RequestHeader(value = "Authorization", required = false) String authHeader
    public ResponseEntity<?> getAllAdmins() throws RuntimeException {
        //Map<String, Object> responseHttp = new HashMap<>();
        List<Admin> responseData = adminServiceImpl.getAllAdmins();

//        responseHttp.put("status", HttpStatus.OK.value());
//
//        if(responseData.isEmpty()) {
//            responseHttp.put("message", "Admin not found.");
//        } else {
//            responseHttp.put("message", "Admin found successfully.");
//        }
//        responseHttp.put("data", responseData);
        return ResponseEntity.status(HttpStatus.OK).body(responseData);
    }
}
