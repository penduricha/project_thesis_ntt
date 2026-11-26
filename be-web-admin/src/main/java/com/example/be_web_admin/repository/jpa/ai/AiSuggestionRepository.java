package com.example.be_web_admin.repository.jpa.ai;

import com.example.be_web_admin.enums.ai.SuggestionDecisionD;
import com.example.be_web_admin.model.jpa.ai.AiSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

@RepositoryRestResource(exported = false)
public interface AiSuggestionRepository extends JpaRepository<AiSuggestion, Long> {

    @Query("select s.taskId from AiSuggestion s where s.suggestionId = :id")
    Optional<String> findTaskIdBySuggestionId(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AiSuggestion s where s.suggestionId = :id")
    Optional<AiSuggestion> findForReview(@Param("id") Long id);

    @Modifying(flushAutomatically = true)
    @Query("update AiSuggestion s set s.decision = :stale where s.decision = :pending and s.taskId in (select t.taskId from AiTask t where t.questionId = :questionId)")
    int markQuestionPendingStale(@Param("questionId") Long questionId, @Param("pending") SuggestionDecisionD pending, @Param("stale") SuggestionDecisionD stale);

    List<AiSuggestion> findByTaskIdOrderBySeqNoAsc(String taskId);

    long countByTaskId(String taskId);

    long countByTaskIdAndDecision(String taskId, SuggestionDecisionD decision);

    long countByDecision(SuggestionDecisionD decision);

    /**
     * Đánh dấu "stale" mọi đề xuất còn pending của các task thuộc 1 câu hỏi.
     * Đây là bản vá D6: trigger hiện tại chỉ chạy khi validation_status chuyển sang 'stale',
     * nên service phải làm việc này một cách tường minh khi nội dung câu hỏi thay đổi.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AiSuggestion s
            set s.decision = :stale
            where s.taskId in :taskIds and s.decision = :pending
            """)
    int markPendingStale(@Param("taskIds") Collection<String> taskIds,
                         @Param("pending") SuggestionDecisionD pending,
                         @Param("stale") SuggestionDecisionD stale);

    /** Số đề xuất còn chờ duyệt của 1 câu hỏi (mọi task của câu đó). */
    @Query("""
            select count(s) from AiSuggestion s
            where s.decision = :decision
              and s.taskId in (select t.taskId from AiTask t where t.questionId = :questionId)
            """)
    long countByDecisionForQuestion(@Param("questionId") Long questionId,
                                    @Param("decision") SuggestionDecisionD decision);

    /** Số đề xuất còn chờ duyệt trong phạm vi 1 môn học. */
    @Query("""
            select count(s) from AiSuggestion s
            where s.decision = :decision
              and s.taskId in (select t.taskId from AiTask t
                               where t.questionId in (select q.questionId from Question q
                                                      where q.subjectId = :subjectId))
            """)
    long countByDecisionForSubject(@Param("subjectId") Long subjectId,
                                   @Param("decision") SuggestionDecisionD decision);
}
