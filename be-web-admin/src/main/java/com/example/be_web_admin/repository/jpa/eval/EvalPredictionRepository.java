package com.example.be_web_admin.repository.jpa.eval;

import com.example.be_web_admin.enums.eval.EvalSplitD;
import com.example.be_web_admin.model.jpa.eval.EvalPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

@RepositoryRestResource(exported = false)
public interface EvalPredictionRepository extends JpaRepository<EvalPrediction, Long> {

    List<EvalPrediction> findByRunIdOrderByItemIdAsc(Long runId);

    List<EvalPrediction> findByRunIdAndSplit(Long runId, EvalSplitD split);

    long countByRunIdAndIssueType(Long runId, String issueType);

    /**
     * Đếm dự đoán theo nhóm lỗi trong 1 run (dùng cho báo cáo Chương 4).
     * Cố ý KHÔNG dùng view vw_eval_confusion: view hiện tại cho FN = 0 (xem D7).
     */
    @Query("""
            select p.issueType as issueType, count(p) as total
            from EvalPrediction p
            where p.runId = :runId
            group by p.issueType
            """)
    List<IssueTypeCount> countGroupByIssueType(@Param("runId") Long runId);

    /** Projection cho thống kê theo nhóm lỗi. */
    interface IssueTypeCount {
        String getIssueType();

        long getTotal();
    }
}
