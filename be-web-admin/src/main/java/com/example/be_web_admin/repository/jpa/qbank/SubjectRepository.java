package com.example.be_web_admin.repository.jpa.qbank;

import com.example.be_web_admin.model.jpa.qbank.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;

@RepositoryRestResource(exported = false)
public interface SubjectRepository extends JpaRepository<Subject, Long> {

    List<Subject> findByIsActiveTrueOrderBySubjectNameAsc();

    List<Subject> findAllByOrderBySubjectNameAsc();

    boolean existsBySubjectCode(String subjectCode);

    long countByIsActiveTrue();
}
