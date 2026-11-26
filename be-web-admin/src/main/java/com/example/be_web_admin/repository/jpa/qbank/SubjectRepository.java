package com.example.be_web_admin.repository.jpa.qbank;

import com.example.be_web_admin.model.jpa.qbank.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@RepositoryRestResource(exported = false)
public interface SubjectRepository extends JpaRepository<Subject, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Subject s where s.subjectId = :id")
    Optional<Subject> findForQuestionCreate(@Param("id") Long id);

    List<Subject> findByIsActiveTrueOrderBySubjectNameAsc();

    List<Subject> findAllByOrderBySubjectNameAsc();

    boolean existsBySubjectCode(String subjectCode);

    long countByIsActiveTrue();
}
