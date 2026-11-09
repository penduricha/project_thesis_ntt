package com.example.be_web_admin.repository.jpa;


import com.example.be_web_admin.model.jpa.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CourseRepository extends JpaRepository<Course,Long>  {
    Course findCourseByCourseId(Long courseId);
}
