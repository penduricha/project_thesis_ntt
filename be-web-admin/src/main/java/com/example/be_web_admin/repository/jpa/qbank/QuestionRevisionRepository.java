package com.example.be_web_admin.repository.jpa.qbank;

import com.example.be_web_admin.model.jpa.qbank.QuestionRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

@RepositoryRestResource(exported = false)
public interface QuestionRevisionRepository extends JpaRepository<QuestionRevision, Long> {

    Optional<QuestionRevision> findTopByQuestionIdOrderByRevNoDesc(Long questionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<QuestionRevision> findFirstByQuestionIdOrderByRevNoDesc(Long questionId);

    List<QuestionRevision> findByQuestionIdOrderByRevNoDesc(Long questionId);

    Optional<QuestionRevision> findByQuestionIdAndRevisionId(Long questionId, Long revisionId);

    @Query("select coalesce(max(r.revNo), 0) from QuestionRevision r where r.questionId = :questionId")
    int findMaxRevNo(@Param("questionId") Long questionId);
}
