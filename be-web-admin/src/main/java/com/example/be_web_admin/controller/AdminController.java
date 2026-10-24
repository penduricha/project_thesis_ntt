package com.example.be_web_admin.controller;

import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.service.jpa.impl.AdminServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin")
//@ResponseBody
public class AdminController {

    private final AdminServiceImpl adminServiceImpl;

    public AdminController(AdminServiceImpl adminServiceImpl) {
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
        boolean gender = (boolean) adminToPost.get("gender");
        String clazzName = (String) adminToPost.get("clazzName");

        int dateOfBirthAdminDay = (Integer) adminToPost.get("dateOfBirthAdminDay");
        int dateOfBirthAdminMonth = (Integer) adminToPost.get("dateOfBirthAdminMonth");
        int dateOfBirthAdminYear = (Integer) adminToPost.get("dateOfBirthAdminYear");

        LocalDate dateOfBirth = LocalDate.of(dateOfBirthAdminYear, dateOfBirthAdminMonth, dateOfBirthAdminDay);

        Number courseIdInt = (Number) adminToPost.get("courseId");
        Long courseId = courseIdInt != null ? courseIdInt.longValue() : null;

        Admin admin = new Admin();
        admin.setAdminId(adminId);
        admin.setPassword(password);
        admin.setAdminName(adminName);
        admin.setGender(gender);
        admin.setDateOfBirthAdmin(dateOfBirth);

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

    @GetMapping ("/information")
    //@RequestHeader(value = "Authorization", required = false) String authHeader
    public ResponseEntity<?> getAdminInformation(@RequestParam Long adminId) throws RuntimeException {
        Map<String, Object> responseHttp = new HashMap<>();
        Map<String, Object> responseData = adminServiceImpl.getAdminInfo(adminId);

        responseHttp.put("status", HttpStatus.OK.value());

        if(responseData.isEmpty()) {
            responseHttp.put("message", "Admin not found.");
        } else {
            responseHttp.put("message", "Admin found successfully.");
        }
        responseHttp.put("data", responseData);
        return ResponseEntity.status(HttpStatus.OK).body(responseHttp);
    }
}
