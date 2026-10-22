package com.example.be_web_students.service.jpa.impl;

import com.example.be_web_students.model.jpa.Course;



import com.example.be_web_students.repository.jpa.CourseRepository;
import com.example.be_web_students.service.jpa.I_CourseService;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;

@Service
public class CourseServiceImpl implements I_CourseService {

    private final CourseRepository courseRepository;

    public CourseServiceImpl(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Override
    public boolean addCourse(Course course) throws JpaSystemException {

        Course courseFound = courseRepository.findCourseByCourseId(course.getCourseId());

        if(courseFound == null) {
            courseRepository.save(course);
            return true;
        }

        return false;
    }
}
