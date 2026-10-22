package com.example.be_web_students.service.jpa;

import com.example.be_web_students.model.jpa.Course;
import org.springframework.orm.jpa.JpaSystemException;

public interface I_CourseService {

    public boolean addCourse(Course course) throws JpaSystemException;

}
