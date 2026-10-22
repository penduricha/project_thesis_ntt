package com.example.be_web_students.controller;

import com.example.be_web_students.model.jpa.Course;
import com.example.be_web_students.service.jpa.impl.CourseServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/course")
public class CourseController {

    private final CourseServiceImpl courseServiceImpl;

    @Autowired
    public CourseController(CourseServiceImpl courseServiceImpl) {
        this.courseServiceImpl = courseServiceImpl;
    }

    @PostMapping
    public ResponseEntity<?> postCourse(@RequestBody Map<String, Object> courseToPost) throws RuntimeException {
//        {
//            courseId : "8480201",
//            trainingSystem : "Master",
//            branch : "An Phu Dong ward, Ho Chi Minh city.",
//            industry : "CNTT",
//            faculty : "Khoa CNTT",
//            major : "AI/ML",
//            typeOfTraining : "Research"
//        }
        Number courseIdInt = (Number) courseToPost.get("courseId");
        Long courseId = courseIdInt != null ? courseIdInt.longValue() : null;

        String trainingSystem = (String) courseToPost.get("trainingSystem");
        String branch = (String) courseToPost.get("branch");
        String industry = (String) courseToPost.get("industry");
        String faculty = (String) courseToPost.get("faculty");
        String major = (String) courseToPost.get("major");
        String typeOfTraining = (String) courseToPost.get("typeOfTraining");

        Course courseToSave = new Course();
        courseToSave.setCourseId(courseId);
        courseToSave.setTrainingSystem(trainingSystem);
        courseToSave.setBranch(branch);
        courseToSave.setIndustry(industry);
        courseToSave.setFaculty(faculty);
        courseToSave.setMajor(major);
        courseToSave.setTypeOfTraining(typeOfTraining);

        if(courseServiceImpl.addCourse(courseToSave)) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("status", HttpStatus.CREATED.value());
            response.put("message", "Added course successfully");
            response.put("data", courseToSave);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}
