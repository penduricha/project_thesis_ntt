package com.example.be_web_students.repository.jpa;

import com.example.be_web_students.model.jpa.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentRepository extends JpaRepository<Student,Long> {

    Student findStudentByStudentId(Long studentId);
}
