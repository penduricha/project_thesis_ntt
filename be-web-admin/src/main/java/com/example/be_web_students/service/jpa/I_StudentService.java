package com.example.be_web_students.service.jpa;

import com.example.be_web_students.model.jpa.Student;
import org.springframework.orm.jpa.JpaSystemException;

import java.util.Map;

public interface I_StudentService {

    public boolean addStudent(Student student, Long courseId) throws JpaSystemException;

    public Object loginStudent(Long studentId, String password) throws Exception;

    public Map<String, Object> getStudentInfo(Long studentId) throws Exception;
}
