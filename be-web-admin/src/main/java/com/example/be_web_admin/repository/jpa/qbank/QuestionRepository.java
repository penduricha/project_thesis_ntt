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

    /**
     * Chặn trùng tiêu đề trong CÙNG một môn (yêu cầu nghiệp vụ: tiêu đề phải khác nhau).
     *
     * <p>So sánh sau khi đã trim và bỏ khoảng trắng ở hai đầu — tầng service chuẩn hoá trước
     * khi gọi. Không phân biệt hoa/thường để "Câu 1" và "câu 1" bị coi là trùng (tránh lách
     * quy tắc chỉ bằng cách đổi kiểu chữ).</p>
     *
     * @param subjectId môn đang xét
     * @param title     tiêu đề đã trim
     * @param excludeId câu đang sửa — không tự so với chính nó.
     *                  <b>Khi tạo mới phải truyền -1</b> (không có question_id âm), KHÔNG truyền
     *                  null: so sánh {@code <> null} trong SQL luôn cho UNKNOWN nên query sẽ
     *                  không trả dòng nào và việc chặn trùng trở nên vô hiệu trong im lặng.
     */
    @Query("""
            select count(q) > 0 from Question q
            where q.subjectId = :subjectId
              and q.status <> :archived
              and lower(q.title) = lower(:title)
              and q.questionId <> :excludeId
            """)
    boolean existsDuplicateTitleInSubject(@Param("subjectId") Long subjectId,
                                          @Param("title") String title,
                                          @Param("excludeId") Long excludeId,
                                          @Param("archived") QuestionStatusD archived);

    /** Tìm câu trùng tiêu đề để báo mã cho người dùng biết đang trùng với câu nào. */
    @Query("""
            select q from Question q
            where q.subjectId = :subjectId
              and q.status <> :archived
              and lower(q.title) = lower(:title)
              and q.questionId <> :excludeId
            order by q.questionId asc
            """)
    List<Question> findDuplicateTitleInSubject(@Param("subjectId") Long subjectId,
                                               @Param("title") String title,
                                               @Param("excludeId") Long excludeId,
                                               @Param("archived") QuestionStatusD archived);

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
