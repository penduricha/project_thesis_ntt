package com.example.be_web_students.controller;

import com.example.be_web_students.model.jpa.Student;
import com.example.be_web_students.service.jpa.impl.StudentServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpSessionRequiredException;
import org.springframework.web.bind.annotation.*;


import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/student")
//@ResponseBody
public class StudentController {

    private final StudentServiceImpl studentServiceImpl;

    public StudentController(StudentServiceImpl studentServiceImpl) {
        this.studentServiceImpl = studentServiceImpl;
    }

//    {
//        studentId : 2500021775,
//                //Nhat@1234
//                password : "$2a$12$FMP1aYy2CF1mxy5vY1SCTOmhac3BWH9odgC0gY1TcHgIc6pyi3mk.",
//            studentName : "Từ Quang Nhật",
//            gender : true,
//            clazzName : "25MTH1B",
//            dateOfBirthStudentDay : 25,
//            dateOfBirthStudentMonth : 12,
//            dateOfBirthStudentYear : 2003,
//            courseId : 8480201
//    }

    @PostMapping
    public ResponseEntity<?> postStudent(@RequestBody Map<String, Object> studentToPost) throws RuntimeException {

        Number studentIdInt = (Number) studentToPost.get("studentId");
        Long studentId = studentIdInt != null ? studentIdInt.longValue() : null;

        String studentName = (String) studentToPost.get("studentName");
        String password = (String) studentToPost.get("password");
        boolean gender = (boolean) studentToPost.get("gender");
        String clazzName = (String) studentToPost.get("clazzName");

        int dateOfBirthStudentDay = (Integer) studentToPost.get("dateOfBirthStudentDay");
        int dateOfBirthStudentMonth = (Integer) studentToPost.get("dateOfBirthStudentMonth");
        int dateOfBirthStudentYear = (Integer) studentToPost.get("dateOfBirthStudentYear");

        LocalDate dateOfBirth = LocalDate.of(dateOfBirthStudentYear, dateOfBirthStudentMonth, dateOfBirthStudentDay);

        Number courseIdInt = (Number) studentToPost.get("courseId");
        Long courseId = courseIdInt != null ? courseIdInt.longValue() : null;

        Student student = new Student();
        student.setStudentId(studentId);
        student.setPassword(password);
        student.setStudentName(studentName);
        student.setGender(gender);
        student.setClazzName(clazzName);
        student.setDateOfBirthStudent(dateOfBirth);

        if(studentServiceImpl.addStudent(student, courseId)) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("status", HttpStatus.CREATED.value());
            response.put("message", "Added student successfully");
            response.put("data", student);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @GetMapping ("/information")
    //@RequestHeader(value = "Authorization", required = false) String authHeader
    public ResponseEntity<?> getStudentInformation(@RequestParam Long studentId) throws RuntimeException {
        Map<String, Object> responseHttp = new HashMap<>();
        Map<String, Object> responseData = studentServiceImpl.getStudentInfo(studentId);

        responseHttp.put("status", HttpStatus.OK.value());

        if(responseData.isEmpty()) {
            responseHttp.put("message", "Student not found.");
        } else {
            responseHttp.put("message", "Student found successfully.");
        }
        responseHttp.put("data", responseData);
        return ResponseEntity.status(HttpStatus.OK).body(responseHttp);
    }
}
