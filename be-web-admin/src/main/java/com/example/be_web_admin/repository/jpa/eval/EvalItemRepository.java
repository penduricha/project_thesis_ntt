package com.example.be_web_admin.repository.jpa.eval;

import com.example.be_web_admin.enums.eval.EvalSplitD;
import com.example.be_web_admin.model.jpa.eval.EvalItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.Optional;

@RepositoryRestResource(exported = false)
public interface EvalItemRepository extends JpaRepository<EvalItem, Long> {

    List<EvalItem> findByDatasetVersionAndSplitOrderByItemIdAsc(String datasetVersion, EvalSplitD split);

    Optional<EvalItem> findByDatasetVersionAndQuestionId(String datasetVersion, Long questionId);

    long countByDatasetVersionAndSplit(String datasetVersion, EvalSplitD split);
}
