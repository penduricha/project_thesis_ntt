package com.example.be_web_admin.repository.jpa.eval;

import com.example.be_web_admin.enums.eval.EvalStageD;
import com.example.be_web_admin.model.jpa.eval.EvalLabel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;

@RepositoryRestResource(exported = false)
public interface EvalLabelRepository extends JpaRepository<EvalLabel, Long> {

    List<EvalLabel> findByItemIdOrderByStageAsc(Long itemId);

    List<EvalLabel> findByItemIdAndStage(Long itemId, EvalStageD stage);

    List<EvalLabel> findByItemIdAndStageAndAnnotatorCode(Long itemId, EvalStageD stage, String annotatorCode);

    long countByItemIdAndStage(Long itemId, EvalStageD stage);
}
