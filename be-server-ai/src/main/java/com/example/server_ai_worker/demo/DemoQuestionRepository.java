package com.example.server_ai_worker.demo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DemoQuestionRepository extends JpaRepository<DemoQuestion, Long> {

    Page<DemoQuestion> findBySubjectCode(String subjectCode, Pageable pageable);

    Page<DemoQuestion> findByGoldIssueType(String goldIssueType, Pageable pageable);

    Page<DemoQuestion> findBySubjectCodeAndGoldIssueType(String subjectCode, String goldIssueType, Pageable pageable);

    List<DemoQuestion> findAllByOrderByExtIdAsc();

    Optional<DemoQuestion> findByExtId(String extId);

    @Query("SELECT q.goldIssueType, COUNT(q) FROM DemoQuestion q GROUP BY q.goldIssueType")
    List<Object[]> countByGoldLabel();

    @Query("SELECT q.subjectCode, COUNT(q) FROM DemoQuestion q GROUP BY q.subjectCode ORDER BY q.subjectCode")
    List<Object[]> countBySubjectCode();

    @Query("SELECT q.subjectCode, q.goldIssueType, COUNT(q) FROM DemoQuestion q GROUP BY q.subjectCode, q.goldIssueType")
    List<Object[]> countBySubjectAndLabel();
}
