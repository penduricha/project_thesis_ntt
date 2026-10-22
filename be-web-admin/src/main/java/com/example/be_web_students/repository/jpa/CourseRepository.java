package com.example.be_web_students.repository.jpa;

import com.example.be_web_students.model.jpa.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CourseRepository extends JpaRepository<Course,Long> {

    Course findCourseByCourseId(Long courseId);
}
