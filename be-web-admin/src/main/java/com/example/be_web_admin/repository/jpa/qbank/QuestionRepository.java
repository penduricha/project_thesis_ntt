package com.example.be_web_admin.repository.jpa.qbank;

import com.example.be_web_admin.enums.qbank.QuestionStatusD;
import com.example.be_web_admin.model.jpa.qbank.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

@RepositoryRestResource(exported = false)
public interface QuestionRepository extends JpaRepository<Question, Long>, JpaSpecificationExecutor<Question> {

    long countByStatus(QuestionStatusD status);

    long countBySubjectIdAndStatus(Long subjectId, QuestionStatusD status);

    /** Cảnh báo trùng nội dung (UC05/UC06): content_hash chỉ INDEX, KHÔNG UNIQUE. */
    boolean existsBySubjectIdAndContentHash(Long subjectId, String contentHash);

    long countByStatusAndUpdatedAtGreaterThanEqual(QuestionStatusD status, Instant since);

    @Query("select q.validationStatus as status, count(q) as total from Question q group by q.validationStatus")
    List<ValidationStatusCount> countGroupByValidationStatus();

    @Query("""
            select q.validationStatus as status, count(q) as total
            from Question q
            where q.subjectId = :subjectId
            group by q.validationStatus
            """)
    List<ValidationStatusCount> countGroupByValidationStatus(@Param("subjectId") Long subjectId);
}
