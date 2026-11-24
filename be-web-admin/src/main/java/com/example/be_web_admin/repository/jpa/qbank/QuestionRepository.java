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
     * Tìm câu đã dùng tiêu đề này trong CÙNG một môn, dùng để <b>chặn khi thêm mới</b>.
     *
     * <p>So sánh trên {@code lower(title)} sau khi service đã trim hai đầu — không phân biệt
     * hoa/thường, để "Câu 1" và "câu 1" bị coi là trùng (không lách được quy tắc bằng cách
     * đổi kiểu chữ). Bỏ qua câu đã lưu trữ ({@code ARCHIVED}) để tiêu đề của câu lưu trữ vẫn
     * dùng lại được.</p>
     *
     * <p>Trả về danh sách (không phải {@code boolean}) để tầng service báo được mã câu đang
     * giữ tiêu đề, thay vì chỉ nói "đã trùng".</p>
     *
     * @param excludeId <b>luôn truyền một số, không truyền null</b>. Hiện chỉ gọi khi thêm mới nên
     *                  truyền {@code -1} (không có {@code question_id} âm ⇒ không loại trừ dòng
     *                  nào). Nếu về sau cần dùng cho đường cập nhật thì truyền id câu đang sửa;
     *                  truyền {@code null} sẽ hỏng trong im lặng vì {@code question_id <> NULL}
     *                  luôn cho {@code UNKNOWN} ⇒ query không trả dòng nào ⇒ mất tác dụng chặn.
     */
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
